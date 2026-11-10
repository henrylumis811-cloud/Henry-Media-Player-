package com.henrylumis.mediaprayer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.content.ComponentCallbacks2
import android.os.Looper
import android.util.LruCache
import com.henrylumis.mediaprayer.data.Song
import java.io.File
import java.io.FileOutputStream
import java.util.HashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/** Shared two-level artwork cache: memory for instant binds, disk across screen/app restarts. */
object ArtworkLoader {
    const val LIST_THUMBNAIL_EDGE = 160
    const val FOREGROUND_ARTWORK_EDGE = 512
    private val main = Handler(Looper.getMainLooper())
    // Bound both active decoders and waiting work. A fixed pool with an unbounded
    // queue can retain hundreds of obsolete requests after repeated fast flings.
    private const val MAX_PENDING_REQUESTS = 48
    private const val MAX_CALLBACKS_PER_KEY = 32
    private const val MAX_QUEUE_RETRIES = 3
    private const val QUEUE_RETRY_DELAY_MS = 180L
    private val executor = ThreadPoolExecutor(
        2, 2, 30L, TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_PENDING_REQUESTS),
        { runnable -> Thread(runnable, "rvh-artwork").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private fun cacheSize(divisor: Int, minimumKb: Int, maximumKb: Int): Int =
        (Runtime.getRuntime().maxMemory() / 1024 / divisor).toInt().coerceIn(minimumKb, maximumKb)

    // Full-size covers are retained for Now Playing; compact variants serve dense lists.
    // The combined caps stay conservative instead of allowing the bitmap cache to consume
    // the same memory needed by Media3 and the rest of the UI.
    private val cache = object : LruCache<Long, Bitmap>(cacheSize(32, 1024, 16 * 1024)) {
        override fun sizeOf(key: Long, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val thumbnailCache = object : LruCache<Long, Bitmap>(cacheSize(16, 1024, 16 * 1024)) {
        override fun sizeOf(key: Long, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private data class ArtworkCallback(val edge: Int, val callback: (Bitmap?) -> Unit)
    private class PendingRequest(val callbacks: MutableList<ArtworkCallback>)
    private val inFlight = HashMap<Long, PendingRequest>()
    /** Release decoded bitmaps when Android signals memory pressure. Disk artwork remains intact. */
    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache.evictAll()
                thumbnailCache.evictAll()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                cache.trimToSize(cache.maxSize() / 4)
                thumbnailCache.trimToSize(thumbnailCache.maxSize() / 4)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.trimToSize(cache.maxSize() / 2)
                thumbnailCache.trimToSize(thumbnailCache.maxSize() / 2)
            }
        }
    }

    /** Returns a decoded image variant without scheduling disk or metadata work. */
    fun peek(song: Song, edge: Int = LIST_THUMBNAIL_EDGE): Bitmap? =
        if (edge >= FOREGROUND_ARTWORK_EDGE) cache.get(key(song)) else thumbnailCache.get(key(song))

    private fun key(song: Song): Long = song.albumId.takeIf { it >= 0L } ?: song.id

    fun load(context: Context, song: Song, callback: (Bitmap?) -> Unit) =
        loadInternal(context, song, LIST_THUMBNAIL_EDGE, callback)

    /** Foreground surfaces receive a larger cached bitmap; list rows stay lightweight. */
    fun loadForeground(context: Context, song: Song, callback: (Bitmap?) -> Unit) =
        loadInternal(context, song, FOREGROUND_ARTWORK_EDGE, callback)

    private fun loadInternal(
        context: Context,
        song: Song,
        targetEdge: Int,
        callback: (Bitmap?) -> Unit,
        queueRetry: Int = 0
    ) {
        val cacheKey = key(song)
        val requestKey = song.id // Track-scoped requests prevent a no-art track masking its album mates.
        // A matching memory variant is a zero-I/O hit. Smaller list art is derived
        // from the canonical 512px cache on the worker, never resized during binding.
        peek(song, targetEdge)?.let { callback(it); return }

        val request: PendingRequest
        val shouldStart: Boolean
        synchronized(inFlight) {
            val existing = inFlight[requestKey]
            if (existing != null) {
                // Avoid unbounded callback growth when the same visible rows are rebound.
                if (existing.callbacks.size < MAX_CALLBACKS_PER_KEY) existing.callbacks.add(ArtworkCallback(targetEdge, callback))
                request = existing
                shouldStart = false
            } else {
                request = PendingRequest(mutableListOf(ArtworkCallback(targetEdge, callback)))
                inFlight[requestKey] = request
                shouldStart = true
            }
        }
        if (!shouldStart) return

        val appContext = context.applicationContext
        val task = Runnable {
            val mayRun = synchronized(inFlight) { inFlight[requestKey] === request }
            if (!mayRun) {
                synchronized(inFlight) { if (inFlight[requestKey] === request) inFlight.remove(requestKey) }
                return@Runnable
            }

            val directory = artworkDirectory(appContext)
            // A track without embedded art must not poison the album-wide cache:
            // another track in the same album may contain the cover.
            val noArtMarker = File(directory, "${song.id}.none")
            val bitmap = cache.get(cacheKey) ?: readDiskCache(directory, cacheKey) ?: if (noArtMarker.exists()) {
                null
            } else {
                val decoded = decode(appContext, song.uriString)
                decoded.bitmap?.also { image ->
                    if (writeDiskCache(directory, cacheKey, song.id, image)) noArtMarker.delete()
                } ?: run {
                    // Only memoize a definitive "no embedded picture" result. URI
                    // permission/I/O/decoder failures are transient and must remain retryable.
                    if (decoded.definitiveNoArtwork) runCatching { noArtMarker.createNewFile() }
                    null
                }
            }
            if (bitmap != null) {
                // Schedule the bitmap's first GPU texture upload before the view needs
                // to draw it. This is especially useful for covers decoded during idle time.
                runCatching { bitmap.prepareToDraw() }
                cache.put(cacheKey, bitmap)
            }

            val callbacks = synchronized(inFlight) {
                if (inFlight[requestKey] === request) {
                    inFlight.remove(requestKey)
                    request.callbacks.toList()
                } else emptyList()
            }
            if (callbacks.isNotEmpty()) {
                // Downsample on the artwork worker, not in the main-thread callback.
                val deliveries = callbacks.map { pending ->
                    val variant = bitmap?.let { image ->
                        if (pending.edge >= FOREGROUND_ARTWORK_EDGE) image
                        else thumbnailCache.get(cacheKey) ?: createThumbnail(image, pending.edge).also { thumbnail ->
                            runCatching { thumbnail.prepareToDraw() }
                            thumbnailCache.put(cacheKey, thumbnail)
                        }
                    }
                    pending.callback to variant
                }
                main.post {
                    deliveries.forEach { (pendingCallback, variant) ->
                        runCatching { pendingCallback(variant) }
                    }
                }
            }
        }
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            // Queue is intentionally bounded. Remove the marker so a later idle bind
            // can retry instead of leaving this artwork key permanently in-flight.
            val retryCallbacks = synchronized(inFlight) {
                if (inFlight[requestKey] === request) {
                    inFlight.remove(requestKey)
                    request.callbacks.toList()
                } else emptyList()
            }
            // A saturated list workload must not permanently drop foreground artwork
            // or callbacks belonging to other views waiting for the same track.
            // Retry a few times with a delay; adapters still validate holder identity.
            if (queueRetry < MAX_QUEUE_RETRIES) {
                retryCallbacks.forEach { pending ->
                    main.postDelayed(
                        { loadInternal(appContext, song, pending.edge, pending.callback, queueRetry + 1) },
                        QUEUE_RETRY_DELAY_MS
                    )
                }
            }
        }
    }

    private fun createThumbnail(source: Bitmap, targetEdge: Int): Bitmap {
        val safeEdge = targetEdge.coerceIn(64, FOREGROUND_ARTWORK_EDGE - 1)
        val scale = safeEdge.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun artworkDirectory(context: Context): File =
        File(context.filesDir, "rvh_artwork_v2").apply { mkdirs() }

    private fun readDiskCache(directory: File, key: Long): Bitmap? {
        val file = File(directory, "$key.jpg")
        if (!file.isFile) return null
        return try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) { null }
    }

    private fun writeDiskCache(directory: File, key: Long, trackId: Long, bitmap: Bitmap): Boolean {
        val target = File(directory, "$key.jpg")
        // Track-unique temp files prevent two tracks from the same album colliding.
        val temporary = File(directory, "$key.$trackId.tmp")
        try {
            val compressed = FileOutputStream(temporary).use { output ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                output.flush()
                success
            }
            if (!compressed) {
                temporary.delete()
                return false
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target.isFile && target.length() > 0L
        } catch (_: Exception) {
            temporary.delete()
            return false
        }
    }

    // Artwork is a user-library asset, not a disposable HTTP cache. Keep successful
    // thumbnails on disk until the user clears app data or uninstalls the app.
    // This intentionally has no age/size LRU eviction: revisiting a track must not
    // trigger another MediaMetadataRetriever extraction just because the cache filled.

    private data class DecodeOutcome(val bitmap: Bitmap?, val definitiveNoArtwork: Boolean)

    private fun decode(context: Context, uriString: String): DecodeOutcome {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme.isNullOrBlank()) return DecodeOutcome(null, false)
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture
                ?: return DecodeOutcome(null, true)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return DecodeOutcome(null, false)
            var sample = 1
            while (bounds.outWidth / sample > FOREGROUND_ARTWORK_EDGE || bounds.outHeight / sample > FOREGROUND_ARTWORK_EDGE) sample *= 2
            DecodeOutcome(
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }),
                false
            )
        } catch (_: Exception) {
            DecodeOutcome(null, false)
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

}

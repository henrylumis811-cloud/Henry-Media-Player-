package com.henrylumis.mediaprayer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.henrylumis.mediaprayer.audio.DualPlayerBridge
import com.henrylumis.mediaprayer.audio.EqualizerController
import com.henrylumis.mediaprayer.audio.ReplayGainReader
import com.henrylumis.mediaprayer.data.MusicScanner
import com.henrylumis.mediaprayer.data.SongSorter
import com.henrylumis.mediaprayer.data.toMediaItem
import com.henrylumis.mediaprayer.util.ListeningStatsStore
import com.henrylumis.mediaprayer.util.PlaybackStateStore
import com.henrylumis.mediaprayer.util.Prefs
import com.henrylumis.mediaprayer.util.RecentlyPlayedStore
import com.henrylumis.mediaprayer.util.SavedPlayback
import com.henrylumis.mediaprayer.util.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Media3's MediaSessionService owns the ExoPlayer instance, the system
 * notification, lock-screen controls, and the foreground-service lifecycle
 * all in one place.
 *
 * Playback itself is delegated to DualPlayerBridge, which wraps two real
 * ExoPlayer instances and ping-pongs between them for genuinely gapless
 * (and, when enabled, crossfaded) transitions -- see that class for why.
 * MediaSession is bound to the bridge, not to a raw ExoPlayer, so every
 * standard playback command (from this app OR from lock-screen/Bluetooth/
 * Android Auto controls) automatically gets the same smooth handling.
 */
class PlaybackService : MediaSessionService() {

    private lateinit var bridge: DualPlayerBridge
    private var mediaSession: MediaSession? = null
    private lateinit var sleepTimerInternal: SleepTimer
    val sleepTimer: SleepTimer get() = sleepTimerInternal
    private val stateSaveHandler = Handler(Looper.getMainLooper())
    private val mediaObserverHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var mediaObserver: ContentObserver? = null
    private var storageEventsReceiver: BroadcastReceiver? = null
    private var mediaScanJob: Job? = null
    private var mediaChangeGeneration = 0L
    private val pendingRemovedMediaIds = mutableSetOf<String>()
    private var queuePruneRetryCount = 0
    private var restoreJob: Job? = null
    private var restoreGeneration = 0L
    private var applyingRestoredSession = false
    companion object {
        var instance: PlaybackService? = null
        private const val PLAY_COUNT_THRESHOLD_MS = 30_000L
    }

    /** Same-process accessor for the visualizer -- reflects whichever internal
     *  player is currently active, but the audio session id it reports is
     *  fixed for the bridge's lifetime, so callers don't need to re-attach. */
    val exoPlayer get() = bridge.activeExoPlayer

    var equalizer: EqualizerController? = null
        private set

    /** The track a crossfade is currently fading INTO, before the switch has
     *  actually landed -- lets the UI show the new track instantly on tap. */
    val pendingCrossfadeTarget: MediaItem? get() = bridge.pendingTarget

    /** Direct, same-process seek -- bypasses the MediaController/Session IPC
     *  layer entirely for reliability (dragging the Altar seek bar was landing
     *  on the session but not visibly taking effect, most likely a command-
     *  availability propagation timing issue over that layer). */
    /** Same-process settings hook: update both internal playback players. */
    fun setPlaybackSpeed(speed: Float) {
        try { bridge.setPlaybackSpeed(speed) } catch (_: Exception) {}
    }

    /** Start optional beat-grid analysis only when Crossfade is enabled. */
    fun refreshBeatAnalysis() {
        try { bridge.refreshBeatAnalysis() } catch (_: Exception) {}
    }

    /** Re-apply ReplayGain immediately after the user changes normalization.
     * Both the current and any pending crossfade target are refreshed. */
    fun refreshNormalization() {
        val current = bridge.currentMediaItem
        applyNormalizationForCurrentTrack(current)
        bridge.pendingTarget?.let { applyNormalizationForCurrentTrack(it) }
    }

    fun seekToPosition(positionMs: Long) {
        try { bridge.seekTo(positionMs) } catch (_: Exception) {}
    }

    /** Direct queue/media selection by stable Media3 media ID. */
    fun playMediaId(mediaId: String) {
        try { bridge.playMediaId(mediaId) } catch (_: Exception) {}
    }

    /** Same-process navigation used by the visible player controls. This
     * avoids waiting for a MediaController command round-trip, so Next is a
     * single-tap action even when crossfade is disabled. */
    fun skipNextDirect() {
        try { bridge.skipToNext() } catch (_: Exception) {}
    }

    /** Direct queue selection used by the Queue screen. */
    fun playQueueIndexDirect(index: Int) {
        try {
            val item = bridge.getQueueItems().getOrNull(index) ?: return
            bridge.playMediaId(item.mediaId)
        } catch (_: Exception) {}
    }

    fun moveQueueItem(from: Int, to: Int) {
        try { bridge.moveItem(from, to) } catch (_: Exception) {}
    }

    fun removeQueueItem(index: Int) {
        try { bridge.removeItem(index) } catch (_: Exception) {}
    }

    fun clearQueue() {
        invalidatePendingSessionRestore()
        try { bridge.clearQueue() } catch (_: Exception) {}
    }


    // --- Listening stats tracking state ---
    private var statsLastPositionMs = 0L
    private var statsAccumulatedMs = 0L
    private var statsCountedThisTrack = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        bridge = DualPlayerBridge(applicationContext)

        // The shared session id is fixed at construction, so effects can be
        // set up immediately -- no need to wait for playback to start.
        equalizer = EqualizerController(applicationContext, bridge.audioSessionId).also { controller ->
            controller.onHeadroomChanged = bridge::setEqualizerHeadroom
        }

        // Restore navigation modes before rebuilding the saved queue so the
        // first Next/Previous after process death follows the same behavior
        // the user had chosen previously.
        bridge.restorePlaybackModes(
            Prefs.isShuffleEnabled(applicationContext),
            Prefs.getRepeatMode(applicationContext)
        )

        bridge.onTransitionTarget = { mediaItem ->
            // Read ReplayGain as soon as the target is known, not after the fade
            // has already completed. This prevents a loud incoming track from
            // briefly arriving at full level. The bridge also guards the volume
            // layer separately from its crossfade curve.
            applyNormalizationForCurrentTrack(mediaItem)
        }

        bridge.onTransitionStarted = { outgoing, _, natural ->
            val id = outgoing.mediaId
            if (natural) {
                ListeningStatsStore.incrementCompletion(applicationContext, id)
            } else if (statsAccumulatedMs >= 3_000L) {
                // Very short taps are not treated as meaningful skips. Once a
                // listener has actually spent a few seconds on the track, a
                // navigation away is recorded as a skip.
                ListeningStatsStore.incrementSkip(applicationContext, id)
            }
        }

        bridge.onQueueAdvanced = { mediaItem ->
            // A real queue selection made while the library scan is restoring an
            // old session takes precedence. Without this guard, a slow scan can
            // finish later and silently replace the listener's newly chosen queue.
            if (!applyingRestoredSession) invalidatePendingSessionRestore()
            mediaItem?.mediaId?.let { RecentlyPlayedStore.addPlayed(applicationContext, it) }
            applyNormalizationForCurrentTrack(mediaItem)
            resetStatsTrackingForNewTrack(mediaItem)
            // If the just-finished track had been removed from MediaStore while
            // it was audible, it was deliberately retained until playback moved on.
            prunePendingRemovedTracks()
            savePlaybackState()
        }

        bridge.onPlaybackStateChanged = {
            // A media item removed while it was current is intentionally retained
            // until playback leaves it. Re-check on subsequent state changes too,
            // including resume after a transition was paused mid-fade.
            prunePendingRemovedTracks()
            // Save immediately for explicit Play/Pause/Seek/Stop commands.
            // The periodic checkpoint remains as a crash-safety net.
            savePlaybackState()
        }

        // Notification/lock-screen taps should open the player directly, not
        // replay the cinematic launcher every time a user taps a media control.
        // SplashActivity is only the true app-launch entry point.
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1001, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionBuilder = MediaSession.Builder(this, bridge)
        pendingIntent?.let { sessionBuilder.setSessionActivity(it) }
        mediaSession = sessionBuilder.build()

        sleepTimerInternal = SleepTimer(this)
        sleepTimerInternal.onFire = {
            // The timer only pauses playback; it deliberately does not touch
            // the crossfade/transition engine.
            bridge.pause()
        }

        registerMediaStoreObserver()
        registerStorageEventsReceiver()
        restoreLastSessionIfFresh()
        startPeriodicStateSaving()
    }

    /** Keep the playback service restartable after an unexpected process kill.
     * Media3 still controls the actual foreground lifecycle and explicit stopSelf()
     * calls are not restarted by Android. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }


    /** Watch external audio changes while the service is alive. Scans are debounced
     * and run off the main thread; a failed scan never prunes the active queue. */
    private fun registerMediaStoreObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(mediaObserverHandler) {
            override fun onChange(selfChange: Boolean) = scheduleMediaStoreReconciliation()
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) = scheduleMediaStoreReconciliation()
        }
        try {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
            )
            mediaObserver = observer
        } catch (_: Exception) {
            // Playback remains usable; queue reconciliation can still occur on restore.
        }
    }

    /** Re-run reconciliation after the Activity observes a permission grant.
     * Permission loss deliberately does not prune or clear the live queue. */
    fun refreshMediaLibraryAfterPermissionChange() {
        if (MusicScanner.hasAudioPermission(applicationContext)) {
            // A previous process/service restoration may have been deferred because
            // audio permission was unavailable. Retry it now that MediaStore can be
            // read, but never replace a queue the listener has already loaded.
            if (bridge.mediaItemCount == 0) restoreLastSessionIfFresh()
            scheduleMediaStoreReconciliation()
        }
    }

    /** MediaStore notifications are the primary source of truth; volume events
     * provide an additional prompt when removable storage is mounted/ejected. */
    private fun registerStorageEventsReceiver() {
        if (storageEventsReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_MEDIA_MOUNTED,
                    Intent.ACTION_MEDIA_UNMOUNTED,
                    Intent.ACTION_MEDIA_EJECT,
                    Intent.ACTION_MEDIA_REMOVED,
                    Intent.ACTION_MEDIA_BAD_REMOVAL,
                    Intent.ACTION_MEDIA_SHARED -> scheduleMediaStoreReconciliation()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_SHARED)
            addDataScheme("file")
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            storageEventsReceiver = receiver
        } catch (_: Exception) {
            // MediaStore ContentObserver remains the fallback.
        }
    }

    private fun scheduleMediaStoreReconciliation() {
        mediaChangeGeneration++
        val generation = mediaChangeGeneration
        mediaScanJob?.cancel()
        mediaObserverHandler.removeCallbacks(mediaReconcileRunnable)
        mediaReconcileRunnable = Runnable {
            mediaScanJob = serviceScope.launch {
                try {
                    val songs = MusicScanner.scan(applicationContext)
                    val validIds = songs.mapTo(HashSet(songs.size)) { it.id.toString() }
                    withContext(Dispatchers.Main) {
                        if (generation != mediaChangeGeneration) return@withContext
                        pendingRemovedMediaIds.removeAll(validIds)
                        bridge.getQueueItems().forEach { item ->
                            if (item.mediaId !in validIds) pendingRemovedMediaIds.add(item.mediaId)
                        }
                        queuePruneRetryCount = 0
                        prunePendingRemovedTracks()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    // Permission loss is not interpreted as an empty library.
                } catch (_: Exception) {
                    // Provider errors are transient; never mutate the queue from a failed scan.
                }
            }
        }
        mediaObserverHandler.postDelayed(mediaReconcileRunnable, 700L)
    }

    private var mediaReconcileRunnable = Runnable { }

    /** Remove stale upcoming/history entries without interrupting an active song.
     * A removed current track is retained until it naturally/manual-advances. */
    private fun prunePendingRemovedTracks() {
        if (pendingRemovedMediaIds.isEmpty()) return
        if (bridge.isTransitionInProgress()) {
            if (queuePruneRetryCount++ < 20) {
                mediaObserverHandler.postDelayed({ prunePendingRemovedTracks() }, 750L)
            }
            return
        }
        val currentId = bridge.currentMediaItem?.mediaId
        val queue = bridge.getQueueItems()
        val staleIndices = queue.mapIndexedNotNull { index, item ->
            if (item.mediaId in pendingRemovedMediaIds && item.mediaId != currentId) index else null
        }.sortedDescending()
        staleIndices.forEach { index ->
            try { bridge.removeItem(index) } catch (_: Exception) { }
        }
        val stillQueued = bridge.getQueueItems().mapTo(HashSet()) { it.mediaId }
        pendingRemovedMediaIds.retainAll(stillQueued)
        if (staleIndices.isNotEmpty()) savePlaybackState()
    }

    // --- Volume normalization (ReplayGain) ---

    private var normalizationGeneration = 0L

    private fun applyNormalizationForCurrentTrack(mediaItem: MediaItem?) {
        normalizationGeneration++
        val generation = normalizationGeneration
        val itemId = mediaItem?.mediaId ?: return
        bridge.setTrackAttenuation(itemId, 1f)
        if (!Prefs.isNormalizationEnabled(applicationContext)) return
        val dataPath = mediaItem.mediaMetadata.extras?.getString("data_path") ?: return

        serviceScope.launch {
            val gainDb = withContext(Dispatchers.IO) { ReplayGainReader.readTrackGainDb(dataPath) }
            withContext(Dispatchers.Main) {
                if (generation != normalizationGeneration) return@withContext
                if (bridge.currentMediaItem?.mediaId != itemId && bridge.pendingTarget?.mediaId != itemId) return@withContext
                if (gainDb == null) return@withContext
                // Keep normalization fidelity-safe: only attenuate tracks whose ReplayGain
                // calls for it. Do not boost the PCM output with a system LoudnessEnhancer;
                // that can alter the Bluetooth output path and introduce clipping/distortion.
                val attenuation = if (gainDb < 0.0) {
                    10.0.pow(gainDb / 20.0).coerceIn(0.15, 1.0).toFloat()
                } else {
                    1f
                }
                bridge.setTrackAttenuation(itemId, attenuation)
            }
        }
    }

    // --- Listening analytics ---

    private fun resetStatsTrackingForNewTrack(mediaItem: MediaItem?) {
        statsAccumulatedMs = 0L
        statsCountedThisTrack = false
        statsLastPositionMs = 0L
        if (mediaItem == null) return

        val trackId = mediaItem.mediaId
        val trackTitle = mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
        val trackArtist = mediaItem.mediaMetadata.artist?.toString() ?: ""
        val trackDuration = mediaItem.mediaMetadata.extras?.getLong("duration_ms") ?: 0L
        val trackAlbum = mediaItem.mediaMetadata.albumTitle?.toString() ?: ""
        serviceScope.launch(Dispatchers.IO) {
            ListeningStatsStore.setTrackMeta(
                applicationContext, trackId, trackTitle, trackArtist, trackDuration, trackAlbum
            )
        }

        if (ListeningStatsStore.getGenre(applicationContext, mediaItem.mediaId) == null) {
            val dataPath = mediaItem.mediaMetadata.extras?.getString("data_path")
            if (dataPath != null) {
                serviceScope.launch {
                    val genre = withContext(Dispatchers.IO) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(dataPath)
                            val g = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
                            retriever.release()
                            g
                        } catch (e: Exception) {
                            null
                        }
                    }
                    ListeningStatsStore.setGenreIfAbsent(applicationContext, mediaItem.mediaId, genre)
                }
            }
        }
    }

    /** Called from the periodic tick -- accumulates real listened time (seek-aware:
     *  a jump in position isn't counted as "listened", only steady playback is). */
    private fun trackListeningProgress() {
        val item = bridge.currentMediaItem ?: return
        if (!bridge.isPlaying) return
        val position = bridge.currentPosition
        val delta = position - statsLastPositionMs
        statsLastPositionMs = position

        if (delta in 1..10_000) {
            val trackId = item.mediaId
            serviceScope.launch(Dispatchers.IO) {
                ListeningStatsStore.addListenedMs(applicationContext, trackId, delta)
            }
            statsAccumulatedMs += delta
            if (!statsCountedThisTrack && statsAccumulatedMs >= PLAY_COUNT_THRESHOLD_MS) {
                statsCountedThisTrack = true
                ListeningStatsStore.incrementPlayCount(applicationContext, item.mediaId)
            }
        }
    }

    /** Rebuilds the FULL library (in the same sort order shown in the Library
     *  tab), positioned at the last-played track and its saved position, so
     *  pressing Play resumes not just that one song but continues naturally
     *  through the rest of your library from there -- exactly like the
     *  session never ended. Falls back to just the single saved track if the
     *  library scan fails for any reason, rather than leaving nothing loaded. */
    private fun invalidatePendingSessionRestore() {
        restoreGeneration++
        restoreJob?.cancel()
        restoreJob = null
    }

    private fun restoreLastSessionIfFresh() {
        val saved = PlaybackStateStore.load(applicationContext) ?: return
        // A persisted empty queue is an intentional user state (for example,
        // removing the final queue item), not a legacy snapshot. Do not rebuild
        // the full library and unexpectedly resurrect playback after restart.
        // Older installs without a queue snapshot still use the compatibility
        // fallback below.
        if (saved.hasQueueSnapshot && saved.queueMediaIds.isEmpty()) return
        val generationAtStart = restoreGeneration

        restoreJob = serviceScope.launch {
            try {
                val allSongs = MusicScanner.scan(applicationContext)
                val sortMode = SongSorter.modeFromSavedName(Prefs.getSortMode(applicationContext))
                val sorted = SongSorter.sort(applicationContext, allSongs, sortMode)
                withContext(Dispatchers.Main) {
                    // Re-check on the main thread immediately before mutation.
                    // A user-selected queue or an earlier restore invalidates this
                    // result, even if the background scan has already completed.
                    if (generationAtStart != restoreGeneration || bridge.mediaItemCount > 0) return@withContext
                    applyingRestoredSession = true
                    try {
                        if (sorted.isEmpty()) {
                            restoreSingleTrackFallback(saved)
                        } else {
                            val libraryItems = sorted.map { it.toMediaItem() }
                            val byId = libraryItems.associateBy { it.mediaId }
                            // Prefer the exact saved queue ordering. Missing/deleted tracks
                            // are filtered out; duplicate IDs are collapsed deterministically.
                            val restoredIds = saved.queueMediaIds
                                .asSequence()
                                .filter { it in byId }
                                .distinct()
                                .toList()

                            // A non-empty persisted queue is authoritative even when
                            // every saved media ID has since disappeared. Falling back
                            // to the complete library here silently changes the user's
                            // queue into an unrelated all-library queue after deletion
                            // or removable-storage loss. Retain only the saved current
                            // item as a best-effort URI fallback; never invent a new queue.
                            if (saved.hasQueueSnapshot && saved.queueMediaIds.isNotEmpty() && restoredIds.isEmpty()) {
                                restoreSingleTrackFallback(saved)
                                savePlaybackState()
                                return@withContext
                            }

                            val orderedItems = if (restoredIds.isNotEmpty()) {
                                restoredIds.mapNotNull(byId::get)
                            } else libraryItems
                            val items = if (orderedItems.any { it.mediaId == saved.mediaId }) {
                                orderedItems
                            } else {
                                val savedCurrent = byId[saved.mediaId]
                                if (savedCurrent == null) orderedItems
                                else listOf(savedCurrent) + orderedItems.filterNot { it.mediaId == saved.mediaId }
                            }
                            val targetIndex = items.indexOfFirst { it.mediaId == saved.mediaId }
                                .let { if (it == -1) 0 else it }
                            bridge.restoreQueueAtPosition(items, targetIndex, saved.positionMs)
                            bridge.restoreShuffleHistoryMediaIds(
                                saved.shuffleHistoryMediaIds,
                                saved.recentlyShuffledMediaIds
                            )
                            // restoreQueueAtPosition publishes the current item before
                            // history is reattached; checkpoint again so an immediate
                            // process death cannot overwrite the restored history with empty lists.
                            savePlaybackState()
                        }
                    } finally {
                        applyingRestoredSession = false
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: SecurityException) {
                // Permission loss is not an empty library. Leave the durable
                // snapshot untouched and retry when the Activity reports a grant.
            } catch (_: Exception) {
                // A transient MediaStore/provider failure must not collapse the
                // persisted multi-track queue to a one-track fallback. In
                // particular, restoreSingleTrackFallback triggers normal queue
                // callbacks which would overwrite the only durable snapshot.
                // Keep the saved state intact; a later permission refresh or
                // service recreation can retry the full restore.
            } finally {
                if (generationAtStart == restoreGeneration) restoreJob = null
            }
        }
    }

    private fun restoreSingleTrackFallback(saved: com.henrylumis.mediaprayer.util.SavedPlayback) {
        try {
            val extras = android.os.Bundle().apply {
                saved.dataPath?.let { putString("data_path", it) }
                if (saved.durationMs > 0) putLong("duration_ms", saved.durationMs)
            }
            if (saved.albumId > 0L) extras.putLong("album_id", saved.albumId)
            val metadata = MediaMetadata.Builder()
                .setTitle(saved.title)
                .setArtist(saved.artist)
                .setAlbumTitle(saved.album)
                .apply {
                    // Match Song.toMediaItem: MediaStore album artwork is keyed
                    // by album ID. If unavailable, leave artwork unset so the
                    // system can use the app's RVH launcher icon as its fallback.
                    if (saved.albumId > 0L) {
                        setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/${saved.albumId}"))
                    }
                }
                .setExtras(extras)
                .build()
            val item = MediaItem.Builder()
                .setUri(saved.uri)
                .setMediaId(saved.mediaId)
                .setMediaMetadata(metadata)
                .build()
            bridge.restoreQueueAtPosition(listOf(item), 0, saved.positionMs)
        } catch (_: Exception) {
            // Corrupt/removed file -- just start with an empty queue instead of crashing.
        }
    }

    private fun savePlaybackState() {
        val queueIds = bridge.getQueueItems().map { it.mediaId }
        val shuffleHistoryIds = bridge.getShuffleHistoryMediaIds()
        val recentlyShuffledIds = bridge.getRecentlyShuffledMediaIds()
        val item = bridge.currentMediaItem
        if (item == null) {
            PlaybackStateStore.saveQueueSnapshot(
                applicationContext, queueIds, shuffleHistoryIds, recentlyShuffledIds
            )
            return
        }
        val uri = item.localConfiguration?.uri?.toString() ?: return
        PlaybackStateStore.save(
            applicationContext,
            SavedPlayback(
                mediaId = item.mediaId,
                uri = uri,
                title = item.mediaMetadata.title?.toString() ?: "",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                album = item.mediaMetadata.albumTitle?.toString() ?: "",
                positionMs = bridge.currentPosition,
                dataPath = item.mediaMetadata.extras?.getString("data_path"),
                durationMs = item.mediaMetadata.extras?.getLong("duration_ms") ?: 0L,
                albumId = item.mediaMetadata.extras?.getLong("album_id", -1L) ?: -1L,
                queueMediaIds = queueIds,
                shuffleHistoryMediaIds = shuffleHistoryIds,
                recentlyShuffledMediaIds = recentlyShuffledIds
            )
        )
    }

    private fun startPeriodicStateSaving() {
        val runnable = object : Runnable {
            override fun run() {
                if (bridge.mediaItemCount > 0) savePlaybackState()
                stateSaveHandler.postDelayed(this, 5000)
            }
        }
        stateSaveHandler.postDelayed(runnable, 5000)

        val statsRunnable = object : Runnable {
            override fun run() {
                trackListeningProgress()
                stateSaveHandler.postDelayed(this, 2000)
            }
        }
        stateSaveHandler.postDelayed(statsRunnable, 2000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackState()
        val player = mediaSession?.player
        val playbackActive = player != null && player.playWhenReady && player.mediaItemCount > 0
        val timerActive = ::sleepTimerInternal.isInitialized && sleepTimerInternal.isRunning()

        if (!playbackActive && timerActive) {
            // The timer is service-owned. Stopping an idle/paused service here
            // would dispose its only live countdown callback before the deadline.
            // Do not call MediaSessionService's default task-removed handling in
            // this branch either: it may stop an idle session service as well.
            // The persisted monotonic deadline still allows same-boot recovery
            // if Android later recreates the service, but cannot force execution
            // if the OS kills this non-foreground service in the meantime.
            return
        }

        if (!playbackActive) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlaybackState()
        stateSaveHandler.removeCallbacksAndMessages(null)
        mediaObserverHandler.removeCallbacksAndMessages(null)
        mediaScanJob?.cancel()
        mediaObserver?.let { observer ->
            try { contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
        mediaObserver = null
        storageEventsReceiver?.let { receiver ->
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
        }
        storageEventsReceiver = null
        serviceScope.cancel()
        if (::sleepTimerInternal.isInitialized) sleepTimerInternal.dispose()
        equalizer?.release()
        mediaSession?.release()
        mediaSession = null
        bridge.releaseAll()
        instance = null
        super.onDestroy()
    }
}

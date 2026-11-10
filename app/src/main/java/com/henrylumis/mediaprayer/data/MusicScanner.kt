package com.henrylumis.mediaprayer.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Scans the device MediaStore for playable audio.
 * Uses scoped-storage-safe MediaStore queries only -- no raw file path access,
 * so this works correctly on Android 10 through 14+.
 */
object MusicScanner {

    /** Fail explicitly when access is absent; callers must never interpret a denied
     * permission as an empty library and erase their last successful snapshot. */
    fun hasAudioPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission(context)) {
            throw SecurityException("Music permission is required to scan this device")
        }
        val songs = mutableListOf<Song>()
        // EXTERNAL_CONTENT_URI maps to MediaStore's synthetic external volume. It
        // aggregates mounted shared external volumes (including indexed removable
        // SD storage) without creating per-volume IDs that would collide in the
        // app's stable MediaStore-ID based queue and playlist model.
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor = context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        ) ?: throw IllegalStateException("MediaStore did not return a music cursor")

        cursor.use { c ->
            fun required(column: String): Int = c.getColumnIndex(column).also {
                check(it >= 0) { "MediaStore is missing required column: $column" }
            }
            val idCol = required(MediaStore.Audio.Media._ID)
            val titleCol = required(MediaStore.Audio.Media.TITLE)
            val artistCol = required(MediaStore.Audio.Media.ARTIST)
            val albumCol = required(MediaStore.Audio.Media.ALBUM)
            val durationCol = required(MediaStore.Audio.Media.DURATION)
            val albumIdCol = required(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedCol = required(MediaStore.Audio.Media.DATE_ADDED)
            // DATA is deprecated and may be unavailable for some providers/storage
            // configurations. Playback uses the content URI, so this path is optional.
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)

            var rowsSinceCancellationCheck = 0
            while (c.moveToNext()) {
                if (++rowsSinceCancellationCheck >= 128) {
                    currentCoroutineContext().ensureActive()
                    rowsSinceCancellationCheck = 0
                }
                val id = c.getLong(idCol)
                if (id <= 0L) continue
                val contentUri = ContentUris.withAppendedId(collection, id)
                songs.add(
                    Song(
                        id = id,
                        title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                        artist = c.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                        album = c.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                        durationMs = c.getLong(durationCol).coerceAtLeast(0L),
                        uriString = contentUri.toString(),
                        albumId = c.getLong(albumIdCol),
                        dateAdded = c.getLong(dateAddedCol),
                        dataPath = if (dataCol >= 0 && !c.isNull(dataCol)) c.getString(dataCol) else null
                    )
                )
            }
        }
        currentCoroutineContext().ensureActive()
        songs
    }
}

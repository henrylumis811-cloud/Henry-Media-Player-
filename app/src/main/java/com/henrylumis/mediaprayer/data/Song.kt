package com.henrylumis.mediaprayer.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val albumId: Long,
    val dateAdded: Long = 0L,
    val dataPath: String? = null
)

/** Single source of truth for building a MediaItem from a Song, used by both
 *  Library/Queue playback and the restore-on-launch path, so both carry the
 *  same extras (data_path, duration_ms) that lyrics/normalization/analytics rely on. */
fun Song.toMediaItem(): MediaItem {
    val extras = android.os.Bundle().apply {
        dataPath?.let { putString("data_path", it) }
        putLong("duration_ms", durationMs)
        putLong("album_id", albumId)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        // MediaStore's legacy album-art endpoint is keyed by ALBUM_ID, not
        // by the audio row's MEDIA_ID. A media/{id}/albumart URI is not the
        // documented album-art lookup and can leave system media controls
        // without artwork even when the app's own artwork cache has it.
        .apply {
            if (albumId > 0L) {
                setArtworkUri(Uri.parse("content://media/external/audio/albumart/$albumId"))
            }
        }
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setUri(uriString)
        .setMediaId(id.toString())
        .setMediaMetadata(metadata)
        .build()
}


/** Reconstructs the canonical artwork lookup key for any player/queue MediaItem. */
fun MediaItem.toArtworkSong(): Song? {
    val uri = localConfiguration?.uri ?: return null
    val id = mediaId.toLongOrNull() ?: return null
    val metadata = mediaMetadata
    val extras = metadata.extras
    return Song(
        id = id,
        title = metadata.title?.toString() ?: "Unknown",
        artist = metadata.artist?.toString() ?: "Unknown Artist",
        album = metadata.albumTitle?.toString() ?: "Unknown Album",
        durationMs = extras?.getLong("duration_ms", 0L) ?: 0L,
        uriString = uri.toString(),
        albumId = extras?.getLong("album_id", -1L) ?: -1L,
        dataPath = extras?.getString("data_path")
    )
}

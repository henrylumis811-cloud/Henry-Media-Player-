package com.henrylumis.mediaprayer.util

import android.content.Context
import org.json.JSONArray

data class SavedPlayback(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val positionMs: Long,
    val dataPath: String? = null,
    val durationMs: Long = 0L,
    val albumId: Long = -1L,
    val queueMediaIds: List<String> = emptyList(),
    val shuffleHistoryMediaIds: List<String> = emptyList(),
    val recentlyShuffledMediaIds: List<String> = emptyList(),
    /** True when this install has written the v59+ queue snapshot key.
     * Distinguishes an intentionally empty queue from a legacy state that
     * predates queue persistence and therefore needs the library fallback. */
    val hasQueueSnapshot: Boolean = false
)

/**
 * Persists just enough about the current track to resume exactly where
 * playback left off, even after the app (and its background service) has
 * been fully closed and the process restarted from scratch.
 */
object PlaybackStateStore {
    private const val FILE = "media_prayer_playback_state"

    fun save(context: Context, state: SavedPlayback) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("media_id", state.mediaId)
            .putString("uri", state.uri)
            .putString("title", state.title)
            .putString("artist", state.artist)
            .putString("album", state.album)
            .putLong("position_ms", state.positionMs)
            .putString("data_path", state.dataPath)
            .putLong("duration_ms", state.durationMs)
            .putLong("album_id", state.albumId)
            .putString("queue_media_ids", JSONArray(state.queueMediaIds).toString())
            .putString("shuffle_history_media_ids", JSONArray(state.shuffleHistoryMediaIds).toString())
            .putString("recently_shuffled_media_ids", JSONArray(state.recentlyShuffledMediaIds).toString())
            .putBoolean("queue_snapshot_present", true)
            .apply()
    }

    /** Updates queue-only state even when the player has no current item. This
     * prevents an emptied queue from resurrecting an obsolete saved queue. */
    fun saveQueueSnapshot(
        context: Context,
        queueMediaIds: List<String>,
        shuffleHistoryMediaIds: List<String>,
        recentlyShuffledMediaIds: List<String>
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("queue_media_ids", JSONArray(queueMediaIds).toString())
            .putString("shuffle_history_media_ids", JSONArray(shuffleHistoryMediaIds).toString())
            .putString("recently_shuffled_media_ids", JSONArray(recentlyShuffledMediaIds).toString())
            .putBoolean("queue_snapshot_present", true)
            .apply()
    }

    fun load(context: Context): SavedPlayback? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val mediaId = prefs.getString("media_id", null) ?: return null
        val uri = prefs.getString("uri", null) ?: return null
        return SavedPlayback(
            mediaId = mediaId,
            uri = uri,
            title = prefs.getString("title", "") ?: "",
            artist = prefs.getString("artist", "") ?: "",
            album = prefs.getString("album", "") ?: "",
            positionMs = prefs.getLong("position_ms", 0L),
            dataPath = prefs.getString("data_path", null),
            durationMs = prefs.getLong("duration_ms", 0L),
            albumId = prefs.getLong("album_id", -1L),
            queueMediaIds = readStringArray(prefs.getString("queue_media_ids", null)),
            shuffleHistoryMediaIds = readStringArray(prefs.getString("shuffle_history_media_ids", null)),
            recentlyShuffledMediaIds = readStringArray(prefs.getString("recently_shuffled_media_ids", null)),
            hasQueueSnapshot = prefs.getBoolean("queue_snapshot_present", prefs.contains("queue_media_ids"))
        )
    }

    private fun readStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val value = json.optString(index).takeIf { it.isNotBlank() } ?: continue
                    add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

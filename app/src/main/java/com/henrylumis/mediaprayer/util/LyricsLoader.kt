package com.henrylumis.mediaprayer.util

import android.content.Context
import androidx.media3.common.MediaItem
import com.henrylumis.mediaprayer.audio.EmbeddedLyricsReader
import com.henrylumis.mediaprayer.data.LyricsLine
import com.henrylumis.mediaprayer.data.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shared lyrics lookup used by the full Lyrics screen and compact live previews. */
object LyricsLoader {
    data class Result(
        val lines: List<LyricsLine>,
        val synced: Boolean,
        val durationMs: Long
    )

    suspend fun load(context: Context, item: MediaItem?): Result = withContext(Dispatchers.IO) {
        if (item == null) return@withContext Result(emptyList(), true, 0L)

        val mediaId = item.mediaId
        val dataPath = item.mediaMetadata.extras?.getString("data_path")
        val durationMs = item.mediaMetadata.extras?.getLong("duration_ms", 0L) ?: 0L

        val lrcPath = LyricsParser.findLrcPath(dataPath)
        if (lrcPath != null) {
            try {
                val file = File(lrcPath)
                if (file.exists()) {
                    val synced = LyricsParser.parse(file.readText())
                    if (synced.isNotEmpty()) return@withContext Result(synced, true, durationMs)
                }
            } catch (_: Exception) { }
        }

        try {
            val embedded = EmbeddedLyricsReader.read(
                context,
                item.localConfiguration?.uri?.toString() ?: mediaId,
                dataPath
            )
            if (embedded != null) {
                if (embedded.syncedLines.isNotEmpty()) {
                    return@withContext Result(embedded.syncedLines, true, durationMs)
                }
                if (!embedded.plainText.isNullOrBlank()) {
                    val plain = embedded.plainText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LyricsLine(0L, it) }
                    if (plain.isNotEmpty()) return@withContext Result(plain, false, durationMs)
                }
            }
        } catch (_: Exception) { }

        if (!mediaId.isNullOrBlank()) {
            val stored = LyricsStore.get(context, mediaId)
            if (!stored.isNullOrBlank()) {
                val synced = LyricsParser.parse(stored)
                if (synced.isNotEmpty()) return@withContext Result(synced, true, durationMs)
                val plain = stored.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { LyricsLine(0L, it) }
                if (plain.isNotEmpty()) return@withContext Result(plain, false, durationMs)
            }
        }

        Result(emptyList(), true, durationMs)
    }
}

package com.henrylumis.mediaprayer.data

/** A word-level cue inside a synchronized lyric line. */
data class LyricsWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList()
)

/** One authoritative mapping from playback position to lyric state. */
object LyricsTimeline {
    fun activeIndex(
        lines: List<LyricsLine>,
        positionMs: Long,
        durationMs: Long = 0L,
        synced: Boolean = true
    ): Int {
        if (lines.isEmpty()) return -1
        if (!synced) {
            val duration = durationMs.takeIf { it > 0L } ?: (lines.size * 4500L).coerceAtLeast(1L)
            return ((positionMs.toDouble() / duration) * lines.size)
                .toInt()
                .coerceIn(0, lines.lastIndex)
        }
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun activeWordIndex(line: LyricsLine?, positionMs: Long): Int {
        val words = line?.words ?: return -1
        if (words.isEmpty()) return -1
        var low = 0
        var high = words.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (words[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/** LRC parser supporting ordinary line timing and Enhanced-LRC word timing. */
object LyricsParser {
    private val LINE_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val OFFSET_REGEX = Regex("""\[offset:([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    private val METADATA_REGEX = Regex("""\[(?:ar|ti|al|by|re|ve|length):.*?]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<LyricsLine> {
        var offsetMs = 0L
        val parsed = mutableListOf<LyricsLine>()

        raw.lineSequence().forEach { originalLine ->
            val line = originalLine.replace("\uFEFF", "").trimStart()
            val offsetMatch = OFFSET_REGEX.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            val matches = LINE_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            if (matches.size == 1 && METADATA_REGEX.matches(line)) return@forEach

            val textStart = matches.last().range.last + 1
            val body = line.substring(textStart).trim()
            if (body.isEmpty() || body.startsWith("//")) return@forEach

            val words = parseEnhancedWords(body, offsetMs)
            val cleanText = if (words.isEmpty()) body else words.joinToString("") { it.text }
            matches.forEach { match ->
                val timeMs = timestampMs(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3],
                    offsetMs
                )
                parsed += LyricsLine(timeMs, cleanText, words)
            }
        }

        val sorted = parsed
            .distinctBy { it.timeMs to it.text }
            .sortedBy { it.timeMs }

        // Enhanced LRC supplies word starts, not necessarily explicit word ends.
        // Derive each word's end from the next cue or the following lyric line so
        // seeking, pauses, and short words never leave a stale highlight behind.
        return sorted.mapIndexed { index, line ->
            if (line.words.isEmpty()) return@mapIndexed line
            val nextLineStart = sorted.getOrNull(index + 1)?.timeMs
            val words = line.words.mapIndexed { wordIndex, word ->
                val nextWordStart = line.words.getOrNull(wordIndex + 1)?.startMs
                val derivedEnd = nextWordStart
                    ?: nextLineStart
                    ?: (word.startMs + 1000L)
                word.copy(endMs = maxOf(word.startMs + 1L, derivedEnd))
            }
            line.copy(words = words)
        }
    }

    private fun parseEnhancedWords(body: String, offsetMs: Long): List<LyricsWord> {
        val tags = WORD_REGEX.findAll(body).toList()
        if (tags.isEmpty()) return emptyList()

        val words = mutableListOf<LyricsWord>()
        tags.forEachIndexed { index, tag ->
            val start = timestampMs(
                tag.groupValues[1],
                tag.groupValues[2],
                tag.groupValues[3],
                offsetMs
            )
            val contentStart = tag.range.last + 1
            val contentEnd = tags.getOrNull(index + 1)?.range?.first ?: body.length
            val text = body.substring(contentStart, contentEnd)
            if (text.isNotEmpty()) words += LyricsWord(start, start + 1L, text)
        }
        return words
    }

    private fun timestampMs(
        minText: String,
        secText: String,
        fracText: String,
        offsetMs: Long
    ): Long {
        val min = minText.toLongOrNull() ?: 0L
        val sec = secText.toLongOrNull() ?: 0L
        if (sec !in 0..59) return 0L
        val fracMs = when (fracText.length) {
            0 -> 0L
            1 -> fracText.toLong() * 100L
            2 -> fracText.toLong() * 10L
            else -> fracText.take(3).toLong()
        }
        return ((min * 60L + sec) * 1000L + fracMs + offsetMs).coerceAtLeast(0L)
    }

    fun findLrcPath(audioDataPath: String?): String? {
        if (audioDataPath.isNullOrBlank()) return null
        val dot = audioDataPath.lastIndexOf('.')
        val base = if (dot > 0) audioDataPath.substring(0, dot) else audioDataPath
        return "$base.lrc"
    }
}

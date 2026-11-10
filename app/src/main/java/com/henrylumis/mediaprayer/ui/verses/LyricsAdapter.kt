package com.henrylumis.mediaprayer.ui.verses

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.data.LyricsLine
import com.henrylumis.mediaprayer.data.LyricsTimeline
import com.henrylumis.mediaprayer.databinding.ItemLyricLineBinding

class LyricsAdapter(
    private val onLineClick: ((LyricsLine) -> Unit)? = null
) : RecyclerView.Adapter<LyricsAdapter.LineViewHolder>() {

    private val lines = mutableListOf<LyricsLine>()
    private var activeIndex = -1
    private var activeWordIndex = -1
    private var synced = true
    private var untimedDurationMs = 0L

    fun setUntimedDuration(durationMs: Long) {
        untimedDurationMs = durationMs.coerceAtLeast(0L)
    }

    fun submitLines(newLines: List<LyricsLine>, isSynced: Boolean = true) {
        lines.clear()
        lines.addAll(newLines)
        synced = isSynced
        activeIndex = -1
        activeWordIndex = -1
        notifyDataSetChanged()
    }

    /** Returns the new scroll index if it changed. Timed lyrics use their timestamps;
     * untimed lyrics are distributed evenly across the song duration so they can still
     * follow playback automatically without pretending the timestamps are exact. */
    fun isSynced(): Boolean = synced

    fun untimedDurationMsForController(): Long = untimedDurationMs

    fun currentActiveIndex(): Int = activeIndex

    fun lineAt(index: Int): LyricsLine? = lines.getOrNull(index)

    fun activeWordIndex(positionMs: Long): Int =
        LyricsTimeline.activeWordIndex(lines.getOrNull(activeIndex), positionMs)

    fun updateActiveIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val idx = LyricsTimeline.activeIndex(lines, positionMs, untimedDurationMs, synced)
        val word = if (idx in lines.indices) LyricsTimeline.activeWordIndex(lines[idx], positionMs) else -1
        if (idx != activeIndex) {
            val old = activeIndex
            activeIndex = idx
            activeWordIndex = word
            if (old in lines.indices) notifyItemChanged(old)
            if (idx in lines.indices) notifyItemChanged(idx)
            return idx
        }
        if (word != activeWordIndex) {
            activeWordIndex = word
            if (idx in lines.indices && lines[idx].words.isNotEmpty()) notifyItemChanged(idx)
        }
        return -1
    }

    fun hasWordTiming(): Boolean = lines.any { it.words.isNotEmpty() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder {
        val binding = ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LineViewHolder, position: Int) {
        val line = lines[position]
        val context = holder.binding.root.context
        holder.binding.lyricText.setOnClickListener { onLineClick?.invoke(line) }
        holder.binding.lyricText.isClickable = onLineClick != null && synced
        holder.binding.lyricText.contentDescription = if (synced) {
            if (line.timeMs >= 0L) "Seek to lyric: ${line.text}" else line.text
        } else line.text
        if (!synced) {
            // Plain lyrics have no trustworthy cue position, so don't fake an
            // active line. They remain a calm, readable reading surface.
            holder.binding.lyricText.text = line.text
            holder.binding.lyricText.setTextColor(ContextCompat.getColor(context, R.color.text_on_photo_primary))
            holder.binding.lyricText.textSize = 18f
            holder.binding.lyricText.alpha = 0.92f
            holder.binding.lyricText.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.binding.lyricText.isSelected = false
            return
        }

        val isActive = position == activeIndex
        holder.binding.lyricText.isSelected = isActive
        holder.binding.lyricText.textSize = if (isActive) 24f else 18f
        holder.binding.lyricText.alpha = when {
            isActive -> 1f
            position in (activeIndex - 2)..(activeIndex + 2) -> 0.72f
            else -> 0.38f
        }
        holder.binding.lyricText.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        if (isActive && line.words.isNotEmpty()) {
            val spannable = SpannableString(line.text)
            val baseColor = ContextCompat.getColor(context, R.color.text_on_photo_primary)
            val sungColor = ContextCompat.getColor(context, R.color.accent_cyan)
            spannable.setSpan(ForegroundColorSpan(baseColor), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            var searchStart = 0
            line.words.forEachIndexed { index, word ->
                val token = word.text.trim()
                if (token.isBlank()) return@forEachIndexed
                val found = line.text.indexOf(token, searchStart)
                if (found < 0) return@forEachIndexed
                val end = (found + token.length).coerceAtMost(line.text.length)
                if (index <= activeWordIndex) {
                    spannable.setSpan(ForegroundColorSpan(sungColor), found, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                searchStart = end
            }
            holder.binding.lyricText.text = spannable
        } else {
            holder.binding.lyricText.text = line.text
            holder.binding.lyricText.setTextColor(
                ContextCompat.getColor(context, if (isActive) R.color.text_on_photo_primary else R.color.text_on_photo_secondary)
            )
        }
    }

    override fun getItemCount() = lines.size

    class LineViewHolder(val binding: ItemLyricLineBinding) : RecyclerView.ViewHolder(binding.root)
}

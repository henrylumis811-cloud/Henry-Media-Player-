package com.henrylumis.mediaprayer.ui.verses

import android.view.Choreographer
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.MainActivity
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.util.LyricsLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Transparent, single-line live lyric surface for Now Playing.
 * The playback position is the only timing authority; this class only renders it.
 */
class LiveLyricsController(
    private val fragment: Fragment,
    private val list: RecyclerView,
    private val modeLabel: TextView,
    private val emptyLabel: TextView,
    private val expandTarget: View? = null,
    private val previewText: TextView? = null
) {
    private val adapter = LyricsAdapter { line ->
        if (line.timeMs > 0L) (fragment.activity as? MainActivity)?.seekTo(line.timeMs)
    }
    private var frameCallback: Choreographer.FrameCallback? = null
    private var loadJob: Job? = null
    private var loadedMediaId: String? = null
    private var lastPreviewIndex = Int.MIN_VALUE
    private var lastPreviewWordIndex = Int.MIN_VALUE
    private var lastRenderedIndex = Int.MIN_VALUE
    private var untimedPositionInitialized = false
    private var untimedScrollResidualPx = 0.0
    private var cachedUntimedMaxOffset = 0
    private var lastUntimedRangeSampleAt = 0L
    private var syncedPositionInitialized = false

    init {
        list.layoutManager = LinearLayoutManager(fragment.requireContext()).apply { isItemPrefetchEnabled = true }
        list.adapter = adapter
        list.itemAnimator = null
        list.isNestedScrollingEnabled = false
        adapter.setUntimedDuration(0L)
        val openLyrics = { (fragment.activity as? MainActivity)?.openVerses() }
        expandTarget?.setOnClickListener { openLyrics() }
        modeLabel.setOnClickListener { openLyrics() }
        modeLabel.contentDescription = "Open full lyrics"
    }

    fun start() {
        if (frameCallback != null) return
        val choreographer = Choreographer.getInstance()
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCallback = null
                if (!fragment.isAdded || fragment.view == null) return
                refresh()
                val activity = fragment.activity as? MainActivity
                val pending = activity?.pendingTrack
                val player = activity?.player
                val position = if (pending != null) 0L else player?.currentPosition ?: 0L
                val index = adapter.updateActiveIndex(position)
                if (previewText != null) {
                    val current = adapter.currentActiveIndex()
                    if (current != lastPreviewIndex) {
                        lastPreviewIndex = current
                        lastPreviewWordIndex = Int.MIN_VALUE
                        updatePreview(current, position)
                    } else if (current >= 0) {
                        val word = adapter.activeWordIndex(position)
                        if (word != lastPreviewWordIndex) {
                            lastPreviewWordIndex = word
                            updatePreview(current, position)
                        }
                    }
                } else if (adapter.isSynced()) {
                    if (index >= 0) scrollToActive(index)
                } else {
                    scrollUntimedSmoothly(position)
                }
                if (fragment.isAdded && fragment.view != null) {
                    frameCallback = this
                    choreographer.postFrameCallback(this)
                }
            }
        }
        choreographer.postFrameCallback(frameCallback!!)
    }

    fun refresh(force: Boolean = false) {
        val activity = fragment.activity as? MainActivity ?: return
        val item = activity.pendingTrack ?: activity.player?.currentMediaItem
        val mediaId = item?.mediaId
        if (!force && mediaId == loadedMediaId) return
        loadedMediaId = mediaId
        loadJob?.cancel()
        lastPreviewIndex = Int.MIN_VALUE
        lastPreviewWordIndex = Int.MIN_VALUE
        lastRenderedIndex = Int.MIN_VALUE
        untimedPositionInitialized = false
        untimedScrollResidualPx = 0.0
        cachedUntimedMaxOffset = 0
        lastUntimedRangeSampleAt = 0L
        syncedPositionInitialized = false
        if (item == null || mediaId.isNullOrBlank()) {
            adapter.submitLines(emptyList())
            showEmpty()
            return
        }
        loadJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(fragment.requireContext(), item)
            if (!fragment.isAdded || fragment.view == null) return@launch
            adapter.setUntimedDuration(result.durationMs)
            adapter.submitLines(result.lines, result.synced)
            if (result.lines.isEmpty()) showEmpty() else {
                emptyLabel.visibility = View.GONE
                list.visibility = View.VISIBLE
                modeLabel.text = if (result.synced) "SYNCED" else "UNSYNCED"
                modeLabel.setTextColor(fragment.resources.getColor(R.color.accent_cyan, null))
                previewText?.text = ""
                previewText?.contentDescription = "Current lyric"
            }
        }
    }

    /** There is deliberately no fallback text inside the live lyric surface. */
    private fun showEmpty() {
        list.visibility = View.GONE
        previewText?.animate()?.cancel()
        previewText?.text = ""
        previewText?.alpha = 0f
        emptyLabel.visibility = View.GONE
        modeLabel.text = "LYRICS"
        modeLabel.setTextColor(fragment.resources.getColor(R.color.text_secondary, null))
    }

    private fun updatePreview(index: Int, positionMs: Long) {
        val target = previewText ?: return
        val line = adapter.lineAt(index)
        val text = line?.text?.replace(Regex("\\s*\\n\\s*"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        target.animate().cancel()
        if (text.isBlank()) {
            target.text = ""
            target.alpha = 0f
            return
        }

        val wordIndex = adapter.activeWordIndex(positionMs)
        if (line != null && line.words.isNotEmpty()) {
            val spannable = SpannableString(text)
            var searchStart = 0
            line.words.forEachIndexed { i, word ->
                val token = word.text.trim()
                if (token.isBlank()) return@forEachIndexed
                val found = text.indexOf(token, searchStart)
                if (found < 0) return@forEachIndexed
                val endWord = (found + token.length).coerceAtMost(text.length)
                val color = if (i <= wordIndex) R.color.accent_cyan else R.color.text_on_photo_secondary
                spannable.setSpan(ForegroundColorSpan(fragment.resources.getColor(color, null)), found, endWord, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchStart = endWord
            }
            target.text = spannable
        } else {
            target.text = text
        }

        target.contentDescription = text
        target.translationY = 0f
        if (target.alpha < 0.99f) {
            target.alpha = 0f
            target.animate().alpha(1f).setDuration(180L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else {
            target.alpha = 1f
        }
    }

    private fun scrollToActive(index: Int) {
        val lm = list.layoutManager as? LinearLayoutManager ?: return
        if (index !in 0 until adapter.itemCount || index == lastRenderedIndex) return
        lastRenderedIndex = index
        val targetOffset = (list.height / 2).coerceAtLeast(0)
        if (!syncedPositionInitialized) {
            lm.scrollToPositionWithOffset(index, targetOffset)
            syncedPositionInitialized = true
            return
        }
        val child = lm.findViewByPosition(index)
        if (child != null) {
            val viewportCenter = list.height / 2
            val childCenter = (child.top + child.bottom) / 2
            val delta = childCenter - viewportCenter
            if (abs(delta) > list.height / 5) list.scrollBy(0, (delta * 0.22f).toInt().coerceIn(-list.height / 2, list.height / 2))
        } else lm.scrollToPositionWithOffset(index, targetOffset)
    }

    private fun scrollUntimedSmoothly(positionMs: Long) {
        if (adapter.itemCount == 0 || list.height <= 0) return
        val duration = adapter.untimedDurationMsForController()
        val effectiveDuration = if (duration > 0L) duration else (adapter.itemCount * 4500L).coerceAtLeast(1L)
        val progress = (positionMs.toDouble() / effectiveDuration.toDouble()).coerceIn(0.0, 1.0)
        val now = android.os.SystemClock.uptimeMillis()
        if (cachedUntimedMaxOffset <= 0 || now - lastUntimedRangeSampleAt >= 500L) {
            cachedUntimedMaxOffset = (list.computeVerticalScrollRange() - list.computeVerticalScrollExtent()).coerceAtLeast(0)
            lastUntimedRangeSampleAt = now
        }
        val maxOffset = cachedUntimedMaxOffset
        if (maxOffset <= 0) return
        val target = progress * maxOffset.toDouble()
        val current = list.computeVerticalScrollOffset().toDouble()
        val desiredDelta = target - current + untimedScrollResidualPx
        val integerDelta = desiredDelta.toInt()
        untimedScrollResidualPx = desiredDelta - integerDelta
        if (integerDelta != 0) list.scrollBy(0, integerDelta)
        untimedPositionInitialized = true
    }

    fun stop() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        loadJob?.cancel()
        loadJob = null
    }
}

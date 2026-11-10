package com.henrylumis.mediaprayer.ui.common

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Rebinds only visible rows when an individual list settles, recovering requests
 * rejected by the artwork loader's bounded worker queue without blocking flings. */
object ArtworkScrollGate {
    fun attach(recyclerView: RecyclerView): RecyclerView.OnScrollListener {
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    rv.post { rebindVisibleRowsIfIdle(rv) }
                }
            }
        }
        recyclerView.addOnScrollListener(listener)
        return listener
    }

    fun detach(recyclerView: RecyclerView, listener: RecyclerView.OnScrollListener?) {
        if (listener != null) recyclerView.removeOnScrollListener(listener)
    }

    private fun rebindVisibleRowsIfIdle(recyclerView: RecyclerView) {
        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val adapter = recyclerView.adapter ?: return
        val manager = recyclerView.layoutManager
        val first: Int
        val last: Int
        if (manager is LinearLayoutManager) {
            first = manager.findFirstVisibleItemPosition()
            last = manager.findLastVisibleItemPosition()
        } else {
            val positions = (0 until recyclerView.childCount)
                .map { recyclerView.getChildAdapterPosition(recyclerView.getChildAt(it)) }
                .filter { it != RecyclerView.NO_POSITION }
            first = positions.minOrNull() ?: return
            last = positions.maxOrNull() ?: return
        }
        if (first != RecyclerView.NO_POSITION && last >= first) {
            adapter.notifyItemRangeChanged(first, last - first + 1, VISIBLE_ARTWORK_RESUME)
        }
    }

    private const val VISIBLE_ARTWORK_RESUME = "rvh-visible-artwork-resume"
}

package com.henrylumis.mediaprayer.ui.common

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/** Small, safe defaults for long music lists: reuse rows and avoid change-fade flashes. */
object RecyclerViewPerformance {
    fun optimize(list: RecyclerView, cachedRows: Int = 6) {
        list.setHasFixedSize(true)
        list.setItemViewCacheSize(cachedRows)
        list.recycledViewPool.setMaxRecycledViews(0, 24)
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }
}

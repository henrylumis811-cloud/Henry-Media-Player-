package com.henrylumis.mediaprayer.ui.queue

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.ui.common.TrackInfoDialog
import com.henrylumis.mediaprayer.databinding.ItemQueueSongBinding

class QueueAdapter(
    private val onClick: (String) -> Unit,
    private val onRemove: (Int) -> Unit,
    private val onMove: (Int, Int) -> Unit,
    private val onDragHandleTouch: (android.view.View, android.view.MotionEvent) -> Boolean
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val items = mutableListOf<MediaItem>()
    private var currentIndex = -1

    fun submitList(newItems: List<MediaItem>, playingIndex: Int) {
        val oldItems = items.toList()
        val oldIndex = currentIndex
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                oldItems[oldPos].mediaId == newItems[newPos].mediaId
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = oldItems[oldPos]
                val new = newItems[newPos]
                return old.mediaId == new.mediaId &&
                    old.mediaMetadata.title == new.mediaMetadata.title &&
                    old.mediaMetadata.artist == new.mediaMetadata.artist &&
                    old.mediaMetadata.albumTitle == new.mediaMetadata.albumTitle &&
                    old.mediaMetadata.artworkUri == new.mediaMetadata.artworkUri
            }
        })
        items.clear()
        items.addAll(newItems)
        currentIndex = playingIndex.coerceIn(-1, items.lastIndex)
        diffResult.dispatchUpdatesTo(this)
        if (oldIndex != currentIndex) {
            if (oldIndex in items.indices) notifyItemChanged(oldIndex)
            if (currentIndex in items.indices) notifyItemChanged(currentIndex)
        }
    }

    fun moveLocally(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        currentIndex = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        notifyItemMoved(from, to)
    }


    fun removeLocally(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        currentIndex = when {
            items.isEmpty() -> -1
            currentIndex == position -> currentIndex.coerceAtMost(items.lastIndex)
            currentIndex > position -> currentIndex - 1
            else -> currentIndex
        }
        notifyItemRemoved(position)
        if (currentIndex in items.indices) notifyItemChanged(currentIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        val isCurrent = position == currentIndex
        holder.binding.queueSongTitle.text = item.mediaMetadata.title?.toString() ?: "Unknown"
        holder.binding.queueSongArtist.text = item.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        holder.binding.queueAlbumArt.setImageDrawable(null)
        item.mediaMetadata.artworkUri?.let { holder.binding.queueAlbumArt.setImageURI(it) }
        if (holder.binding.queueAlbumArt.drawable == null) {
            holder.binding.queueAlbumArt.setImageResource(R.mipmap.ic_launcher)
        }
        holder.binding.root.setBackgroundResource(if (isCurrent) R.drawable.bg_queue_current else R.drawable.bg_queue_row)
        holder.binding.queueSongTitle.setTextColor(
            ContextCompat.getColor(context, if (isCurrent) R.color.accent_cyan else R.color.text_primary)
        )
        holder.binding.queueNowPlaying.visibility = if (isCurrent) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.queueAlbumArt.contentDescription =
            "Album artwork for ${item.mediaMetadata.title?.toString() ?: "song"}"

        holder.binding.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                // Pass the stable media identity, not the transient row position.
                // Queue refreshes can reorder/highlight rows while the user taps.
                onClick(items[p].mediaId)
            }
        }
        holder.binding.queueDragHandle.setOnTouchListener { v, event -> onDragHandleTouch(v, event) }
        holder.binding.btnInfo.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p == RecyclerView.NO_POSITION) return@setOnClickListener
            val current = items[p]
            val duration = current.mediaMetadata.extras?.getLong("duration_ms", 0L) ?: 0L
            val path = current.mediaMetadata.extras?.getString("data_path")
            val id = current.mediaId.toLongOrNull() ?: return@setOnClickListener
            val uri = current.localConfiguration?.uri?.toString() ?: return@setOnClickListener
            TrackInfoDialog.show(context, Song(
                id = id,
                title = current.mediaMetadata.title?.toString() ?: "Unknown",
                artist = current.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                album = current.mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
                durationMs = duration,
                uriString = uri,
                albumId = -1L,
                dataPath = path
            ))
        }

        ViewCompat.setAccessibilityDelegate(holder.binding.root, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) return
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_CLICK, "Play song"
                ))
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    0x01010001, "Remove from queue"
                ))
                if (p > 0) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(0x01010002, "Move up"))
                if (p < itemCount - 1) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(0x01010003, "Move down"))
            }
            override fun performAccessibilityAction(host: android.view.View, action: Int, args: android.os.Bundle?): Boolean {
                val p = holder.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) return false
                return when (action) {
                    0x01010001 -> { onRemove(p); true }
                    0x01010002 -> { onMove(p, p - 1); true }
                    0x01010003 -> { onMove(p, p + 1); true }
                    else -> super.performAccessibilityAction(host, action, args)
                }
            }
        })
    }

    override fun getItemCount() = items.size

    class QueueViewHolder(val binding: ItemQueueSongBinding) : RecyclerView.ViewHolder(binding.root)
}

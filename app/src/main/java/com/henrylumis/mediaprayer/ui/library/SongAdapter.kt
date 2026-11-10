package com.henrylumis.mediaprayer.ui.library

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.ui.common.ArtworkLoader
import com.henrylumis.mediaprayer.databinding.ItemSongBinding
import java.util.concurrent.TimeUnit

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Song>() {
    override fun areItemsTheSame(old: Song, new: Song) = old.id == new.id
    override fun areContentsTheSame(old: Song, new: Song) = old == new
}

class SongAdapter(
    private val onClick: (Song, Int) -> Unit,
    private val onLongClick: (Song) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF_CALLBACK) {

    private var playingSongId: String? = null
    private var isPlaying = false
    private var selectionMode = false
    private val selectedIds = LinkedHashSet<Long>()

    init { setHasStableIds(true) }

    fun currentList(): List<Song> = currentList
    fun selectedSongs(): List<Song> = currentList.filter { selectedIds.contains(it.id) }
    fun isSelectionMode() = selectionMode

    fun startSelection(song: Song) {
        val enteringSelectionMode = !selectionMode
        selectionMode = true
        if (!selectedIds.add(song.id)) selectedIds.remove(song.id)
        if (selectedIds.isEmpty()) selectionMode = false
        if (enteringSelectionMode || !selectionMode) notifyItemRangeChanged(0, itemCount)
        else currentList.indexOfFirst { it.id == song.id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        onSelectionChanged(selectedIds.size)
    }

    fun toggleSelection(song: Song) {
        val wasInSelectionMode = selectionMode
        if (!selectionMode) selectionMode = true
        if (!selectedIds.add(song.id)) selectedIds.remove(song.id)
        if (selectedIds.isEmpty()) selectionMode = false
        val position = currentList.indexOfFirst { it.id == song.id }
        if (wasInSelectionMode && selectionMode) {
            if (position != -1) notifyItemChanged(position)
        } else {
            // Checkbox visibility changed for every row when entering/leaving selection mode.
            notifyItemRangeChanged(0, itemCount)
        }
        onSelectionChanged(selectedIds.size)
    }

    fun clearSelection() {
        if (selectedIds.isEmpty() && !selectionMode) return
        selectedIds.clear(); selectionMode = false
        notifyItemRangeChanged(0, itemCount); onSelectionChanged(0)
    }

    fun updateNowPlaying(mediaId: String?, playing: Boolean) {
        if (mediaId == playingSongId && playing == isPlaying) return
        val previousId = playingSongId
        playingSongId = mediaId; isPlaying = playing
        currentList.forEachIndexed { index, song ->
            val idStr = song.id.toString()
            if (idStr == mediaId || idStr == previousId) notifyItemChanged(index)
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).id
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder =
        SongViewHolder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, song.id.toString() == playingSongId, isPlaying, selectionMode, selectedIds.contains(song.id))
        loadArtwork(holder, song)
        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(song) else onClick(song, holder.bindingAdapterPosition)
        }
        holder.itemView.setOnLongClickListener {
            if (selectionMode) toggleSelection(song) else onLongClick(song)
            true
        }
        holder.binding.selectSong.setOnClickListener { toggleSelection(song) }
    }

    private fun loadArtwork(holder: SongViewHolder, song: Song) {
        val cached = ArtworkLoader.peek(song)
        if (holder.artworkSongId != song.id) {
            holder.artworkSongId = song.id
            if (cached != null) holder.setArtwork(cached) else holder.showArtworkPlaceholder()
        } else if (cached != null) {
            holder.setArtwork(cached)
        }
        ArtworkLoader.load(holder.itemView.context, song) { bitmap ->
            if (bitmap != null && holder.artworkSongId == song.id) holder.setArtwork(bitmap)
        }
    }

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var artworkSongId: Long = -1L
        fun setArtwork(bitmap: Bitmap) { binding.albumArt.setImageBitmap(bitmap) }
        fun showArtworkPlaceholder() { binding.albumArt.setImageResource(com.henrylumis.mediaprayer.R.mipmap.ic_launcher) }
        fun bind(song: Song, isCurrent: Boolean, isPlaying: Boolean, selecting: Boolean, selected: Boolean) {
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist
            binding.selectSong.visibility = if (selecting) View.VISIBLE else View.GONE
            binding.selectSong.setOnCheckedChangeListener(null)
            binding.selectSong.isChecked = selected
            if (isCurrent && !selecting) {
                binding.miniEq.visibility = View.VISIBLE; binding.miniEq.setPlaying(isPlaying)
                binding.songDuration.visibility = View.GONE
            } else {
                binding.miniEq.visibility = View.GONE; binding.miniEq.setPlaying(false)
                binding.songDuration.visibility = if (selecting) View.GONE else View.VISIBLE
                val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(song.durationMs)
                binding.songDuration.text = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
            }
        }
    }
}

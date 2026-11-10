package com.henrylumis.mediaprayer.ui.playlists

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.ui.common.ArtworkLoader
import com.henrylumis.mediaprayer.databinding.ItemPlaylistSongBinding

class PlaylistSongAdapter(
    songs: List<Song>,
    private val onPlay: (Int) -> Unit,
    private val onDragStart: (Holder) -> Unit
) : RecyclerView.Adapter<PlaylistSongAdapter.Holder>() {
    private val items = songs.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPlaylistSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = items[position]
        val cachedArtwork = ArtworkLoader.peek(song)
        if (holder.artworkSongId != song.id) {
            holder.artworkSongId = song.id
            if (cachedArtwork != null) holder.binding.albumArt.setImageBitmap(cachedArtwork)
            else holder.binding.albumArt.setImageResource(com.henrylumis.mediaprayer.R.mipmap.ic_launcher)
        } else if (cachedArtwork != null) {
            holder.binding.albumArt.setImageBitmap(cachedArtwork)
        }
        holder.binding.title.text = song.title
        holder.binding.artist.text = song.artist
        holder.binding.root.setOnClickListener { onPlay(holder.bindingAdapterPosition) }
        holder.binding.dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) onDragStart(holder)
            false
        }
        ArtworkLoader.load(holder.itemView.context, song) { bitmap ->
            if (bitmap != null && holder.artworkSongId == song.id) holder.binding.albumArt.setImageBitmap(bitmap)
        }
    }

    override fun getItemCount() = items.size

    fun moveLocally(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun songIds(): List<String> = items.map { it.id.toString() }

    class Holder(val binding: ItemPlaylistSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var artworkSongId: Long = -1L
    }
}

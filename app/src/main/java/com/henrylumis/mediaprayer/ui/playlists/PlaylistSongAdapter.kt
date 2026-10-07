package com.henrylumis.mediaprayer.ui.playlists

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.databinding.ItemPlaylistSongBinding
import java.util.concurrent.Executors

class PlaylistSongAdapter(
    songs: List<Song>,
    private val onPlay: (Int) -> Unit,
    private val onDragStart: (Holder) -> Unit
) : RecyclerView.Adapter<PlaylistSongAdapter.Holder>() {
    private val items = songs.toMutableList()
    private val artworkExecutor = Executors.newFixedThreadPool(2)
    private val artworkCache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {}
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPlaylistSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = items[position]
        holder.artworkSongId = song.id
        holder.binding.title.text = song.title
        holder.binding.artist.text = song.artist
        holder.binding.albumArt.setImageResource(com.henrylumis.mediaprayer.R.mipmap.ic_launcher)
        holder.binding.root.setOnClickListener { onPlay(holder.bindingAdapterPosition) }
        holder.binding.dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) onDragStart(holder)
            false
        }
        val key = if (song.albumId >= 0L) song.albumId else song.id
        artworkCache.get(key)?.let { holder.binding.albumArt.setImageBitmap(it); return }
        artworkExecutor.execute {
            val bitmap = try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(holder.itemView.context, Uri.parse(song.uriString))
                    retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                } finally { retriever.release() }
            } catch (_: Exception) { null }
            bitmap?.let { artworkCache.put(key, it) }
            if (bitmap != null) mainHandler.post {
                if (holder.artworkSongId == song.id) holder.binding.albumArt.setImageBitmap(bitmap)
            }
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

    fun close() {
        artworkExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        artworkCache.evictAll()
    }

    class Holder(val binding: ItemPlaylistSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var artworkSongId: Long = -1L
    }
}

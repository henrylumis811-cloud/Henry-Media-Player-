package com.henrylumis.mediaprayer.ui.playlists

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.databinding.ItemPlaylistPickerSongBinding
import java.util.concurrent.Executors

class PlaylistPickerAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Song, PlaylistPickerAdapter.Holder>(DIFF) {
    private var allItems: List<Song> = emptyList()
    private val existingIds = mutableSetOf<String>()
    private val selectedIds = linkedSetOf<String>()
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val artworkCache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {}

    fun setItems(songs: List<Song>, existing: Set<String>) {
        allItems = songs.toList()
        existingIds.clear(); existingIds.addAll(existing)
        val valid = allItems.asSequence().map { it.id.toString() }.toSet() - existingIds
        selectedIds.retainAll(valid)
        submitList(allItems)
        onSelectionChanged(selectedIds.size)
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        submitList(if (q.isBlank()) allItems else allItems.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        })
    }

    fun selectedIds(): Set<String> = selectedIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPlaylistPickerSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = getItem(position)
        val id = song.id.toString()
        holder.songId = song.id
        holder.binding.pickerTitle.text = song.title
        holder.binding.pickerMeta.text = "${song.artist} • ${song.album}"
        val existing = id in existingIds
        holder.binding.pickerExisting.visibility = if (existing) View.VISIBLE else View.GONE
        holder.binding.pickerCheck.isEnabled = !existing
        holder.binding.pickerCheck.isChecked = !existing && id in selectedIds
        holder.binding.root.alpha = if (existing) 0.58f else 1f
        holder.binding.root.setOnClickListener {
            if (existing) return@setOnClickListener
            if (!selectedIds.add(id)) selectedIds.remove(id)
            holder.binding.pickerCheck.isChecked = id in selectedIds
            onSelectionChanged(selectedIds.size)
        }
        holder.binding.pickerArtwork.setImageResource(R.mipmap.ic_launcher)
        val key = song.albumId.takeIf { it >= 0L } ?: song.id
        artworkCache.get(key)?.let { holder.binding.pickerArtwork.setImageBitmap(it); return }
        executor.execute {
            val bitmap = try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(holder.itemView.context, Uri.parse(song.uriString))
                    retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                } finally { retriever.release() }
            } catch (_: Exception) { null }
            if (bitmap != null) artworkCache.put(key, bitmap)
            if (bitmap != null) main.post {
                if (holder.songId == song.id) holder.binding.pickerArtwork.setImageBitmap(bitmap)
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.binding.pickerArtwork.setImageResource(R.mipmap.ic_launcher)
        holder.songId = -1L
        super.onViewRecycled(holder)
    }

    fun close() {
        executor.shutdownNow()
        main.removeCallbacksAndMessages(null)
        artworkCache.evictAll()
    }

    class Holder(val binding: ItemPlaylistPickerSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var songId: Long = -1L
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}

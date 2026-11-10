package com.henrylumis.mediaprayer.ui.library

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.ui.common.ArtworkLoader
import com.henrylumis.mediaprayer.databinding.ItemLibraryGroupBinding

/** A compact Albums/Artists view. Clicking a group starts that group's songs. */
data class LibraryGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val artSong: Song,
    /** Ordered alternatives let a group find art even when its first track has none. */
    val artCandidates: List<Song> = listOf(artSong)
)

private val GROUP_DIFF = object : DiffUtil.ItemCallback<LibraryGroup>() {
    override fun areItemsTheSame(old: LibraryGroup, new: LibraryGroup) = old.key == new.key
    override fun areContentsTheSame(old: LibraryGroup, new: LibraryGroup) =
        old.title == new.title && old.subtitle == new.subtitle && old.songs.map { it.id } == new.songs.map { it.id }
}

class LibraryGroupAdapter(
    private val onClick: (LibraryGroup) -> Unit
) : ListAdapter<LibraryGroup, LibraryGroupAdapter.GroupViewHolder>(GROUP_DIFF) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder =
        GroupViewHolder(ItemLibraryGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = getItem(position)
        val identityChanged = holder.bind(group)
        val cached = ArtworkLoader.peek(group.artSong)
        if (cached != null) holder.setArt(cached)
        else if (identityChanged) holder.showPlaceholder()
        loadGroupArtwork(holder, group, 0)
        holder.itemView.setOnClickListener { onClick(group) }
    }

    private fun loadGroupArtwork(holder: GroupViewHolder, group: LibraryGroup, candidateIndex: Int) {
        if (candidateIndex >= group.artCandidates.size) return
        val candidate = group.artCandidates[candidateIndex]
        ArtworkLoader.load(holder.itemView.context, candidate) { bitmap ->
            if (holder.groupKey != group.key || holder.artSongId != group.artSong.id) return@load
            if (bitmap != null) holder.setArt(bitmap)
            else loadGroupArtwork(holder, group, candidateIndex + 1)
        }
    }

    class GroupViewHolder(private val binding: ItemLibraryGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        var groupKey: String = ""
        var artSongId: Long = -1L
        fun bind(group: LibraryGroup): Boolean {
            val changed = groupKey != group.key || artSongId != group.artSong.id
            groupKey = group.key
            artSongId = group.artSong.id
            binding.groupTitle.text = group.title
            binding.groupSubtitle.text = group.subtitle
            binding.groupCount.text = when (group.songs.size) {
                1 -> "1 song"
                else -> "${group.songs.size} songs"
            }
            return changed
        }
        fun setArt(bitmap: Bitmap) { binding.groupArt.setImageBitmap(bitmap) }
        fun showPlaceholder() { binding.groupArt.setImageResource(com.henrylumis.mediaprayer.R.mipmap.ic_launcher) }
    }
}

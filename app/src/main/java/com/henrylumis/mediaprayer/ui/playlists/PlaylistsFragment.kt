package com.henrylumis.mediaprayer.ui.playlists

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.henrylumis.mediaprayer.MainActivity
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.data.MusicScanner
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.databinding.FragmentPlaylistsBinding
import com.henrylumis.mediaprayer.databinding.ItemPlaylistBinding
import com.henrylumis.mediaprayer.util.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.henrylumis.mediaprayer.ui.common.DialogStyler
class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private var openPlaylist: String? = null
    private var songsById: Map<String, Song> = emptyMap()
    private var playlistSongAdapter: PlaylistSongAdapter? = null
    private var draggedPlaylistName: String? = null
    private var librarySongs: List<Song> = emptyList()
    private var playlistCatalogAdapter: PlaylistAdapter? = null
    private val playlistTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val adapter = playlistSongAdapter ?: return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            adapter.moveLocally(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (direction != ItemTouchHelper.LEFT) return
            val position = viewHolder.bindingAdapterPosition
            val name = openPlaylist ?: return
            val adapter = playlistSongAdapter ?: return
            if (position !in adapter.songIds().indices) return
            val id = adapter.songIds()[position]
            PlaylistStore.removeSong(requireContext(), name, id)
            renderPlaylistSongs(name)
            Toast.makeText(requireContext(), "Removed from playlist", Toast.LENGTH_SHORT).show()
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                draggedPlaylistName = openPlaylist
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val name = draggedPlaylistName ?: return
            val adapter = playlistSongAdapter ?: return
            PlaylistStore.setPlayableSongOrder(requireContext(), name, adapter.songIds())
            draggedPlaylistName = null
        }
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCreate.setOnClickListener { showCreateDialog() }
        binding.playlistSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (openPlaylist == null && _binding != null) renderPlaylists()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        playlistTouchHelper.attachToRecyclerView(binding.list)
        // Render the persisted playlist catalog immediately. Do not make the
        // playlist screen wait for a full MediaStore scan just to show names.
        renderPlaylists()
        loadLibraryAndRefresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadLibraryAndRefresh()
    }

    private fun loadLibraryAndRefresh() {
        // The catalog is local and should be visible even while MediaStore is
        // being scanned. Only the contents/counts of an opened playlist depend
        // on the library scan.
        if (openPlaylist == null) renderPlaylists()
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) { MusicScanner.scan(requireContext()) }
            if (!isAdded || _binding == null) return@launch
            librarySongs = songs
            songsById = songs.associateBy { it.id.toString() }
            render()
        }
    }

    private fun render() {
        val playlist = openPlaylist
        if (playlist == null) renderPlaylists() else renderPlaylistSongs(playlist)
    }

    private fun renderPlaylists() {
        binding.btnCreate.visibility = View.VISIBLE
        binding.playlistSearch.visibility = View.VISIBLE
        (activity as? MainActivity)?.setShellTitle("PLAYLISTS")
        binding.playlistDetailHeader.visibility = View.GONE
        binding.playlistControls.visibility = View.GONE
        binding.unavailableHint.visibility = View.GONE
        val names = PlaylistStore.getPlaylistNames(requireContext())
        val query = binding.playlistSearch.text?.toString()?.trim().orEmpty()
        val filtered = if (query.isBlank()) names else names.filter { it.contains(query, ignoreCase = true) }
        binding.emptyState.text = when {
            names.isEmpty() -> "No playlists yet.\nCreate one to organize your music."
            filtered.isEmpty() -> "No playlists match your search."
            else -> ""
        }
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        if (playlistCatalogAdapter == null) {
            playlistCatalogAdapter = PlaylistAdapter(names, librarySongs) { openPlaylist(it) }
            binding.list.adapter = playlistCatalogAdapter
        } else {
            // Keep one adapter alive so typing in search does not restart artwork loading.
            playlistCatalogAdapter?.setData(names, librarySongs)
        }
        playlistCatalogAdapter?.filter(query)
    }

    private fun renderPlaylistSongs(name: String) {
        binding.btnCreate.visibility = View.GONE
        binding.playlistSearch.visibility = View.GONE
        (activity as? MainActivity)?.setShellTitle(name)
        binding.playlistDetailHeader.visibility = View.VISIBLE
        binding.playlistControls.visibility = View.VISIBLE
        val ids = PlaylistStore.getSongIds(requireContext(), name)
        val songs = ids.mapNotNull { songsById[it] }
        val unavailable = (ids.size - songs.size).coerceAtLeast(0)
        binding.playlistDetailName.text = name
        binding.playlistDetailStats.text = if (unavailable > 0) "${ids.size} songs • $unavailable unavailable" else "${ids.size} ${if (ids.size == 1) "song" else "songs"}"
        binding.unavailableHint.text = if (unavailable > 0) "$unavailable song${if (unavailable == 1) " is" else "s are"} unavailable on this device." else ""
        binding.unavailableHint.visibility = if (unavailable > 0) View.VISIBLE else View.GONE
        binding.emptyState.text = if (ids.isEmpty()) "This playlist is empty.\nAdd songs from your library." else "No playable songs are currently available."
        binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
        loadPlaylistArtwork(ids.mapNotNull { songsById[it] })
        playlistSongAdapter = PlaylistSongAdapter(
            songs = songs,
            onPlay = { index ->
                if (index != RecyclerView.NO_POSITION) (activity as? MainActivity)?.playQueue(playlistSongAdapter?.let {
                    it.songIds().mapNotNull { id -> songsById[id] }
                } ?: songs, index)
            },
            onDragStart = { holder -> playlistTouchHelper.startDrag(holder) }
        )
        draggedPlaylistName = null
        binding.list.adapter = playlistSongAdapter

        binding.btnPlaylistPlay.setOnClickListener {
            if (songs.isNotEmpty()) (activity as? MainActivity)?.playQueue(songs, 0)
            else Toast.makeText(requireContext(), "No playable songs in this playlist", Toast.LENGTH_SHORT).show()
        }
        binding.btnPlaylistMenu.setOnClickListener { showPlaylistMenu(name) }
        binding.btnPlaylistAdd.setOnClickListener { showAddSongsPicker(name) }
        binding.btnPlaylistShuffle.setOnClickListener {
            if (songs.isNotEmpty()) (activity as? MainActivity)?.playQueue(songs.shuffled(), 0)
            else Toast.makeText(requireContext(), "No playable songs in this playlist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddSongsPicker(playlistName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_add_songs, null)
        val search = dialogView.findViewById<EditText>(R.id.playlist_add_search)
        val count = dialogView.findViewById<android.widget.TextView>(R.id.playlist_add_count)
        val list = dialogView.findViewById<RecyclerView>(R.id.playlist_add_list)
        val cancel = dialogView.findViewById<android.widget.Button>(R.id.playlist_add_cancel)
        val add = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.playlist_add_confirm)
        val picker = PlaylistPickerAdapter { selected ->
            count.text = if (selected == 1) "1 selected" else "$selected selected"
            add.isEnabled = selected > 0
        }
        val existing = PlaylistStore.getSongIds(requireContext(), playlistName).toSet()
        picker.setItems(librarySongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }), existing)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.itemAnimator = null
        list.adapter = picker

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { picker.filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        cancel.setOnClickListener { dialog.dismiss() }
        add.setOnClickListener {
            val selected = picker.selectedIds()
            if (selected.isEmpty()) return@setOnClickListener
            val added = PlaylistStore.addSongs(requireContext(), playlistName, selected)
            dialog.dismiss()
            renderPlaylistSongs(playlistName)
            Toast.makeText(requireContext(), if (added == 1) "Added 1 song" else "Added $added songs", Toast.LENGTH_SHORT).show()
        }
        dialog.setOnDismissListener { picker.close() }
        DialogStyler.show(dialog)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.86f).toInt()
        )
        search.requestFocus()
    }

    private fun openPlaylist(name: String) { openPlaylist = name; render() }
    private fun closePlaylist() { openPlaylist = null; binding.playlistSearch.setText(""); render() }

    /** Lets the persistent shell handle nested playlist navigation without a second back button. */
    fun handleShellBack(): Boolean {
        if (openPlaylist == null) return false
        closePlaylist()
        return true
    }

    private fun showCreateDialog() {
        val input = EditText(requireContext()).apply { hint = "Playlist name"; setSingleLine(true); setPadding(32, 8, 32, 8) }
        AlertDialog.Builder(requireContext()).setTitle("New playlist").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (!PlaylistStore.createPlaylist(requireContext(), name)) Toast.makeText(requireContext(), "Choose a unique name", Toast.LENGTH_SHORT).show()
                render()
            }.let { DialogStyler.show(it) }
    }

    private fun showPlaylistMenu(name: String) {
        AlertDialog.Builder(requireContext()).setTitle(name)
            .setItems(arrayOf("Rename", "Delete")) { _, which -> if (which == 0) showRenameDialog(name) else confirmDelete(name) }
            .let { DialogStyler.show(it) }
    }

    private fun showRenameDialog(oldName: String) {
        val input = EditText(requireContext()).apply { setText(oldName); selectAll(); setSingleLine(true) }
        AlertDialog.Builder(requireContext()).setTitle("Rename playlist").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                if (PlaylistStore.renamePlaylist(requireContext(), oldName, newName)) { openPlaylist = newName; render() }
                else Toast.makeText(requireContext(), "Choose a unique name", Toast.LENGTH_SHORT).show()
            }.let { DialogStyler.show(it) }
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(requireContext()).setTitle("Delete playlist?").setMessage("\"$name\" will be removed, but your music files will not be deleted.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                PlaylistStore.deletePlaylist(requireContext(), name); closePlaylist()
            }.let { DialogStyler.show(it) }
    }

    private fun loadPlaylistArtwork(songs: List<Song>) {
        val target = binding.playlistArtwork
        val expectedId = songs.firstOrNull()?.id ?: -1L
        target.tag = expectedId
        target.setImageResource(R.mipmap.ic_launcher)
        if (expectedId == -1L) return
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val song = songs.first()
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(requireContext(), android.net.Uri.parse(song.uriString))
                    retriever.embeddedPicture?.let { bytes -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                } finally { retriever.release() }
            } catch (_: Exception) { null }
            if (!isAdded || _binding == null) return@launch
            withContext(Dispatchers.Main) {
                if (_binding != null && target.tag == expectedId && bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    override fun onDestroyView() {
        playlistSongAdapter?.close()
        playlistSongAdapter = null
        playlistCatalogAdapter?.close()
        playlistCatalogAdapter = null
        super.onDestroyView(); _binding = null
    }

    private class PlaylistAdapter(
        private val allNames: List<String>,
        private val songs: List<Song>,
        private val click: (String) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<String, PlaylistAdapter.Holder>(DIFF) {
        private val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        private val artworkCache = object : android.util.LruCache<Long, android.graphics.Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {}

        init { submitList(allNames) }

        fun setData(names: List<String>, newSongs: List<Song>) {
            allNamesRef = names.toList()
            songsRef = newSongs.toList()
            submitList(allNamesRef)
        }

        private var allNamesRef: List<String> = allNames.toList()
        private var songsRef: List<Song> = songs.toList()

        fun filter(query: String) {
            val q = query.trim()
            submitList(if (q.isBlank()) allNamesRef else allNamesRef.filter { it.contains(q, ignoreCase = true) })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val name = getItem(position)
            holder.name = name
            holder.binding.name.text = name
            val ids = PlaylistStore.getSongIds(holder.binding.root.context, name)
            holder.binding.count.text = "${ids.size} ${if (ids.size == 1) "song" else "songs"}"
            holder.binding.artwork.setImageResource(R.mipmap.ic_launcher)
            val song = ids.firstOrNull()?.let { id -> songsRef.firstOrNull { it.id.toString() == id } }
            if (song != null) {
                val key = song.albumId.takeIf { it >= 0L } ?: song.id
                artworkCache.get(key)?.let { holder.binding.artwork.setImageBitmap(it) } ?: executor.execute {
                    val bitmap = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(holder.itemView.context, android.net.Uri.parse(song.uriString))
                            retriever.embeddedPicture?.let { bytes -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        } finally { retriever.release() }
                    } catch (_: Exception) { null }
                    if (bitmap != null) artworkCache.put(key, bitmap)
                    if (bitmap != null) main.post {
                        if (holder.name == name) holder.binding.artwork.setImageBitmap(bitmap)
                    }
                }
            }
            holder.binding.root.setOnClickListener { click(name) }
        }

        override fun onViewRecycled(holder: Holder) {
            holder.name = null
            holder.binding.artwork.setImageResource(R.mipmap.ic_launcher)
            super.onViewRecycled(holder)
        }

        fun close() {
            executor.shutdownNow()
            main.removeCallbacksAndMessages(null)
            artworkCache.evictAll()
        }

        class Holder(val binding: ItemPlaylistBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
            var name: String? = null
        }

        companion object {
            private val DIFF = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<String>() {
                override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem.equals(newItem, ignoreCase = true)
                override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            }
        }
    }

}

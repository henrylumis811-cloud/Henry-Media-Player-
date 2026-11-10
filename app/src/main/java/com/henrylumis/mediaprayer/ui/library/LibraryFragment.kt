package com.henrylumis.mediaprayer.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.henrylumis.mediaprayer.MainActivity
import com.henrylumis.mediaprayer.R
import com.henrylumis.mediaprayer.data.MusicScanner
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.data.SongSorter
import com.henrylumis.mediaprayer.databinding.FragmentLibraryBinding
import com.henrylumis.mediaprayer.trim.TrimmerDialog
import com.henrylumis.mediaprayer.ui.common.ArtworkScrollGate
import com.henrylumis.mediaprayer.ui.common.RecyclerViewPerformance
import com.henrylumis.mediaprayer.ui.common.DialogStyler
import com.henrylumis.mediaprayer.ui.common.GlassPopup
import com.henrylumis.mediaprayer.ui.common.TrackInfoDialog
import com.henrylumis.mediaprayer.util.PlaylistStore
import com.henrylumis.mediaprayer.util.Prefs
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var artworkScrollListener: androidx.recyclerview.widget.RecyclerView.OnScrollListener? = null

    private var allSongs: List<Song> = emptyList()
    private var searchQuery: String = ""
    private var sortMode: SongSorter.SortMode = SongSorter.SortMode.TITLE_ASC
    private var favoritesOnly: Boolean = false
    private enum class ViewMode { SONGS, ALBUMS, ARTISTS }
    private var viewMode: ViewMode = ViewMode.SONGS
    private lateinit var groupAdapter: LibraryGroupAdapter
    private var searchDebounceRunnable: Runnable? = null
    private var filterJob: Job? = null
    private var filterGeneration = 0L
    private var scanJob: Job? = null
    private var scanGeneration = 0L
    private val mediaRefreshHandler = Handler(Looper.getMainLooper())
    private val mediaRefreshRunnable = Runnable { if (_binding != null && isAdded) loadLibrary() }
    private val mediaObserver = object : ContentObserver(mediaRefreshHandler) {
        override fun onChange(selfChange: Boolean) {
            scheduleMediaRefresh()
        }
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            scheduleMediaRefresh()
        }
    }
    private var mediaObserverRegistered = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sortMode = SongSorter.modeFromSavedName(Prefs.getSortMode(requireContext()))

        adapter = SongAdapter(
            onClick = { song, _ ->
                // Play starting from this song's position within the currently
                // displayed (filtered + sorted) list, so what you tap is what plays first.
                val displayed = adapter.currentList()
                val startIndex = displayed.indexOf(song).coerceAtLeast(0)
                (activity as? MainActivity)?.playQueue(displayed, startIndex)
            },
            onLongClick = { song -> showSongLongPressMenu(song) },
            onSelectionChanged = { count -> updateSelectionUi(count) }
        )
        groupAdapter = LibraryGroupAdapter { group ->
            val songs = group.songs
                .filter { it.uriString.isNotBlank() }
                .let { SongSorter.sort(requireContext(), it, SongSorter.SortMode.TITLE_ASC) }
            if (songs.isEmpty()) {
                Toast.makeText(requireContext(), "No playable songs found in this album", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    (activity as? MainActivity)?.playQueue(songs, 0)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Couldn't open this album", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.songList.layoutManager = LinearLayoutManager(requireContext()).apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = 4
        }
        RecyclerViewPerformance.optimize(binding.songList, cachedRows = 6)
        binding.songList.adapter = adapter
        artworkScrollListener = ArtworkScrollGate.attach(binding.songList)


        updateViewModeHint()
        binding.btnViewMode.setOnClickListener { showViewModeMenu() }


        binding.btnAddToPlaylist.setOnClickListener { showAddSelectedToPlaylistDialog() }

        binding.swipeRefresh.setOnRefreshListener { loadLibrary() }
        binding.btnGrantMusicPermission.setOnClickListener {
            (activity as? MainActivity)?.requestMusicPermission()
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                searchDebounceRunnable?.let { binding.searchInput.removeCallbacks(it) }
                searchDebounceRunnable = Runnable { applyFilterAndSort() }
                binding.searchInput.postDelayed(searchDebounceRunnable!!, 140)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSort.setOnClickListener { showSortMenu() }

        updateFavoritesFilterAppearance()
        binding.btnFavoritesFilter.setOnClickListener {
            favoritesOnly = !favoritesOnly
            updateFavoritesFilterAppearance()
            applyFilterAndSort()
        }

        loadLibrary()
        refreshPlaybackIndicator()
    }

    private fun updateFavoritesFilterAppearance() {
        binding.btnFavoritesFilter.setImageResource(
            if (favoritesOnly) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        binding.btnFavoritesFilter.contentDescription = if (favoritesOnly) {
            "Show all songs; favorites filter is on"
        } else {
            "Show favorites only"
        }
        binding.btnFavoritesFilter.isSelected = favoritesOnly
    }

    /** Update the current-track marker only when playback state actually changes. */
    fun refreshPlaybackIndicator() {
        if (_binding == null || !::adapter.isInitialized) return
        val activity = activity as? MainActivity ?: return
        adapter.updateNowPlaying(
            activity.pendingTrack?.mediaId ?: activity.player?.currentMediaItem?.mediaId,
            activity.player?.isPlaying ?: false
        )
    }

    fun playAllFromShell() {
        if (_binding == null) return
        val songs = SongSorter.sort(requireContext(), allSongs, SongSorter.SortMode.TITLE_ASC)
        if (songs.isNotEmpty()) {
            (activity as? MainActivity)?.playQueue(songs, 0)
        }
    }

    fun shuffleAllFromShell() {
        if (_binding == null) return
        val songs = allSongs.shuffled()
        if (songs.isNotEmpty()) {
            (activity as? MainActivity)?.playQueue(songs, 0)
        }
    }

    private fun showSongLongPressMenu(song: Song) {
        DialogStyler.show(
            AlertDialog.Builder(requireContext())
                .setTitle(song.title)
                .setItems(arrayOf("Track info", "Select for playlist", "Trim audio")) { _, which ->
                    when (which) {
                        0 -> TrackInfoDialog.show(requireContext(), song)
                        1 -> adapter.startSelection(song)
                        2 -> TrimmerDialog.show(requireContext(), song)
                    }
                }
        )
    }

    private fun updateSelectionUi(count: Int) {
        val active = count > 0
        binding.btnAddToPlaylist.visibility = if (active && viewMode == ViewMode.SONGS) View.VISIBLE else View.GONE
        // In selection mode the sort/view/favorites actions are unavailable anyway.
        // Remove them from the toolbar so the selection action and count remain clear
        // on narrow phones instead of squeezing the search field to almost nothing.
        val browsingActionVisibility = if (active) View.GONE else View.VISIBLE
        binding.btnSort.visibility = browsingActionVisibility
        binding.btnViewMode.visibility = browsingActionVisibility
        binding.btnFavoritesFilter.visibility = browsingActionVisibility
        binding.selectionCount.visibility = if (active) View.VISIBLE else View.GONE
        binding.selectionCount.text = "$count selected"
        binding.searchInput.isEnabled = !active
        binding.swipeRefresh.isEnabled = !active
    }

    private fun showAddSelectedToPlaylistDialog() {
        val selected = adapter.selectedSongs()
        if (selected.isEmpty()) return
        val names = PlaylistStore.getPlaylistNames(requireContext()).toMutableList()
        names.add("＋ Create new playlist")
        AlertDialog.Builder(requireContext())
            .setTitle("Add ${selected.size} songs to playlist")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == names.lastIndex) {
                    showCreatePlaylistFromSelection(selected)
                } else {
                    val added = PlaylistStore.addSongs(requireContext(), names[which], selected.map { it.id.toString() })
                    Toast.makeText(requireContext(), if (added == 0) "Songs already in playlist" else "$added song${if (added == 1) "" else "s"} added", Toast.LENGTH_SHORT).show()
                    adapter.clearSelection()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> adapter.clearSelection() }
            .let { DialogStyler.show(it) }
    }

    private fun showCreatePlaylistFromSelection(selected: List<Song>) {
        val input = EditText(requireContext()).apply { hint = "Playlist name"; setSingleLine(true); setPadding(32, 8, 32, 8) }
        AlertDialog.Builder(requireContext()).setTitle("Create playlist").setView(input)
            .setNegativeButton("Cancel") { _, _ -> adapter.clearSelection() }
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (PlaylistStore.createPlaylist(requireContext(), name)) {
                    PlaylistStore.addSongs(requireContext(), name, selected.map { it.id.toString() })
                    Toast.makeText(requireContext(), "Playlist created", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Choose a unique playlist name", Toast.LENGTH_SHORT).show()
                }
                adapter.clearSelection()
            }.let { DialogStyler.show(it) }
    }

    fun focusSearch() {
        val input = _binding?.searchInput ?: return
        input.requestFocus()
        input.post {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateViewModeHint() {
        binding.searchInput.hint = when (viewMode) {
            ViewMode.SONGS -> "Search songs, artists or albums"
            ViewMode.ALBUMS -> "Search albums or artists"
            ViewMode.ARTISTS -> "Search artists"
        }
        binding.viewModeLabel.text = viewMode.name
    }

    private fun setViewMode(mode: ViewMode) {
        if (viewMode == mode) return
        viewMode = mode
        updateViewModeHint()
        applyFilterAndSort()
        updateSelectionUi(if (adapter.isSelectionMode()) adapter.selectedSongs().size else 0)
    }

    private fun showViewModeMenu() {
        GlassPopup.show(requireContext(), binding.btnViewMode, listOf(
            GlassPopup.Item("Songs", viewMode == ViewMode.SONGS),
            GlassPopup.Item("Albums", viewMode == ViewMode.ALBUMS),
            GlassPopup.Item("Artists", viewMode == ViewMode.ARTISTS)
        )) { index ->
            setViewMode(when (index) {
                1 -> ViewMode.ALBUMS
                2 -> ViewMode.ARTISTS
                else -> ViewMode.SONGS
            })
        }
    }

    private fun showSortMenu() {
        val modes = SongSorter.SortMode.values()
        GlassPopup.show(requireContext(), binding.btnSort, modes.map {
            GlassPopup.Item(it.label, it == sortMode)
        }) { index ->
            sortMode = modes[index]
            Prefs.setSortMode(requireContext(), sortMode.name)
            applyFilterAndSort()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mediaObserverRegistered) {
            try {
                requireContext().contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
                )
                mediaObserverRegistered = true
            } catch (_: Exception) {
                // Pull-to-refresh remains available if a provider rejects observation.
            }
        }
    }

    override fun onStop() {
        if (mediaObserverRegistered) {
            try { requireContext().contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
            mediaObserverRegistered = false
        }
        mediaRefreshHandler.removeCallbacks(mediaRefreshRunnable)
        super.onStop()
    }

    private fun scheduleMediaRefresh() {
        mediaRefreshHandler.removeCallbacks(mediaRefreshRunnable)
        mediaRefreshHandler.postDelayed(mediaRefreshRunnable, 650L)
    }

    override fun onResume() {
        super.onResume()
        if (favoritesOnly) applyFilterAndSort()
        refreshPlaybackIndicator()
        // Entering the Library should land directly on the active track rather
        // than visibly animating from the previous scroll position.
        showCurrentTrackImmediately()
    }

    private fun showCurrentTrackImmediately() {
        val activity = activity as? MainActivity ?: return
        val recycler = _binding?.songList ?: return
        val currentId = activity.pendingTrack?.mediaId ?: activity.player?.currentMediaItem?.mediaId ?: return
        recycler.post {
            if (_binding == null || !isAdded || viewMode != ViewMode.SONGS) return@post
            val position = adapter.currentList().indexOfFirst { it.id.toString() == currentId }
            if (position >= 0 && position < adapter.itemCount) {
                (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
            }
        }
    }

    fun loadLibrary() {
        val currentBinding = _binding ?: return
        val generation = ++scanGeneration
        scanJob?.cancel()
        currentBinding.swipeRefresh.isRefreshing = true
        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val scanned = MusicScanner.scan(requireContext())
                if (generation != scanGeneration || _binding == null) return@launch
                // Replace the visible snapshot only after a complete successful scan.
                // A failed/null MediaStore query must never masquerade as an empty library.
                allSongs = scanned
                binding.songList.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                binding.btnGrantMusicPermission.visibility = View.GONE
                binding.searchInput.isEnabled = true
                binding.btnSort.isEnabled = true
                binding.btnViewMode.isEnabled = true
                binding.btnFavoritesFilter.isEnabled = true
                binding.swipeRefresh.isEnabled = true
                applyFilterAndSort()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                if (generation != scanGeneration || _binding == null) return@launch
                showPermissionRequired()
            } catch (_: Exception) {
                if (generation != scanGeneration || _binding == null) return@launch
                // Keep the last good list and its scroll position when MediaStore is
                // temporarily unavailable; never clear a valid library on scan failure.
                if (allSongs.isEmpty()) {
                    binding.songList.visibility = View.VISIBLE
                    binding.emptyState.text = "Couldn't load your music. Check that your storage is available, then pull down to try again."
                    binding.emptyState.visibility = View.VISIBLE
                    binding.btnGrantMusicPermission.visibility = View.GONE
                } else {
                    binding.songList.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Couldn't refresh music. Showing the previous library.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (generation == scanGeneration && _binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    fun showPermissionRequired() {
        val current = _binding ?: return
        current.songList.visibility = View.GONE
        current.emptyState.text = "Music access is turned off. Allow RVH Music to read audio on this device."
        current.emptyState.visibility = View.VISIBLE
        current.btnGrantMusicPermission.visibility = View.VISIBLE
        current.searchInput.isEnabled = false
        current.btnSort.isEnabled = false
        current.btnViewMode.isEnabled = false
        current.btnFavoritesFilter.isEnabled = false
        current.swipeRefresh.isEnabled = false
        current.swipeRefresh.isRefreshing = false
    }

    private fun applyFilterAndSort() {
        if (_binding == null) return
        val generation = ++filterGeneration
        filterJob?.cancel()

        // Snapshot mutable UI state on the main thread, then do filtering,
        // grouping and potentially expensive preference-based sorting off it.
        val source = allSongs
        val query = searchQuery.trim()
        val mode = viewMode
        val selectedSort = sortMode
        val onlyFavorites = favoritesOnly
        val context = requireContext().applicationContext
        val favoriteIds = if (onlyFavorites) PlaylistStore.getFavoriteIds(context) else emptySet()

        filterJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                var songs = source
                if (onlyFavorites) songs = songs.filter { favoriteIds.contains(it.id.toString()) }
                if (query.isNotBlank()) {
                    songs = songs.filter {
                        when (mode) {
                            ViewMode.SONGS -> it.title.contains(query, ignoreCase = true) ||
                                it.artist.contains(query, ignoreCase = true) || it.album.contains(query, ignoreCase = true)
                            ViewMode.ALBUMS -> it.album.contains(query, ignoreCase = true) ||
                                it.artist.contains(query, ignoreCase = true)
                            ViewMode.ARTISTS -> it.artist.contains(query, ignoreCase = true)
                        }
                    }
                }
                val sorted = SongSorter.sort(context, songs, selectedSort)
                if (mode == ViewMode.SONGS) sorted to emptyList<LibraryGroup>()
                else sorted to buildGroups(sorted, mode)
            }
            if (_binding == null || generation != filterGeneration) return@launch
            val songs = result.first
            val groups = result.second
            if (mode == ViewMode.SONGS) {
                binding.songList.adapter = adapter
                adapter.submitList(songs) { showCurrentTrackImmediately() }
                binding.listSectionTitle.text = when (songs.size) {
                    0 -> ""
                    1 -> "1 song"
                    else -> "${songs.size} songs"
                }
            } else {
                binding.songList.adapter = groupAdapter
                groupAdapter.submitList(groups)
                binding.listSectionTitle.text = when (groups.size) {
                    0 -> ""
                    1 -> "1 ${if (mode == ViewMode.ALBUMS) "album" else "artist"}"
                    else -> "${groups.size} ${if (mode == ViewMode.ALBUMS) "albums" else "artists"}"
                }
            }
            val empty = if (mode == ViewMode.SONGS) songs.isEmpty() else groups.isEmpty()
            if (empty) {
                binding.emptyState.text = if (allSongs.isEmpty()) {
                    "No music found on this device.\nAdd some songs, then pull down to rescan."
                } else {
                    "No songs match your current filters."
                }
                binding.emptyState.visibility = View.VISIBLE
            } else {
                binding.emptyState.visibility = View.GONE
            }
        }
    }

    private fun buildGroups(songs: List<Song>, mode: ViewMode): List<LibraryGroup> {
        val grouped = when (mode) {
            ViewMode.ALBUMS -> songs.groupBy { it.album.trim().ifBlank { "Unknown album" } }
            ViewMode.ARTISTS -> songs.groupBy { it.artist.trim().ifBlank { "Unknown artist" } }
            ViewMode.SONGS -> emptyMap()
        }
        return grouped.entries
            .sortedBy { it.key.lowercase() }
            .map { (name, groupSongs) ->
                val first = groupSongs.first()
                LibraryGroup(
                    key = "${mode.name}:$name",
                    title = name,
                    subtitle = if (mode == ViewMode.ALBUMS) {
                        groupSongs.map { it.artist.trim() }.firstOrNull { it.isNotBlank() } ?: "Unknown artist"
                    } else {
                        "Artist"
                    },
                    songs = groupSongs,
                    artSong = first,
                    // Longer tracks are more likely to contain complete embedded tags;
                    // keep every track as a fallback rather than trusting the first row.
                    artCandidates = groupSongs.sortedByDescending { it.durationMs }
                )
            }
    }

    override fun onDestroyView() {
        scanGeneration++
        scanJob?.cancel()
        scanJob = null
        mediaRefreshHandler.removeCallbacks(mediaRefreshRunnable)
        if (mediaObserverRegistered) {
            try { requireContext().contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
            mediaObserverRegistered = false
        }
        _binding?.songList?.let { ArtworkScrollGate.detach(it, artworkScrollListener) }
        artworkScrollListener = null
        adapter.clearSelection()
        filterJob?.cancel()
        filterJob = null
        searchDebounceRunnable?.let { _binding?.searchInput?.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}

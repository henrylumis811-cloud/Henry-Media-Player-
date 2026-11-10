package com.henrylumis.mediaprayer

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.henrylumis.mediaprayer.data.MusicScanner
import com.henrylumis.mediaprayer.data.Song
import com.henrylumis.mediaprayer.data.SongSorter
import com.henrylumis.mediaprayer.data.toMediaItem
import com.henrylumis.mediaprayer.data.toArtworkSong
import com.henrylumis.mediaprayer.ui.common.ArtworkLoader
import com.henrylumis.mediaprayer.databinding.ActivityMainBinding
import com.henrylumis.mediaprayer.ui.altar.AltarFragment
import com.henrylumis.mediaprayer.ui.library.LibraryFragment
import com.henrylumis.mediaprayer.ui.playlists.PlaylistsFragment
import com.henrylumis.mediaprayer.ui.queue.QueueFragment
import com.henrylumis.mediaprayer.ui.signal.SignalFragment
import com.henrylumis.mediaprayer.ui.verses.VersesFragment
import com.henrylumis.mediaprayer.ui.common.GlassPopup
import com.henrylumis.mediaprayer.util.Prefs
import kotlinx.coroutines.launch

/**
 * RVH Music presents every music experience inside one persistent visual shell.
 * Library, playback, lyrics, queue and settings are contextual states of the
 * same environment rather than separate visual identities. Playback remains
 * owned by PlaybackService/DualPlayerBridge.
 */
@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaController: MediaController? = null
    private var lastBackPressAt = 0L
    private var controllerListener: Player.Listener? = null
    // Prevent rapid repeated taps from enqueueing duplicate Fragment transactions.
    private var pendingDestinationTag: String? = null
    private var miniArtworkMediaId: String? = null
    private var lastAudioPermissionGranted: Boolean? = null

    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val current = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
            if (current is PlaylistsFragment && current.handleShellBack()) return
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastBackPressAt < 1800L) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            } else {
                lastBackPressAt = now
                Toast.makeText(this@MainActivity, "Press Back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val player: Player? get() = mediaController
    val exoPlayerForVisualizer get() = PlaybackService.instance?.exoPlayer
    val equalizer get() = PlaybackService.instance?.equalizer
    val pendingTrack: MediaItem? get() = PlaybackService.instance?.pendingCrossfadeTarget

    fun seekTo(positionMs: Long) {
        val service = PlaybackService.instance
        if (service != null) service.seekToPosition(positionMs) else mediaController?.seekTo(positionMs)
    }

    /**
     * Keeps the mini-player visually attached to the device rather than making
     * it look like a rectangular sheet laid over the bottom of the screen.
     *
     * The background remains full width, while the actual controls keep their
     * own touch-safe padding. On Android 12+ the platform reports the physical
     * display corner radii, so both sides can adapt independently on devices
     * whose corners are not perfectly symmetrical.
     */
    private fun applyMiniPlayerDisplayGeometry() {
        val root = binding.miniPlayer.miniPlayerRoot
        val density = resources.displayMetrics.density
        val fallbackRadius = 16f * density
        val horizontalSafePadding = (10f * density).toInt()

        fun updateBackground(insets: WindowInsetsCompat?) {
            val platformInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                insets?.toWindowInsets()
            } else null

            val bottomLeft = platformInsets
                ?.getRoundedCorner(3)
                ?.radius
                ?.toFloat()
                ?.takeIf { it > 0f }
                ?: fallbackRadius

            val bottomRight = platformInsets
                ?.getRoundedCorner(2)
                ?.radius
                ?.toFloat()
                ?.takeIf { it > 0f }
                ?: fallbackRadius

            // Keep the radius bounded by the mini-player's height so an
            // unusually large device corner cannot consume the whole surface.
            val maxRadius = root.height.takeIf { it > 0 }?.times(0.5f) ?: 34f * density
            val leftRadius = bottomLeft.coerceAtMost(maxRadius)
            val rightRadius = bottomRight.coerceAtMost(maxRadius)

            root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Slightly lighter than the old slab so the car image continues
                // visually through the player instead of stopping at a black wall.
                setColor(0x78000000)
                setStroke((1f * density).toInt().coerceAtLeast(1), 0x1CFFFFFF)
                setCornerRadii(
                    floatArrayOf(
                        0f, 0f,
                        0f, 0f,
                        rightRadius, rightRadius,
                        leftRadius, leftRadius
                    )
                )
            }
        }

        // The player already has deliberate horizontal touch padding. Keep it
        // stable instead of copying system-bar insets into the background itself.
        // System-bar/gesture protection is handled by the window layout, while
        // the background is allowed to remain edge-to-edge when applicable.
        root.setPadding(
            horizontalSafePadding,
            root.paddingTop,
            (8f * density).toInt(),
            root.paddingBottom
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            updateBackground(insets)
            insets
        }

        root.post { updateBackground(ViewCompat.getRootWindowInsets(root)) }
        updateBackground(ViewCompat.getRootWindowInsets(root))
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshMediaPermissionState()
    }

    private val pickBackgroundPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            Prefs.setBackgroundUri(this, uri)
            applyBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        applyMiniPlayerDisplayGeometry()

        binding.btnHeaderBack.setOnClickListener {
            val current = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
            if (current is PlaylistsFragment && current.handleShellBack()) return@setOnClickListener
            supportFragmentManager.popBackStack()
        }
        binding.btnHeaderSearch.setOnClickListener { focusLibrarySearch() }
        binding.btnHeaderMore.setOnClickListener { showMoreMenu() }
        binding.miniPlayer.miniPlayerRoot.setOnClickListener {
            if ((pendingTrack ?: mediaController?.currentMediaItem) != null) openNowPlaying()
        }
        binding.miniPlayer.miniPlayPause.setOnClickListener { togglePlayPause() }
        binding.miniPlayer.miniPrevious.setOnClickListener { skipPrevious() }
        binding.miniPlayer.miniNext.setOnClickListener { skipNext() }

        supportFragmentManager.addOnBackStackChangedListener {
            // FragmentManager applies shell navigation asynchronously. Release the
            // tap guard only after that transaction/back-stack change has landed.
            pendingDestinationTag = null
            updateShellForDestination()
        }
        binding.mainFragmentContainer.setOnSwipeDismissListener {
            supportFragmentManager.popBackStack()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.main_fragment_container, LibraryFragment(), "library")
                // The initial root transaction is not on the back stack, so the
                // back-stack listener cannot announce its completion. Apply the
                // shell state only after Library is actually installed; otherwise
                // a cold launch can briefly (or indefinitely) retain the stale
                // secondary-screen back button.
                .runOnCommit { updateShellForDestination() }
                .commit()
        }

        applyBackground()
        requestNeededPermissions()
        connectToPlaybackService()
        updateShellForDestination()
    }

    override fun onResume() {
        super.onResume()
        // Reconcile the persistent header after process restoration or returning
        // from an external activity, even if no FragmentManager back-stack event
        // was emitted during that lifecycle transition.
        updateShellForDestination()
        // Runtime permissions can be revoked or restored while the app is in
        // Settings. Reconcile only when the access state actually changes.
        refreshMediaPermissionState()
    }

    private fun hasAudioPermission(): Boolean = MusicScanner.hasAudioPermission(this)

    private fun refreshMediaPermissionState() {
        val granted = hasAudioPermission()
        val previous = lastAudioPermissionGranted
        lastAudioPermissionGranted = granted
        val library = supportFragmentManager.findFragmentByTag("library") as? LibraryFragment
        if (granted) {
            if (previous != true) library?.loadLibrary()
            PlaybackService.instance?.refreshMediaLibraryAfterPermissionChange()
        } else {
            library?.showPermissionRequired()
        }
    }

    private fun focusLibrarySearch() {
        val library = supportFragmentManager.findFragmentByTag("library") as? LibraryFragment
        if (library != null) {
            while (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStackImmediate()
            }
            binding.mainFragmentContainer.post {
                (supportFragmentManager.findFragmentByTag("library") as? LibraryFragment)?.focusSearch()
            }
        }
    }

    private fun showMoreMenu() {
        // The overflow control is intentionally contextual now. It belongs to
        // Library, where its actions operate on the library as a whole.
        // Other destinations already have their own local actions and should
        // not be covered by a global navigation popup.
        val current = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        if (current !is LibraryFragment) return

        val items = listOf("Play all", "Shuffle all", "Queue", "Playlists", "Settings")
            .map { GlassPopup.Item(it, false) }

        binding.btnHeaderMore.animate().cancel()
        binding.btnHeaderMore.animate().rotation(180f).setDuration(160L).start()
        GlassPopup.show(this, binding.btnHeaderMore, items, onDismiss = {
            binding.btnHeaderMore.animate().cancel()
            binding.btnHeaderMore.animate().rotation(0f).setDuration(140L).start()
        }) { index ->
            when (index) {
                0 -> playAllFromShell()
                1 -> shuffleAllFromShell()
                2 -> openQueue()
                3 -> openPlaylists()
                4 -> openSignal()
            }
        }
    }
    private fun playAllFromShell() {
        lifecycleScope.launch {
            try {
                val songs = MusicScanner.scan(this@MainActivity)
                val ordered = SongSorter.sort(this@MainActivity, songs, SongSorter.SortMode.TITLE_ASC)
                if (ordered.isNotEmpty()) playQueue(ordered, 0)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@MainActivity, "Couldn't read the music library.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shuffleAllFromShell() {
        lifecycleScope.launch {
            try {
                val songs = MusicScanner.scan(this@MainActivity).shuffled()
                if (songs.isNotEmpty()) playQueue(songs, 0)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@MainActivity, "Couldn't read the music library.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFragment(fragment: androidx.fragment.app.Fragment, tag: String) {
        val manager = supportFragmentManager
        if (manager.isStateSaved) return
        if (pendingDestinationTag != null) return

        val current = manager.findFragmentById(R.id.main_fragment_container)
        if (current?.tag == tag) return

        // Reuse an existing shell destination rather than creating a second
        // instance. Popping to its named entry restores the exact previous
        // navigation context and its retained Fragment state.
        val existing = manager.findFragmentByTag(tag)
        if (existing != null) {
            pendingDestinationTag = tag
            manager.popBackStack(tag, 0)
            return
        }

        pendingDestinationTag = tag
        manager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.animator.screen_enter_anim, R.animator.screen_exit_anim,
                R.animator.screen_pop_enter_anim, R.animator.screen_pop_exit_anim
            )
            .replace(R.id.main_fragment_container, fragment, tag)
            .addToBackStack(tag)
            .commit()
    }

    fun openQueue() = openFragment(QueueFragment(), "queue")
    fun openLibraryFromQueue() {
        // This action means "browse the library", not merely "go back one
        // screen". Queue may have been opened from Now Playing or another
        // destination, so unwind the complete shell stack to the Library root.
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }
    fun openPlaylists() = openFragment(PlaylistsFragment(), "playlists")
    fun openSignal() = openFragment(SignalFragment(), "signal")
    fun openVerses() = openFragment(VersesFragment(), "verses")
    fun openNowPlaying() {
        val manager = supportFragmentManager
        if (manager.isStateSaved || pendingDestinationTag != null) return
        val current = manager.findFragmentById(R.id.main_fragment_container)
        if (current is AltarFragment) return

        // The mini-player can be tapped from a screen opened above Now Playing.
        // Reuse the existing Now Playing entry instead of stacking a second
        // AltarFragment, which would duplicate listeners and rendering surfaces.
        if (manager.findFragmentByTag("altar") != null) {
            pendingDestinationTag = "altar"
            manager.popBackStack("altar", 0)
            return
        }

        // First visit: add one Now Playing layer over the current destination.
        // Back therefore returns to the exact screen from which it was opened.
        pendingDestinationTag = "altar"
        manager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.animator.screen_enter_anim, R.animator.screen_exit_anim,
                R.animator.screen_pop_enter_anim, R.animator.screen_pop_exit_anim
            )
            .add(R.id.main_fragment_container, AltarFragment(), "altar")
            .addToBackStack("altar")
            .commit()
    }

    /** Contextual title override used by nested content inside a single shell destination. */
    fun setShellTitle(title: String) {
        binding.headerTitle.text = title
    }

    private fun updateShellForDestination() {
        val current = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        // During the very first cold-launch frame the root Fragment transaction
        // may not have committed yet. Treat that empty container as the Library
        // root so the persistent header never flashes the dead-end back arrow.
        val isPrimarySurface = current == null || current is LibraryFragment
        binding.btnHeaderBack.visibility = if (isPrimarySurface) View.GONE else View.VISIBLE
        binding.headerAvatar.visibility = if (isPrimarySurface) View.VISIBLE else View.GONE
        binding.btnHeaderSearch.visibility = View.GONE
        // The overflow menu is a Library-level action surface only. Queue,
        // Lyrics, Playlists and Settings use their own contextual controls,
        // so a global popup would obscure content and duplicate navigation.
        binding.btnHeaderMore.visibility = if (isPrimarySurface) View.VISIBLE else View.GONE
        binding.headerTitle.visibility = View.VISIBLE
        binding.headerTitle.text = when (current) {
            is LibraryFragment -> "RVH MUSIC"
            is AltarFragment -> "NOW PLAYING"
            is QueueFragment -> "QUEUE"
            is PlaylistsFragment -> "PLAYLISTS"
            is VersesFragment -> "LYRICS"
            is SignalFragment -> "SETTINGS"
            else -> "RVH MUSIC"
        }
        binding.miniPlayer.miniPlayerRoot.visibility = if (current is AltarFragment) View.GONE else View.VISIBLE
        binding.mainFragmentContainer.setSwipeDismissEnabled(current != null && current !is LibraryFragment)
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        val item = pendingTrack ?: mediaController?.currentMediaItem
        if (item == null) {
            binding.miniPlayer.miniTitle.text = "Nothing playing"
            binding.miniPlayer.miniArtist.text = "Choose a track"
            miniArtworkMediaId = null
            binding.miniPlayer.miniArtwork.setImageResource(R.mipmap.ic_launcher)
            binding.miniPlayer.miniPlayPause.setImageResource(android.R.drawable.ic_media_play)
            binding.miniPlayer.miniPlayPause.contentDescription = "Play music"
            return
        }

        binding.miniPlayer.miniTitle.text = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" }
        binding.miniPlayer.miniArtist.text = item.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" }
        binding.miniPlayer.miniPlayPause.setImageResource(if (mediaController?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        binding.miniPlayer.miniPlayPause.contentDescription = if (mediaController?.isPlaying == true) "Pause" else "Play"

        // Do not clear the artwork during routine shell updates. Screen navigation,
        // playback-state callbacks and progress updates often refer to the same song.
        if (miniArtworkMediaId == item.mediaId) return
        miniArtworkMediaId = item.mediaId
        val artworkSong = item.toArtworkSong()
        if (artworkSong == null) {
            binding.miniPlayer.miniArtwork.setImageResource(R.mipmap.ic_launcher)
            return
        }
        ArtworkLoader.peek(artworkSong)?.let {
            binding.miniPlayer.miniArtwork.setImageBitmap(it)
            return
        }
        ArtworkLoader.load(this, artworkSong) { bitmap ->
            val currentId = (pendingTrack ?: mediaController?.currentMediaItem)?.mediaId
            if (miniArtworkMediaId == item.mediaId && currentId == item.mediaId) {
                if (bitmap != null) binding.miniPlayer.miniArtwork.setImageBitmap(bitmap)
                else if (ArtworkLoader.peek(artworkSong) == null) binding.miniPlayer.miniArtwork.setImageResource(R.mipmap.ic_launcher)
            }
        }
    }

    fun pickBackground() {
        pickBackgroundPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun clearBackground() {
        Prefs.setBackgroundUri(this, null)
        applyBackground()
    }

    fun setBackgroundOpacity(percent: Int) {
        Prefs.setBackgroundOpacity(this, percent)
        binding.bgPhoto.alpha = percent / 100f
    }

    fun applyBackground() {
        val uri = Prefs.getBackgroundUri(this)
        val opacity = Prefs.getBackgroundOpacity(this) / 100f
        if (uri != null) {
            try {
                binding.bgPhoto.setImageURI(uri)
                binding.bgPhoto.alpha = opacity
                binding.bgPhoto.visibility = View.VISIBLE
            } catch (_: Exception) {
                Prefs.setBackgroundUri(this, null)
                binding.bgPhoto.setImageResource(R.drawable.bg_supercar_default)
                binding.bgPhoto.alpha = opacity
                binding.bgPhoto.visibility = View.VISIBLE
            }
        } else {
            binding.bgPhoto.setImageResource(R.drawable.bg_supercar_default)
            binding.bgPhoto.alpha = opacity
            binding.bgPhoto.visibility = View.VISIBLE
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(audioPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("rvh_permission_state", MODE_PRIVATE).edit()
                .putBoolean("audio_permission_requested", true).apply()
            perms.add(audioPermission)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) requestPermissions.launch(perms.toTypedArray())
    }

    /** Called from the Library's inline empty-state action. If Android has
     * stopped showing the runtime prompt, take the user directly to app settings. */
    fun requestMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            refreshMediaPermissionState()
            return
        }
        val state = getSharedPreferences("rvh_permission_state", MODE_PRIVATE)
        val requestedBefore = state.getBoolean("audio_permission_requested", false)
        val shouldExplain = shouldShowRequestPermissionRationale(permission)
        if (!requestedBefore || shouldExplain) {
            state.edit().putBoolean("audio_permission_requested", true).apply()
            requestPermissions.launch(arrayOf(permission))
        } else {
            try {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ))
            } catch (_: Exception) {
                Toast.makeText(this, "Open Android Settings and allow music access for RVH Music.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                controllerListener = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateMiniPlayer()
                        refreshLibraryPlaybackIndicator()
                        refreshQueueSurface()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateMiniPlayer()
                        refreshLibraryPlaybackIndicator()
                        refreshQueueSurface()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateMiniPlayer()
                        refreshLibraryPlaybackIndicator()
                        refreshQueueSurface()
                    }
                }
                mediaController?.addListener(controllerListener!!)
                updateMiniPlayer()
                refreshQueueSurface()
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }


    private fun refreshLibraryPlaybackIndicator() {
        (supportFragmentManager.findFragmentByTag("library") as? LibraryFragment)
            ?.refreshPlaybackIndicator()
    }

    private fun refreshQueueSurface() {
        (supportFragmentManager.findFragmentByTag("queue") as? QueueFragment)
            ?.refreshQueueState()
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (!hasAudioPermission()) {
            Toast.makeText(this, "Allow music access before starting a new library queue.", Toast.LENGTH_SHORT).show()
            refreshMediaPermissionState()
            return
        }
        val controller = mediaController ?: return
        controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            lifecycleScope.launch {
                try {
                    val songs = MusicScanner.scan(this@MainActivity)
                    if (songs.isNotEmpty()) playQueue(songs, 0)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "Couldn't read the music library.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        PlaybackService.instance?.skipNextDirect() ?: mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (pendingTrack != null) {
            controller.seekToPreviousMediaItem()
            return
        }
        if (controller.currentPosition > 4_000L) controller.seekTo(0L) else controller.seekToPreviousMediaItem()
    }

    fun getQueue(): List<MediaItem> = mediaController?.let { controller ->
        (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it) }
    } ?: emptyList()

    fun currentQueueIndex(): Int = mediaController?.currentMediaItemIndex ?: -1
    fun moveQueueItem(from: Int, to: Int) {
        PlaybackService.instance?.moveQueueItem(from, to) ?: mediaController?.moveMediaItem(from, to)
    }
    fun removeQueueItem(index: Int) {
        PlaybackService.instance?.removeQueueItem(index) ?: mediaController?.removeMediaItem(index)
    }
    fun clearQueue() {
        PlaybackService.instance?.clearQueue() ?: mediaController?.clearMediaItems()
    }

    fun playQueueIndex(index: Int) {
        val item = mediaController?.getMediaItemAt(index) ?: return
        playQueueMediaId(item.mediaId)
    }

    fun playQueueMediaId(mediaId: String) {
        PlaybackService.instance?.playMediaId(mediaId) ?: mediaController?.let { controller ->
            val index = (0 until controller.mediaItemCount).firstOrNull {
                controller.getMediaItemAt(it).mediaId == mediaId
            } ?: return
            controller.seekTo(index, 0L)
            controller.play()
        }
    }

    fun startSleepTimer(durationMs: Long): Boolean {
        val timer = PlaybackService.instance?.sleepTimer ?: return false
        return try { timer.start(durationMs); true } catch (_: Exception) { false }
    }
    fun cancelSleepTimer(): Boolean {
        val timer = PlaybackService.instance?.sleepTimer ?: return false
        return try { timer.cancel(); true } catch (_: Exception) { false }
    }

    override fun onDestroy() {
        controllerListener?.let { mediaController?.removeListener(it) }
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }
}

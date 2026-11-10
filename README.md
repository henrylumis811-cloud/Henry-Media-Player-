# RVH Music

## Latest focused update — v71 (3.54)

The Library shell header is now deterministic on cold launch and after activity resume. The empty pre-commit fragment container is treated as the Library root, and the initial root transaction refreshes the header after commit. This prevents the dead-end back arrow from appearing instead of the app icon and Library menu. The generic overflow glyph is replaced by a dark circular dropdown control with a restrained cyan outline/chevron, ripple feedback, and a subtle open/close rotation. Existing menu actions are unchanged. See `docs_LIBRARY_HEADER_CONSISTENCY_V71.md`; physical cold-launch and navigation testing remains outstanding.

## Previous focused update — v70 (3.53)

The library/queue consistency audit fixed a destructive restoration edge case: when a previously saved non-empty queue contains only tracks that are now missing from MediaStore (for example, deleted files or an unavailable removable volume), restoration no longer substitutes the entire current library. RVH Music now preserves the saved queue's intent and makes only a best-effort restoration of the saved current track from its persisted URI. If that item is also unavailable, the existing safe empty-queue behavior applies. Legacy installations without a queue snapshot retain their compatibility fallback. Queue reconciliation continues to remove missing upcoming items while allowing the currently audible removed track to finish. See `docs_LIBRARY_QUEUE_CONSISTENCY_AUDIT_V70.md`; removable-storage and process-death device tests remain outstanding.

## Latest focused update — v69 (3.52)

The media-session audit corrected the album-art URI supplied to Android system media controls. The previous URI was constructed from the audio media ID, while MediaStore's album-art endpoint is keyed by album ID. Album ID is now retained in the saved current-track snapshot so the process-restoration fallback builds the same correct artwork metadata. When no album ID is available, RVH Music leaves the artwork URI unset so the system can fall back to the app icon. MediaSession command routing and the stable dual-player bridge are unchanged. See `docs_MEDIA_SESSION_ARTWORK_METADATA_AUDIT_V69.md`; physical lock-screen and Bluetooth artwork verification remains outstanding.

## Latest focused update — v68 (3.51)

The sleep-timer expiry path now durably records that its pause action is pending before invoking the playback callback. If the process dies in the narrow interval between reaching the deadline and completing the pause, a same-boot service recreation retries the pending pause. A timer first discovered expired after service downtime is still cleared without retroactively pausing a later listening session. The timer remains service-owned and does not request exact-alarm permission or claim guaranteed execution while Android has stopped the service. See `docs_SLEEP_TIMER_EXPIRY_RECOVERY_V68.md`.

## Latest focused update — v67 (3.50)

The process-restoration pass prevents a failed or permission-blocked MediaStore scan from replacing the saved multi-track queue with a one-track fallback. The durable queue, current track, position and shuffle snapshots remain untouched until a valid scan can rebuild the session. When audio permission is granted while the service is alive, restoration is retried if no queue has been loaded. A genuinely successful empty-library scan retains the existing single-track fallback behavior. See `docs_PROCESS_RESTORE_SNAPSHOT_PRESERVATION_V67.md`.

*A personal Android music player project built around better listening, thoughtful controls, and smooth transitions between songs.*

RVH Music began as an existing Android audio-player project and has grown through a series of focused improvements. The goal is not just to play local music, but to make the everyday listening experience feel polished: a useful library, dependable playback, flexible sound controls, and transitions that feel intentional.

## Latest focused update — v66 (3.49)

The audio-interruption reliability pass gives every Android audio-focus request its own listener generation. Delayed callbacks from an abandoned focus lease are now ignored even if the user has already started a newer playback session and reacquired focus. This closes a narrow race where an old transient-loss callback could otherwise pause a new session, or an old gain callback could affect the wrong session. Explicit pause, permanent focus loss and noisy-output pause continue to clear resume intent. See `docs_AUDIO_INTERRUPTION_LEASE_GUARD_V66.md`. Physical call, notification, navigation and Bluetooth interruption tests are still required.

## Previous focused update — v65 (3.48)

The playback lifecycle pass distinguishes a deliberately saved empty queue from a legacy installation with no queue snapshot, preventing a cleared queue from being rebuilt from the library after service/process recreation. See `docs_PLAYBACK_LIFECYCLE_AUDIT_V65.md`.

## Previous focused update — v64 (3.47)

The audio-output quality pass adds EQ-aware digital headroom: when the Android Equalizer applies positive band gain, RVH lowers the common player gain before the effect to reduce clipping risk. The correction is applied consistently to both internal players and retained through fades/crossfades; disabling EQ restores the prior gain. The audit also confirmed that both players are configured with the same non-zero audio-session ID and matching media/music audio attributes. Bluetooth codec selection, device DSP and actual output quality remain controlled by Android and the connected hardware and require physical testing. See `docs_AUDIO_OUTPUT_QUALITY_AUDIT_V64.md`.

## Previous focused update — v63 (3.46)

The audio-engine resource pass bounds the BPM, beat-grid and ReplayGain caches to 128 tracks and limits pending beat-analysis work to two jobs. See `docs_AUDIO_ENGINE_RESOURCE_AUDIT_V63.md`.

## Previous focused update — v62 (3.45)

The battery/resource pass caps Aura visualizer redraw scheduling at about 30 fps instead of continuously requesting display-refresh-rate frames while audio plays. FFT capture, playback, crossfade behavior and the visual design are unchanged. Existing bounded artwork workers/caches, opt-in serialized beat analysis, and screen/service lifecycle cleanup were audited and retained. See `docs_BATTERY_RESOURCE_AUDIT_V62.md`; physical battery profiling is still required.

## Previous focused update — v57 (3.40)

The shell interaction consistency pass corrects edge-swipe hit testing for scrolled/transformed child controls and prevents a cancelled swipe's return animation from contaminating the next gesture's distance calculation. Existing system-back priority, queue/playlist gestures and navigation design are preserved. See `docs_INTERACTION_CONSISTENCY_AUDIT_V57.md` for scope and validation limits.


Accessibility/readability follow-up: improved font-scale resilience in the Library toolbar and Now Playing action row; raised small status/metadata labels in key playback, lyrics, playlist-picker and Settings surfaces; and introduced theme-specific high-contrast cyan text for Library labels without changing icon/control accent colours. The light-theme text palette was checked against its actual background (the new cyan text colour exceeds WCAG AA for normal text). See `docs_ACCESSIBILITY_READABILITY_AUDIT_V56.md`.

The v55 accessibility pass removes duplicate and misleading TalkBack stops from the Now Playing action rail: navigation destinations are each exposed as one named control, while their decorative icons and captions are hidden from the accessibility tree. Shuffle and Repeat remain independently accessible through their actual buttons rather than inert clickable parent containers. The song-selection checkbox now has a 48dp touch target, and the playback-position and media-volume sliders have explicit accessible names. Decorative mini-player artwork no longer creates an unnecessary screen-reader stop. No navigation cards or navigation redesign were introduced. See `docs_ACCESSIBILITY_INTERACTION_AUDIT_V55.md` for scope and validation limits.

## Latest focused update — v53 (3.36)

The v53 responsive-layout pass makes the Now Playing artwork/ring composition size itself within the available visualizer frame, preserving the existing 250dp/226dp/190dp maximum sizes while allowing the artwork to contract on narrow or short layouts. The Library toolbar now uses 44dp action targets with tighter spacing and a 96dp minimum search width; while multi-select is active, unavailable sort/view/favorites controls are removed from the row so the selection action remains usable on compact screens. No navigation redesign or bottom-navigation cards were introduced. See `docs_UI_RESPONSIVE_AUDIT_V53.md` for scope and validation limits.

## Latest focused update — v52 (3.35)

The v52 interface pass corrects the Now Playing secondary-action row: its six destinations previously had 48dp glyph controls plus 18dp labels inside 52dp containers, allowing the label area to be clipped. The row and its action cells now provide sufficient vertical room without changing the destinations, introducing bottom-navigation cards, or altering the established shell navigation. The Library favorites filter now exposes its active state through an updated accessibility description and selected state. See `docs_UI_AUDIT_V52.md` for scope and validation limits.

## Previous focused update — v51 (3.34)

The v51 audio-focus lifecycle pass closes a stale-callback edge case: a queued focus-gain callback arriving after RVH has abandoned focus (for example, because the user paused during an interruption) can no longer mark the bridge as holding focus. Without this guard, a later Play command could incorrectly skip a fresh AudioManager focus request. Genuine transient interruptions still retain the active lease and can resume normally. See `docs_AUDIO_FOCUS_LIFECYCLE_V51.md` for the exact state handling and validation limits.


The Library, Queue, Playlists and mini-player/Now Playing artwork surfaces now use size-appropriate decoded images: compact 160px thumbnails for dense lists and the existing 512px canonical artwork for larger playback surfaces. The 512px artwork remains persistently cached in app-private storage, while compact variants are generated from that cached bitmap on a worker thread instead of repeatedly extracting embedded art or scaling images during row binding. The memory cache is split between compact and full-size images, uses conservative heap-based limits, and responds to Android memory-pressure callbacks without deleting the persistent disk artwork.

List scrolling no longer globally pauses artwork requests. That old all-or-nothing gate left newly revealed rows blank until a fling ended and then caused a burst of late updates. Requests now continue through the bounded background worker pool while lists move; each list rebinds only its visible rows when it becomes idle to recover work rejected during an unusually heavy fling. Decoded images are prepared for drawing before delivery, RecyclerView view-holder caches and prefetch counts are more conservative for budget devices, and Library selection toggles update only affected rows except when selection mode visibility changes globally.

The change follows Android's documented recommendations to cache decoded images, size bitmaps to their display surfaces, keep disk work off the UI thread, and prepare bitmaps before their first visible draw. See `docs_SCROLL_ARTWORK_PERFORMANCE_V47.md` for implementation details and validation limits. The v51 snapshot was version 3.34 (versionCode 37).

The v48 playback-service pass fixes a task-removal lifecycle hole: if playback is paused but a sleep timer is still counting down, removing the app from Recents no longer explicitly stops the service or delegates to the base task-removal handler that may stop it. This keeps the service-owned timer callback alive for as long as Android allows the service to remain running. It does not claim immunity from OS process reclamation; see `docs_PLAYBACK_SERVICE_RELIABILITY_V48.md` for the exact boundary.

The v49 playback-engine pass adds recovery for failures from either internal ExoPlayer. Failed prebuffer/incoming decoders no longer leave a crossfade waiting indefinitely for READY; the failed item is excluded from automatic advance for the current queue session, while an explicit user selection clears the failure marker and allows retry. If the active decoder fails, RVH advances directly to the next healthy item or pauses and releases audio focus when none remains. See `docs_PLAYBACK_ENGINE_RECOVERY_V49.md` for implementation details and validation limits.

The v50 audio-route/focus pass centralizes `AUDIO_BECOMING_NOISY` handling for the dual-player bridge. A headset or Bluetooth route disconnect now pauses both physical players, cancels an in-flight transition, clears any pending transient-focus auto-resume, and abandons the shared focus lease so music cannot unexpectedly restart through the phone speaker. The pass also resets a stale duck multiplier when focus is abandoned or a fresh focus lease is granted, preventing a later listening session from remaining unexpectedly quiet. See `docs_AUDIO_ROUTE_AND_FOCUS_AUDIT_V50.md` for scope and validation limits.

## The journey so far

- **A working music-player foundation** — built on Android’s Media3/ExoPlayer stack, with a local music library, playback queue, background playback, and system media controls.
- **A more personal listening experience** — added playlists and Favorites, recently played history, listening statistics, a sleep timer, custom visual styling, and persistent preferences.
- **More insight and control** — expanded track information, audio metadata support, embedded-lyrics reading, song trimming, visualizer effects, and sound-processing controls.
- **A distinctive interface** — developed a premium automotive-inspired visual direction, cohesive dialogs, clearer playback surfaces, a dedicated startup experience, and a full-screen Library designed around browsing rather than a large hero. The Library keeps the music list dominant, while a compact bottom mini-player provides the playback entry point.
- **A deeper focus on transitions** — evolved from a basic dual-player crossfade approach toward beat-aware transitions, with Smart Crossfade and a fallback path when reliable beat matching is unavailable. Manual song changes are designed to fade out, switch tracks, and fade back in rather than overlap songs unexpectedly.
- **Ongoing refinement** — the current development snapshot continues to improve transition timing, volume consistency, and playback handover behavior. These details are treated as real listening problems to solve, not as finished just because the code exists.

## Navigation model

RVH Music now uses the **Library as the permanent home screen**. The app no longer presents Now Playing, Queue, Lyrics, Signal, and Playlists as a row of swipeable top-level pages. Instead, they appear contextually:

- Selecting a song starts playback from the Library.
- The persistent mini-player opens the full Now Playing screen.
- Queue and Playlists are opened from Library shortcuts or the header menu.
- Lyrics is reached from the contextual More menu / playback flow.
- Signal & sound controls are available from the header menu.
- Back always returns to the previous contextual screen and ultimately to Library.

This keeps the user's music collection as the center of the app while retaining the existing playback features. Secondary destinations use animated enter/exit transitions, while the Library hero and collection rows use subtle reveal motion to make the home screen feel alive without distracting from music browsing.

## v61 UI direction

The current UI pass moves RVH Music toward a single persistent visual entity rather than a collection of separately branded screens. The RVH Music shell remains constant across Library, Queue, Playlists, Lyrics, Now Playing, and Signal & sound; child surfaces no longer replace that identity with their own large headers.

The Library is now a full-screen browsing surface with no playback hero or embedded live-lyrics panel. The mini-player stays at the bottom and opens Now Playing. Now Playing owns the live-lyrics experience: one transparent, naturally integrated current line rather than a miniature lyrics reader. Full Lyrics remains a separate destination.

## What it includes

- Local audio library and sorting
- Playback queue and background playback with media-session integration
- Playlists and Favorites
- Recently played history and listening statistics
- Sleep timer
- Equalizer and sound controls, including Bass Boost, Vocal Clarity, and Treble Boost, with presets
- Audio visualization
- Embedded lyrics support, ordinary LRC, Enhanced-LRC word timing, and a shared playback-position lyrics timeline
- Track information and audio metadata tools
- Audio trimming tools
- Custom backgrounds and a cohesive, automotive-inspired interface
- Automatic crossfade development with beat-synchronization, Smart Crossfade, and fallback handling
- Manual track changes designed around fade-out / switch / fade-in behavior

Feature availability and behavior can vary by device and audio file. Crossfade in particular remains under active testing and refinement.

## Lyrics synchronization architecture

RVH Music uses one playback-position timeline for both the transparent Now Playing lyric moment and the full Lyrics screen. The renderer does not maintain an independent coarse timer. Timing resolution follows this hierarchy:

1. Enhanced-LRC / word timing when available
2. Line-synchronized lyrics
3. Untimed lyrics with deterministic position-based scrolling
4. Plain embedded/manual lyrics as the final fallback

Seeking, pausing, resuming, and changing tracks re-anchor the lyric state from the player's actual position.

## Playback reliability checks

Before treating a playback change as complete, test these cases on a device:

- Crossfade OFF: manual Next, Previous, queue-row selection, library selection, and MediaSession/notification item selection fade out, switch without overlapping tracks, and fade in.
- Crossfade OFF: automatic end-of-track advance does not start a DJ-style overlap or trigger an early transition.
- Crossfade ON: automatic transitions use beat synchronization when the analyzed grids are sufficiently reliable, otherwise Smart Crossfade/fallback behavior.
- Shuffle and repeat-one/repeat-all remain consistent across UI, notification, Bluetooth, and headset controls.
- Pause, resume, seek, rapid repeated skips, and audio-focus changes never leave a hidden player audible.
- Very short tracks and unreadable/unsupported files recover without leaving playback silent.
- Rapid repeated navigation does not produce a volume jump or count one manual transition twice.

## Current hardening snapshot

The current development snapshot is intentionally conservative: playback transitions, lyrics timing, queue state, and audio processing are treated as shared infrastructure. UI changes should not create separate timing or playback implementations for individual screens.

These are a test checklist, not a claim that every case has already passed. Run the Android build and verify playback on a real device after changes to `DualPlayerBridge`.

## Built with

- **Kotlin** and Android SDK
- **Media3 / ExoPlayer** for playback and media sessions
- AndroidX and Material Components for app UI and lifecycle support
- **jaudiotagger** for audio-tag metadata support
- **GitHub Actions** for automated, signed Android Release APK builds

## Build the app

The GitHub Actions workflow builds and publishes the signed Release APK when changes are pushed to `main`, or when manually dispatched from the Actions tab. It requires four repository Actions secrets: `RVH_UPLOAD_KEY_BASE64`, `RVH_STORE_PASSWORD`, `RVH_KEY_ALIAS`, and `RVH_KEY_PASSWORD`. See [docs/RELEASE_SIGNING_SETUP.md](docs/RELEASE_SIGNING_SETUP.md) for setup instructions.

The workflow creates `app/build/outputs/apk/release/app-release.apk`, verifies its signature, uploads an Actions artifact named `RVH-Music-release-apk`, and publishes `RVH-Music-release.apk` to the rolling `rvh-latest` GitHub Release. The private release keystore is restored temporarily on the CI runner and is never stored in this repository.

RVH Music is built and distributed through the signed **Release** workflow; this project checkpoint documents the production Release path only.

## Project layout

```text
app/src/main/java/com/henrylumis/mediaprayer/
├── audio/       # Playback bridge, beat analysis, normalization, equalizer
├── data/        # Music scanning and song models
├── ui/          # Library, queue, playlists, lyrics, visualizer, dialogs
├── util/        # Preferences, playlists, history, statistics, timers
├── trim/        # Audio trimming
└──              # Activities and playback service
```

## Direction

The work continues in small, deliberate steps: preserve the existing app, improve reliability before adding complexity, and make important controls understandable without sacrificing the visual identity. The crossfade system is a key area of development, with beat-synchronized transitions as the primary goal and graceful alternatives when the audio does not provide enough reliable information.

---

**App name:** RVH Music
**Android application ID:** `com.henrylumis.mediaprayer` (retained for installation and data compatibility)


Latest source checkpoint: RVH Music 3.54 (versionCode 57), deterministic Library header and dropdown polish v71; RVH Music 3.53 (versionCode 56), authoritative saved-queue restoration when all saved media IDs have disappeared v70; RVH Music 3.52 (versionCode 55), MediaSession artwork metadata correction v69; RVH Music 3.51 (versionCode 54), sleep-timer expiry recovery v68; RVH Music 3.50 (versionCode 53), process-restore snapshot preservation v67; RVH Music 3.49 (versionCode 52), audio-focus lease generation guard v66; RVH Music 3.48 (versionCode 51), playback lifecycle and empty-queue restoration v65; RVH Music 3.47 (versionCode 50), EQ-aware output headroom/audio quality pass v64; RVH Music 3.46 (versionCode 49), bounded beat-analysis/cache audio-resource pass v63; RVH Music 3.45 (versionCode 48), Aura 30 fps battery/resource pass v62; RVH Music 3.44 (versionCode 47), storage permission and removable-volume resilience v61; MediaStore change reconciliation and failure-safe library refresh v60; stable-ID queue order and shuffle-history persistence v59; playback/session restoration race guard and Activity/Fragment lifecycle audit v58; dialogs, popups, track-info memory/layout and playlist-picker empty-state pass v54; responsive Now Playing artwork and compact Library toolbar pass v53, Now Playing action-row and Library favorites accessibility pass v52; stale audio-focus callback guard v51 (3.34/code37), audio-route/focus correction v50, dual-decoder error recovery v49 and paused-service sleep-timer lifecycle fix v48; size-aware artwork and scrolling performance pass v47; sleep timer audit v45, release-only configuration audit v44; artwork-pipeline reliability pass v42; playback focus and transition-readiness audit v27 remains a historical checkpoint.


## Latest update — v59 (3.42)

- Persists the actual queue order by stable media ID, rather than rebuilding only the sorted library queue after process death.
- Restores shuffle Previous history and recent-shuffle avoidance history by media identity, safely dropping entries for tracks that are no longer available.
- Keeps repeat and shuffle mode preferences restored before the saved queue is rebuilt.
- Queue reorder/removal now remaps shuffle-history indices to the corresponding tracks; queue replacement and clear reset history belonging to the previous queue.
- Queue reorder and playback-mode changes trigger an immediate playback-state checkpoint, in addition to the periodic checkpoint.
- Release-only version: 3.42 (versionCode 45).


## Latest update — v60 (3.43)

- `MusicScanner` now treats a null MediaStore cursor or missing required column as a failed scan, not a valid empty library; optional/deprecated `DATA` path access is guarded.
- Large scans periodically check coroutine cancellation so superseded refreshes stop promptly and release their cursor.
- Library refreshes are debounced from MediaStore audio change notifications and use generation/cancellation guards so an older scan cannot overwrite a newer snapshot. A failed refresh keeps the last successful library instead of clearing it.
- The playback service observes MediaStore audio changes, scans off the main thread, and prunes queue entries whose media IDs are genuinely gone. The currently audible removed track is retained until playback advances; active crossfade/manual transitions are allowed to finish before queue indices are changed.
- Playlist and shell scan callers now handle transient provider/permission failures without turning persisted playlists or playback into empty states.
- Release-only version: 3.43 (versionCode 46). Device testing is still required for storage-provider notifications, file deletion during playback, and permission revocation.


## Latest update — v61 (3.44)

- `MusicScanner` now checks the applicable runtime audio permission before querying MediaStore and throws an explicit `SecurityException` when access is absent. A denied permission can never be mistaken for an empty library.
- The Library now presents an inline music-access action when permission is revoked or denied. It hides stale rows and disables library actions while access is unavailable, while retaining the last successful in-memory snapshot for recovery.
- Permission state is rechecked when the Activity resumes, so grants/revocations made in Android Settings are reflected without restarting RVH Music. A grant triggers a fresh Library scan and playback-service reconciliation.
- The permission action retries the runtime prompt when Android permits it and opens this app's Settings page after the system stops showing the prompt. Notification permission remains independent of music-library access.
- `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` is documented as the synthetic external-volume query, keeping the existing stable media-ID/URI model while covering indexed mounted shared external storage.
- PlaybackService now listens for removable-volume mount, unmount, eject, removal and shared-storage events as an additional reconciliation trigger. MediaStore ContentObserver remains the primary change signal.
- Permission loss does not clear the active queue or prune tracks based on an inaccessible scan. Starting a new Library queue is blocked until audio access is restored.
- Release-only version: 3.44 (versionCode 47). Physical tests are still required for actual SD-card insertion/ejection, permission revocation/regrant and provider-specific MediaStore timing.


## Latest update — v62 (3.45)

- Aura rendering now schedules at approximately 30 fps rather than requesting a redraw on every display vsync while playback is active. Audio FFT capture and signal analysis are unchanged.
- Audited artwork cache/worker bounds, opt-in beat-analysis scheduling, Now Playing/lyrics lifecycle callbacks, and service/bridge teardown; no unnecessary worker expansion or playback behavior changes were introduced.
- Release-only version: 3.45 (versionCode 48). Device battery, temperature and visual-response profiling remain outstanding.


## Latest update — v63 (3.46)

- Beat-analysis metadata and per-track ReplayGain attenuation are now held in bounded 128-track LRU caches, preventing long listening sessions from retaining every historical media ID indefinitely.
- Optional beat analysis still uses one low-priority worker, but its pending work queue is now bounded to two jobs. Rapid queue changes cannot accumulate an unbounded backlog of stale analysis tasks; rejected requests clear their in-flight marker and can be retried on a later revisit.
- Audio output, EQ curves, ReplayGain gain policy, crossfade timing/curves, playback speed and focus behavior are unchanged.
- Release-only version: 3.46 (versionCode 49). Compilation and physical audio/battery testing remain required.


## Latest update — v64 (3.47)

- Added conservative EQ-aware output headroom. Positive Equalizer band gain now applies a corresponding pre-attenuation in the shared bridge volume layer, reducing the risk of clipping on near-full-scale source peaks. A small additional margin accounts for overlapping filter skirts.
- Headroom is applied to both ExoPlayer instances and is retained through the existing master-volume, ReplayGain, focus and crossfade calculations. Turning the EQ off returns this layer to unity.
- Corrected the ReplayGain code comment to match actual behavior: negative track gain attenuates; positive track gain is not amplified.
- Preserved the shared audio session, media/music audio attributes, decoder path, output routing, crossfade curves and playback-speed behavior. No forced Bluetooth codec, sample rate, offload mode or device-specific DSP was introduced.
- Release-only version: 3.47 (versionCode 50). Static checks only; signed Release compilation and physical output tests remain required.


## Latest update — v65 (3.48)

- Playback restoration now distinguishes an intentionally empty persisted queue from an older installation that predates queue snapshots. Removing the final queued track no longer causes the entire library and previous track to reappear after the playback service/process is recreated.
- Queue snapshot presence is recorded explicitly while preserving backward compatibility: legacy saved states without a queue snapshot continue using the established library-restoration fallback.
- Existing service ownership, paused-on-restore behavior, periodic position checkpoints, queue order, shuffle history, sleep timer deadline handling, and audio-route behavior are unchanged.
- Release-only version: 3.48 (versionCode 51). Source-level validation only; signed Release compilation and physical process-death/service-recreation tests remain required.


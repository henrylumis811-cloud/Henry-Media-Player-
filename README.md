# RVH Music

*A personal Android music player project built around better listening, thoughtful controls, and smooth transitions between songs.*

RVH Music began as an existing Android audio-player project and has grown through a series of focused improvements. The goal is not just to play local music, but to make the everyday listening experience feel polished: a useful library, dependable playback, flexible sound controls, and transitions that feel intentional.

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
- **GitHub Actions** for automated Android debug builds

## Build the app

The repository includes a GitHub Actions workflow that builds a debug APK on pushes and pull requests. To build locally, use a compatible Android SDK, JDK 17, and Gradle 8.7, then run:

```bash
gradle assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

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

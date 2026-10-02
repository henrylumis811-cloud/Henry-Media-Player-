# RVH Music

*A personal Android music player project built around better listening, thoughtful controls, and smooth transitions between songs.*

RVH Music began as an existing Android audio-player project and has grown through a series of focused improvements. The goal is not just to play local music, but to make the everyday listening experience feel polished: a useful library, dependable playback, flexible sound controls, and transitions that feel intentional.

## The journey so far

- **A working music-player foundation** — built on Android’s Media3/ExoPlayer stack, with a local music library, playback queue, background playback, and system media controls.
- **A more personal listening experience** — added playlists and Favorites, recently played history, listening statistics, a sleep timer, custom visual styling, and persistent preferences.
- **More insight and control** — expanded track information, audio metadata support, embedded-lyrics reading, song trimming, visualizer effects, and sound-processing controls.
- **A distinctive interface** — developed a premium automotive-inspired visual direction, glass-style dialogs, clearer playback surfaces, and a dedicated startup experience.
- **A deeper focus on transitions** — evolved from a basic dual-player crossfade approach toward beat-aware transitions, with Smart Crossfade and a fallback path when reliable beat matching is unavailable. Manual song changes are designed to fade out, switch tracks, and fade back in rather than overlap songs unexpectedly.
- **Ongoing refinement** — the current development snapshot continues to improve transition timing, volume consistency, and playback handover behavior. These details are treated as real listening problems to solve, not as finished just because the code exists.

## What it includes

- Local audio library and sorting
- Playback queue and background playback with media-session integration
- Playlists and Favorites
- Recently played history and listening statistics
- Sleep timer
- Equalizer and sound controls, including Bass Boost, Vocal Clarity, and Treble Boost, with presets
- Audio visualization
- Embedded lyrics support and a lyrics display area
- Track information and audio metadata tools
- Audio trimming tools
- Custom backgrounds and a cohesive, automotive-inspired interface
- Automatic crossfade development with beat-synchronization, Smart Crossfade, and fallback handling
- Manual track changes designed around fade-out / switch / fade-in behavior

Feature availability and behavior can vary by device and audio file. Crossfade in particular remains under active testing and refinement.

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

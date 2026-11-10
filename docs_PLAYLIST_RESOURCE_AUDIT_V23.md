# RVH Music UI/resource audit — v23

## Scope
This checkpoint continues the v22 UI/performance audit with a focused lifecycle and memory review of playlist detail artwork loading.

## Changes
- Playlist detail refreshes now close the previous `PlaylistSongAdapter` before replacing it. This covers remove, reorder, add-song and refresh paths that previously replaced an adapter while its artwork executor/cache could remain alive.
- Playlist artwork extraction now uses a single worker and a bounded queue (48 pending jobs) with oldest-work shedding during rapid scrolling. This prevents a fast fling from accumulating an unlimited backlog of metadata extraction tasks.
- Embedded artwork is decoded down to a maximum 512-pixel dimension and RGB_565 for list thumbnails, reducing transient bitmap memory while retaining adequate detail at row size.
- App version advanced to `3.9` / versionCode `12` so this checkpoint can be installed over v22 where signing identity is unchanged.

## Deliberately not changed
- The Fragment `replace()` navigation architecture remains in place. Converting it to retained `show()`/`hide()` or a Navigation Component graph affects gesture-back, Now Playing layering, nested playlist detail and state restoration. It should be treated as a dedicated navigation migration with device testing, not a speculative one-line optimization.
- The 500 ms Now Playing progress refresh remains lifecycle-bound and stops when the screen backgrounds. It updates the visible timeline, not audio playback; changing it requires checking seek accuracy and pending crossfade behavior.
- Equalizer and analytics creation were not changed without runtime profiling evidence.

## Verification
- ZIP integrity checked.
- XML resource parsing checked.
- Static source checks confirm the previous playlist adapter is closed before replacement and the artwork executor has a bounded queue and sampled decode.
- No Gradle/Android SDK build or device frame profiling was available in this environment. GitHub Actions must confirm compilation before treating this as build-verified.

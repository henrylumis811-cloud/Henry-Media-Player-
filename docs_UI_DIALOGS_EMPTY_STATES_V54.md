# UI transient surfaces audit — v54

## Scope

Audited the dialog and popup infrastructure, track-information inspector, playlist song picker, confirmation flows, and the existing Library/Queue/Playlists/Lyrics empty-state surfaces. The established RVH visual language and navigation design are retained. Bottom-navigation cards were not added.

## Changes

### Shared dialog sizing

- Standard styled dialogs now use 90% of the available screen width, capped at 560dp and kept at least 16dp from either screen edge.
- This avoids oversized, desktop-like dialogs on tablets/foldables while preserving the existing phone presentation.
- Existing dim amount, glass background and button tint remain unchanged.

### Context popups

- Context menu labels may wrap to two lines instead of being forcibly clipped to one line.
- Popup width is capped to the available screen width with 16dp side margins.
- Existing row minimum touch height, selection indication, dismissal-before-navigation and glass treatment remain intact.

### Track information dialog

- Corrected programmatic view dimensions and padding that were raw pixels rather than density-independent pixels.
- Metadata inspection uses a bounded two-thread executor instead of an unbounded cached thread pool.
- File inspection uses the application context on the worker thread.
- Embedded artwork is decoded to a thumbnail-sized bitmap rather than decoding the full image into memory for a 112dp preview.

### Playlist song picker empty state

- Added an in-dialog empty-state label for a device with no available songs and for searches with no matches.
- Empty-state visibility is updated from the ListAdapter commit callback, so it follows the actually committed filtered list rather than racing the asynchronous diff.
- Existing-song selection restrictions and the selected-count / Add-button behavior are unchanged.

## Deliberately unchanged

- No bottom-navigation cards or navigation redesign.
- No redesign of Now Playing, Library, Queue or established dialogs.
- No changes to playback, crossfade, lyrics timing, audio focus or sleep timer.
- No changes to release signing secrets or release workflow behavior.

## Validation

- Android resource XML parsing and structural checks performed.
- Kotlin source reviewed for dialog sizing, popup dismissal, adapter commit callbacks and track-info worker/UI thread boundaries.
- Release-only workflow retained (`clean assembleRelease`); no debug build path added.
- Version: 3.37, versionCode 40.
- No Android SDK/Gradle toolchain is available in this workspace. A signed Release APK, emulator rendering, and physical-device dialog testing are therefore not claimed and remain required through GitHub Actions/device verification.

# Artwork restoration after returning to Now Playing — v46

## Reproduction

1. Start a song from Library.
2. Open Now Playing and allow its artwork to appear.
3. Navigate to Queue, Settings, Playlists, Lyrics, or another destination.
4. Return to the existing Now Playing back-stack entry while the same song continues.

## Root cause

`MainActivity.openFragment()` uses `FragmentTransaction.replace()` and adds the transaction to the back stack. When a destination is pushed over Now Playing, Android destroys the Now Playing fragment's **view** while retaining the fragment instance in the back stack. When the user returns, a new view hierarchy and a new album-art `ImageView` are created.

`AltarFragment.lastArtMediaId`, however, was retained on the fragment instance. `onViewCreated()` reconnects the player listener, which calls `updateNowPlaying()` and then `loadAlbumArt()`. The latter returned early when the current `mediaId` equalled `lastArtMediaId`. It therefore skipped applying artwork to the newly created `ImageView`, leaving its XML launcher-icon placeholder visible.

This precisely matches the reported symptom: playback continues, title/artist can refresh, but artwork resets to the app icon after navigating away and back. It is not evidence that the persistent disk artwork cache was erased.

## Fix

Treat `lastArtMediaId` as view-scoped rendered state. Reset it when a new view is created and again during `onDestroyView()`. On re-entry, `updateNowPlaying()` now runs the normal shared artwork path: synchronous memory-cache hit, otherwise the shared foreground loader (which can decode the persistent disk thumbnail without re-extracting the original audio artwork when the disk entry exists).

The fix does not change playback, crossfade, queue state, artwork cache keys, cache persistence, or release signing.

## Validation

- Static regression assertion confirms both lifecycle resets exist.
- Static navigation assertion confirms the existing Now Playing entry is restored from the back stack and its view is recreated.
- XML parsing, ZIP integrity, root-level archive layout, and release version consistency are checked for this checkpoint.
- Android `assembleRelease` and physical-device navigation testing are not available in this workspace and remain required in GitHub Actions/on device.

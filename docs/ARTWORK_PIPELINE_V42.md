# RVH Music v42 — unified, persistent artwork pipeline

Version: 3.26 (versionCode 29)

## Why artwork was still appearing to reload

The previous implementation had a disk cache, but it was not a single source of truth across the app:

- `MainActivity.updateMiniPlayer()` used `ImageView.setImageURI()` for Media3 artwork and fell back to its own `MediaMetadataRetriever` extraction path. This bypassed the shared cache and could reset the mini-player image on routine shell updates.
- `AltarFragment.loadAlbumArt()` independently opened the audio file with `MediaMetadataRetriever`, so Now Playing repeated work that Library and Queue had already done.
- The disk cache silently evicted old thumbnails after 64 MiB, contradicting the intended retain-and-reuse behavior.
- `ArtworkScrollGate` cleared every in-flight request and the entire executor queue at the beginning of a fling. A decode already running could finish and save its thumbnail, but clearing its in-flight entry allowed the idle rebind to start a duplicate extraction before the first one completed.
- A transient URI/permission/decoder failure was recorded as a permanent per-track "no artwork" marker, preventing a later retry.
- Playlist hero artwork was reset to the launcher icon every time its container rerendered, even if the selected song had not changed.

## v42 changes

1. **One shared loader across surfaces.** Added `MediaItem.toArtworkSong()` and routed mini-player and Now Playing through `ArtworkLoader`. The shell no longer uses `setImageURI()` or its own embedded-art extractor.
2. **Persistent artwork is no longer size-pruned.** Successful 512px thumbnails remain in `filesDir/rvh_artwork_v2` until app data is cleared or the app is uninstalled. Removed directory-wide LRU pruning and the 64 MiB cap.
3. **Larger decoded-memory working set.** Increased the memory LRU budget from 1/12 to 1/8 of the app heap to reduce disk JPEG decodes during normal back-and-forth browsing. Disk remains the persistent source of truth; memory can still be evicted by Android/app memory pressure.
4. **Scroll cancellation is request-aware.** A fling removes only queued requests that have not begun. Running extraction is allowed to finish and populate memory/disk, and its callbacks remain protected by each view's identity checks. This prevents the old clear-in-flight-then-restart duplicate extraction race.
5. **Foreground artwork bypasses list-scroll gating.** Mini-player and Now Playing requests use `loadForeground()`, so a list fling cannot leave persistent shell artwork stuck on a placeholder.
6. **Only definitive no-art results are memoized.** An actual absence of embedded picture is recorded. URI access, I/O, and decoder failures remain retryable.
7. **Avoid unnecessary visual resets.** Repeated updates for the same mini-player item do not reset its image; Now Playing uses the same shared cache; playlist hero artwork is retained when the selected song identity is unchanged.
8. **Version updated** to 3.26 / code 29. Playback, crossfade, sleep timer behavior, and visualizer logic were not intentionally changed.

## Cache behavior and limits

- Artwork is keyed by MediaStore album ID, falling back to song ID when album ID is unavailable.
- Successful artwork is stored as a JPEG thumbnail (up to 512 px edge) in app-private files. It survives process death, app restarts, and ordinary app updates.
- The cache intentionally has no automatic size/age eviction to honor the requirement that already-extracted artwork should be retained. Users with very large libraries should be aware that this consumes internal app storage over time.
- Android may still evict decoded bitmaps from RAM. In that case the saved JPEG is decoded again, but the original audio file is not reopened for artwork extraction.
- Clearing app data or uninstalling RVH Music deletes the app-private artwork cache.
- An album's changed embedded cover is not automatically detected yet; an existing album-keyed thumbnail can remain until explicit cache invalidation is added.

## Validation status

- Static checks verify the mini-player and Now Playing no longer contain independent artwork extraction paths and both use `ArtworkLoader`.
- Static checks verify no 64 MiB pruning code remains and the scroll gate no longer clears running in-flight requests.
- XML parsing and ZIP integrity are checked for the delivered archive.
- This workspace does not contain a usable Android SDK/Gradle installation, so Android compilation, release signing, and device performance tests are not claimed. GitHub Actions must pass `clean assembleRelease`; then test repeated fast flings, reverse flings, screen changes while music is playing, queue/playlist artwork, no-art tracks, and process restart on the target phone.

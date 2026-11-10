# RVH Music 3.15 — Shared Artwork Pipeline (v31)

## Scope
Focused thumbnail-loading improvement. No playback engine, crossfade, navigation structure, screen layout, or Now Playing design changes.

## Changes
- Added `ui/common/ArtworkLoader`, a process-wide artwork extraction and bitmap cache.
- Coalesces concurrent requests for the same album/song key so one embedded-art extraction serves multiple visible rows and screens.
- Uses three background workers; `MediaMetadataRetriever` is created and released inside the worker that owns it.
- Decodes embedded artwork to a maximum 512px dimension using sampled RGB_565 bitmaps.
- Uses a shared LruCache sized by bitmap memory (KiB), rather than per-adapter caches whose combined budgets could grow unpredictably.
- Delivers results on the main thread. Each adapter validates the bound song/group/playlist identity before applying a result, protecting recycled rows from stale images.
- Replaced separate loaders in SongAdapter, LibraryGroupAdapter, PlaylistSongAdapter, PlaylistPickerAdapter, and the playlist catalogue/header artwork paths.
- Adapter `close()` methods no longer shut down shared workers or clear shared cache while another screen may still need them.

## Expected behaviour
- Fast scrolling no longer loses queued artwork requests due to per-adapter `DiscardOldestPolicy` executors.
- Repeated album requests share one extraction and one cached bitmap.
- Visible rows retain placeholder artwork until the matching image is ready; stale callbacks cannot overwrite a recycled row.
- The cache is shared across library and playlist screens, improving reuse when navigating between them.

## Verification performed
- Source-level inspection of all modified adapters and loader.
- Confirmed the modified scrolling adapters no longer contain independent `DiscardOldestPolicy` artwork executors or duplicate `MediaMetadataRetriever` extraction code.
- Confirmed release version incremented to versionCode 18 / versionName 3.15.
- ZIP structure and integrity checked after packaging.

## Runtime checks still required
GitHub Actions must compile the Android project. On-device testing should include fast repeated flings through Songs, Albums, Artists, playlist catalogue, playlist detail, and Add Songs; verify no wrong-row artwork, no persistent placeholders after scrolling stops, and acceptable memory/scroll performance with a large library. This source-level change is not a claim of device profiling or successful compilation.

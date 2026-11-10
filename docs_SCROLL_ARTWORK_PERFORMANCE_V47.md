# RVH Music v47 — Scroll and artwork performance implementation

## Scope

This update implements the v46 audit recommendations for the custom local-artwork pipeline. It keeps the existing `MediaMetadataRetriever` extraction path and permanent app-private artwork cache; it does not replace the loader with a network-oriented image library.

## Changes

### 1. Size-aware artwork delivery

- The persistent canonical artwork remains capped at 512px on its longest edge and continues to use the existing `rvh_artwork_v2/<album-or-track-id>.jpg` files. Existing cached files remain readable; no cache migration or deletion is required.
- Dense list surfaces request 160px variants. They are derived from the canonical bitmap on the artwork worker thread, cached separately in memory, and never resized in `onBindViewHolder`.
- The mini-player uses the compact variant because its artwork view is small. Now Playing and the playlist hero request the full 512px variant.
- A shared in-flight track request can serve both compact and foreground callbacks, avoiding a second metadata extraction when two surfaces request the same track at nearly the same time.

### 2. Scrolling behavior

- Removed the global scroll pause. It previously prevented all non-foreground disk/decode work while any attached list was moving, causing visible rows to remain placeholders until scrolling stopped.
- Artwork extraction and disk reads continue on the bounded two-thread worker pool during a fling.
- The queue remains bounded at 48 waiting requests. If an unusually large fling causes rejection, callbacks are retried with a short delay up to three times; when a list reaches idle, only its visible rows are rebound as a final recovery path. This also protects foreground requests from being silently lost during queue saturation.
- Rebinding is per RecyclerView rather than globally coordinated, so one idle list is not prevented from recovering just because another list is still moving.

### 3. Memory and rendering

- Full-size and compact decoded bitmaps have separate LRU memory caches, each capped relative to the app heap with upper limits. Persistent disk artwork is unaffected by memory trimming.
- `MediaPrayerApp.onTrimMemory()` trims or clears decoded bitmap caches at the corresponding Android memory-pressure levels.
- `Bitmap.prepareToDraw()` is invoked after decode/disk read, and compact variants are created before callbacks are posted to the main thread.
- RecyclerView view-holder cache sizes and initial prefetch counts were reduced. View-holder caching is distinct from bitmap caching; this reduces retained row views without treating them as artwork storage.

### 4. Adapter work

- Library selection toggles now refresh the changed row while selection mode remains active. A full visible-range refresh is retained when entering or leaving selection mode because checkbox visibility changes for all rows.

## Compatibility

- The package/application ID, Media3 playback implementation, artwork disk-cache directory and filename convention, permanent disk retention behavior, release signing configuration and GitHub Actions release workflow are unchanged.
- The release version is now 3.30 / versionCode 33.

## Validation performed in this workspace

- Reviewed all `ArtworkLoader` call sites to separate compact list peeks from full-size foreground peeks.
- Checked that compact bitmap scaling happens on the worker thread and that the main-thread callback only assigns the already-prepared bitmap.
- Checked the artwork gate for stale global pause/resume references and confirmed that none remain.
- Checked queue-rejection recovery to ensure every callback coalesced into a rejected request is retried, not just the first caller.
- Checked the Android manifest still registers `MediaPrayerApp`, so memory callbacks reach the loader.
- XML parsing, workflow YAML parsing, ZIP integrity and release-only workflow checks are performed on the final archive.

## Validation limitations

This workspace does not contain an Android SDK or Gradle executable/wrapper. Therefore this update has **not** been compiled into a Release APK or tested on a physical phone. Smoothness, frame timing, memory use, and actual disk-cache hit behavior still require release-build/device profiling. The 160px/512px split and cache limits are reasoned defaults, not device-measured tuning results.

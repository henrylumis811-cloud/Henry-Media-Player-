# RVH Music v37 — scrolling and artwork pipeline audit

Version: 3.21 (versionCode 24)

## Findings from v36 source inspection

1. `ArtworkLoader.pruneDiskCache()` listed and sorted the entire cache directory after every artwork request. That turns a growing disk cache into repeated directory I/O during library browsing and is a credible source of scroll stutter.
2. Several adapters reset artwork to the launcher icon during binds even when the same item was being rebound or its bitmap was already in the memory cache. This creates visible thumbnail flashes and makes cached artwork appear to reload.
3. Queue rows bypassed the shared artwork cache and called `ImageView.setImageURI()` during row binding. URI decoding could therefore happen on the UI thread while scrolling.
4. Library, playlist and queue RecyclerViews did not share consistent row-reuse/prefetch settings. Change animations could also make visible artwork rebinds look like flicker.
5. Negative artwork-cache entries were album-keyed. A track without embedded art could incorrectly mark the whole album as artworkless even if another track in that album contained a cover.

## Changes in v37

- Keep the two-level memory + persistent disk cache.
- Expose a memory-only `peek()` so adapters can apply a cached bitmap before deciding to show a placeholder.
- Preserve existing row artwork when a row is rebound for the same item and cache memory has been evicted.
- Prune the disk cache only after batches of 16 successful thumbnail writes, not on every load.
- Store artworkless markers per track ID, not per album ID, so one artworkless track cannot suppress another track's cover.
- Route queue artwork through the shared background decoder/cache instead of `ImageView.setImageURI()` in `onBindViewHolder`.
- Carry `album_id` in MediaItem extras so queue rows use the same album cache key as library rows.
- Apply RecyclerView row reuse, prefetch, and change-animation tuning to Library, Playlists, picker, and Queue lists.
- Preserve the existing scroll gate: memory hits remain immediate; expensive cache/decode work waits until lists settle; only visible rows are rebound on idle.
- Guard asynchronous callbacks against recycled/rebound holders using item identity.

## Validation and limits

- Source-level inspection and targeted consistency checks only; no Android SDK/Gradle build is available in this workspace.
- No device frame-time benchmark or real-device fling test has been performed.
- GitHub Actions must confirm `clean assembleRelease` before this version is considered build-verified.
- Device testing should compare fast flings, reverse scrolling over already-seen rows, queue scrolling, and first-time artwork loading.

# RVH Music — Artwork Memory & Scroll Audit (v24)

## Scope
A focused audit of artwork loading in Library, Albums/Artists, Playlist catalog, Add Songs picker, and Playlist detail. This is a resource/performance correction, not a visual redesign.

## Findings and changes

1. **Artwork cache units were incorrect.** Several `LruCache` instances used the default entry-count sizing while their maximum was calculated from available memory in KiB. That did not impose a real bitmap-memory limit. Caches now measure each bitmap by `byteCount / 1024` and have explicit KiB budgets.
2. **Library and picker artwork queues were unbounded.** `Executors.newFixedThreadPool()` uses an unbounded work queue. Fast scrolling through large libraries could retain many obsolete metadata-extraction jobs. Replaced with fixed-size `ThreadPoolExecutor`s and bounded queues using a discard-oldest policy.
3. **Artwork was decoded at source resolution.** Library rows, groups, picker rows, and playlist catalog artwork now inspect dimensions first, downsample to a maximum 512px edge, and prefer RGB_565 for these thumbnail surfaces. Playlist detail already had downsampling; its cache now measures actual bitmap memory.
4. **Albums/Artists adapter was not closed with Library's view.** Added explicit executor shutdown, callback removal, and cache eviction in `LibraryFragment.onDestroyView()`.
5. **Playlist header artwork work outlived its view.** Moved it to `viewLifecycleOwner.lifecycleScope` and captured application context before dispatching IO, so destroying the view cancels the work cleanly.
6. **Playlist catalog and Add Songs picker work is bounded.** Catalog artwork uses one worker and a bounded queue; picker uses two workers and a bounded queue. Their existing close paths now also benefit from correctly sized caches.

## Expected user-visible impact
- Less memory retained by album artwork, especially on devices with limited RAM.
- Fewer stale artwork jobs competing with visible rows during fast scrolls.
- Lower chance of memory pressure and UI hitching when switching between Library and playlist screens.
- No intended changes to playback, crossfade, queue ordering, or the settled Now Playing design.

## Verification status
- Static source checks: performed.
- XML parsing: performed.
- ZIP integrity: performed.
- Android Gradle compilation and device profiling: not performed in this environment; GitHub Actions and real-device testing remain required.

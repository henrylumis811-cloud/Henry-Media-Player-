# RVH Music v36 — Persistent artwork cache

## Why artwork appeared to reload
The previous shared loader cached decoded thumbnails only in an in-memory `LruCache`. That cache is temporary and can evict entries under memory pressure or disappear when the app process is recreated. In addition, the Library and playlist adapters reset artwork views to the app icon on every bind, so a row could visibly flash its placeholder even when its thumbnail was already cached.

## Changes
- Added an app-private disk thumbnail cache in `filesDir/rvh_artwork_v2`, retained across app restarts and normal app updates.
- Kept the in-memory LRU cache for instant repeat binds; disk hits are decoded off the main thread and promoted into memory.
- Persisted 512px-or-smaller JPEG thumbnails, using RGB_565 decode, with a 64 MiB least-recently-used disk limit.
- Coalesced concurrent requests for the same album as before.
- Remembered albums with no embedded artwork so every track row does not repeatedly invoke `MediaMetadataRetriever` for the same empty album.
- Preserved existing artwork when the same RecyclerView holder is rebound for the same song/group, rather than resetting it to the app icon on every bind.
- Applied the placeholder-preservation fix to Library songs, Library groups, playlist songs, and playlist-picker rows.
- Kept scroll-aware request gating. Requests are still deferred while lists move; visible rows are rebound when scrolling settles and can now be served from memory or disk.

## Cache behaviour and limitations
- The cache survives process death and app updates; uninstalling the app or clearing app data removes it.
- The disk cache is bounded to approximately 64 MiB and can prune older thumbnails.
- Artwork is keyed by MediaStore album ID, falling back to song ID when no album ID exists.
- A changed embedded cover for an existing album may remain cached until app data/cache is cleared or a future explicit invalidation is added.
- Device-side smoothness and memory impact still require testing on the target phone. No Android build or frame-rate benchmark is claimed by this source-only check.

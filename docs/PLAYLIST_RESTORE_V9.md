# RVH Music — Playlist Restoration v9

Restored on top of `RVH_Music_lyrics_refined_v8.zip`.

## Restored foundation
- Playlist catalog artwork identity using first playable song artwork.
- Playlist detail header with artwork, name, song count, and unavailable count.
- Cleaner 56dp artwork-based song rows.
- Play All, Add Songs, Shuffle, and playlist menu controls.
- Shuffle plays a shuffled copy without changing saved playlist order.
- Unavailable playlist entries are preserved in PlaylistStore and reported in the detail header.
- Swipe-left removal replaces the old permanent delete button.
- Drag handle remains available for explicit playlist reordering.
- Playlist song artwork loads off the main thread and is guarded against RecyclerView recycling.
- Playlist artwork loading is guarded against stale async results after changing playlists.
- Playlist song adapter releases its artwork executor/cache with the fragment view lifecycle.

## Deliberate next steps
The Add Songs button is intentionally a temporary bridge until the dedicated v10 picker is restored. The next checkpoint should implement the multi-select Add Songs picker rather than inventing a second picker here.

## Verification
- Playlist XML files parse successfully.
- Source archive is structurally valid.
- Android/Gradle compilation was not run because a Gradle executable/wrapper is unavailable in this environment.

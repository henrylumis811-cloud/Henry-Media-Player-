# Media-library change and active-queue audit — v60 (3.43)

## Findings

1. A null MediaStore cursor was previously treated as a successful empty scan. A transient provider failure could therefore clear a valid Library snapshot or be mistaken for an empty device.
2. Concurrent Library scans had no generation ownership; an older query could finish later and replace a newer result.
3. The Library did not observe MediaStore audio changes, so external additions, removals, and metadata edits could remain invisible until a manual refresh.
4. The playback queue had no live reconciliation path for tracks removed from MediaStore while the service remained active.
5. Playlist and shell scan call sites did not consistently handle provider/permission failures after the scanner began distinguishing failure from an empty result.

## Changes

- `MusicScanner.scan` now requires a non-null cursor and validates required columns. `DATA` remains optional because playback uses MediaStore content URIs and the raw path is deprecated/unavailable in some storage contexts.
- Scans check coroutine cancellation every 128 rows and after cursor completion.
- Library audio notifications are debounced by 650 ms. Scan generations prevent stale results from committing; a failed scan retains the last known-good song list.
- The playback service observes external audio changes and performs a debounced scan on IO. Only a successful scan can mark queue IDs as removed.
- Missing upcoming queue items are removed by stable media ID. A currently audible track that disappears from MediaStore is retained until playback moves away from it, avoiding an abrupt stop. Queue pruning waits for crossfade/manual transitions to finish, so queue indices are not mutated during a transition.
- If a removed track becomes available again before pruning, its pending removal marker is cleared.
- Playlist and MainActivity scan callers now handle cancellation separately and report transient scan errors without crashing or replacing persisted state with empty data.

## Deliberately unchanged

- No playback URI or metadata snapshot is replaced while a decoder is actively using it. Metadata edits become visible in the refreshed Library; an already-loaded queue item keeps its original metadata snapshot until that queue is rebuilt.
- No attempt is made to delete files from storage.
- Queue order, current position, shuffle/repeat state, crossfade curves, artwork cache, and navigation design are unchanged.
- No bottom-navigation cards or unrelated UI redesigns.

## Verification performed

- Reviewed every `MusicScanner.scan` call site and added explicit failure/cancellation handling.
- Checked queue pruning uses stable media IDs and descending indices, while excluding the active current item.
- Checked pruning is deferred during crossfade/manual transitions and retried after the transition window.
- Checked observer registration/unregistration and pending callbacks/jobs are cleaned up with service/fragment lifecycle.
- Confirmed versionName 3.43 and versionCode 46; workflow remains signed Release-only (`assembleRelease`).
- XML resource parsing, ZIP integrity, archive-root layout, secret/keystore exclusion, and whitespace checks are recorded in the delivery validation.

## Device tests still required

1. Add an audio file while Library is visible; confirm one refresh after the notification burst.
2. Rename/edit tags externally and confirm the Library reflects the metadata after refresh.
3. Delete an upcoming queue track; confirm it disappears without interrupting the current song.
4. Delete the currently playing track; confirm playback is not forcibly stopped, then confirm the removed item is pruned after advancing.
5. Remove a file during an active crossfade/manual fade; confirm the transition completes before pruning.
6. Revoke audio permission during a scan; confirm no empty-library commit occurs and playback is not pruned.
7. Simulate a provider query failure/null cursor; confirm the last successful Library and queue remain intact.
8. Restore permission and rescan; confirm newly visible media is included in the Library.
9. Edit tags on a queued track; confirm the Library updates, while noting that the already-loaded queue snapshot is intentionally not mutated mid-playback.

Android SDK/Gradle are not available in this workspace, so no signed Release APK compilation or physical-device run is claimed.

# Library and queue consistency audit — v70 (3.53)

## Scope

Reviewed MediaStore scanning, active queue reconciliation, saved queue restoration, playlist references, stable media IDs, and metadata snapshot behavior.

## Finding and correction

A saved non-empty queue could be silently replaced by the entire current library after process death if every ID in the saved queue had disappeared from a successful MediaStore scan. The restoration code filtered unavailable IDs, then treated an empty filtered result like a legacy state and selected all current library items. This violated the persisted queue snapshot and could make unrelated tracks appear after files were deleted or removable storage was unavailable.

For v70, a non-empty, explicitly persisted queue is now authoritative. If none of its IDs resolve in the current MediaStore scan, restoration makes only a best-effort attempt to restore the saved current item from its persisted URI. If that URI is no longer playable, the existing safe failure behavior leaves playback empty; it does not manufacture a new all-library queue. Legacy states without a queue snapshot retain the compatibility fallback. Explicitly saved empty queues continue to return without rebuilding playback.

## Existing behavior confirmed and preserved

- MediaStore scan failures, permission loss, and null cursors do not trigger queue pruning.
- Successful scans identify missing IDs; the active current item is retained until playback advances.
- Missing upcoming items are pruned after active transitions, using descending queue indices.
- IDs that reappear before pruning have their pending-removal markers cleared.
- Playlist entries whose files are unavailable remain represented as unavailable rather than being silently deleted from the user's playlist.
- Playlist reorder operations preserve unavailable IDs in their original slots.
- MediaStore metadata changes refresh the Library, while already-loaded queue items intentionally retain their metadata snapshot until the queue is rebuilt; no live metadata mutation is attempted against an active decoder.
- Stable MediaStore media IDs remain the queue and playlist identity.

## Deliberately unchanged

No new deduplication heuristic was added based on title, artist, path, or duration: different files can legitimately share those values. No file is deleted from storage. No automatic removal of unavailable playlist references was introduced. Playback transitions, shuffle/repeat, artwork caching, navigation, and audio processing are unchanged. No bottom-navigation cards or unrelated UI redesigns were introduced.

## Static verification

- Confirmed the new guard applies only when a queue snapshot exists, the saved queue was non-empty, and zero saved IDs resolve in the successful current scan.
- Confirmed partial matches still restore the surviving saved IDs in their saved order.
- Confirmed legacy states without a queue snapshot still use the compatibility library fallback.
- Confirmed explicitly saved empty queues are still handled by the pre-existing early return.
- Confirmed release versionName 3.53 and versionCode 56; workflow remains Release-only and signing secret names are unchanged.
- XML parsing, whitespace, archive integrity, and root-level ZIP checks are part of delivery validation.

## Device tests still required

1. Save a queue, remove all its files, force-stop the process, then relaunch; confirm no unrelated library queue appears.
2. Save a queue, remove only some files, then relaunch; confirm surviving tracks retain their saved order.
3. Remove the current track while it is playing; confirm it is allowed to finish and is pruned after advancing.
4. Eject and remount an indexed SD card; confirm transient unavailability does not permanently prune entries.
5. Rename/edit tags on a queued file; confirm Library metadata refreshes and note that the existing queue item's metadata snapshot remains stable until rebuilt.
6. Confirm unavailable playlist entries remain visible as unavailable and retain their relative order after reordering playable entries.

Android SDK/Gradle are not available in this workspace, so no signed Release compilation or physical-device tests are claimed.

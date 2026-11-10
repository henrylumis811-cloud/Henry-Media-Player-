# Queue persistence and shuffle continuity — v59 (3.42)

## Problem
The prior process-death restore rebuilt a sorted library queue around the saved current track. This restored the song and position but lost a listener's custom queue order. Shuffle Previous history and recent-shuffle avoidance history were also memory-only.

## Changes
- `PlaybackStateStore` now stores queue media IDs and both shuffle-history sequences as JSON string arrays alongside the existing current-track checkpoint.
- The service resolves those IDs against the freshly scanned library, preserves saved order, filters unavailable tracks and deduplicates malformed/repeated IDs. If the saved current track is absent from the saved queue but remains in the library, it is retained without discarding the other restored queue entries.
- Shuffle history is stored by stable media ID, never raw queue index. The bridge maps those IDs to indices after restoring the queue.
- Queue moves remap both in-memory shuffle histories to preserve the same track identities. Removing a queue item removes its history entries and shifts later indices. Replacing/clearing a queue resets history associated with the prior queue.
- Reordering and shuffle/repeat mode changes request an immediate state checkpoint. If the queue becomes empty, a queue-only checkpoint clears the obsolete saved queue without deleting the last-track resume metadata. Existing five-second periodic saving remains as a crash-safety backstop.
- Shuffle/repeat preferences continue to be restored before queue reconstruction.

## Compatibility and fallback
Older saved states without queue arrays load with empty lists and retain the prior sorted-library restoration fallback. Malformed JSON arrays are treated as empty. Tracks removed from the device are skipped. The single-track fallback remains available when the library scan is empty or fails.

## Validation limits
Source-level checks cover persistence fields, stable-ID remapping, queue mutation handling, and release-only workflow configuration. Android SDK/Gradle are not available in this workspace, so no Kotlin/Android compilation, signed Release APK, process-kill test, or device playback test is claimed. Those require GitHub Actions and a physical Android test.

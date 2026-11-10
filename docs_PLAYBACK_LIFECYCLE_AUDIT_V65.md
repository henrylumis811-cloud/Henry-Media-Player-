# RVH Music v65 — Playback lifecycle audit

Version: 3.48
Version code: 51

## Scope reviewed

Reviewed `PlaybackService`, `DualPlayerBridge`, `PlaybackStateStore`, `SleepTimer`, the MediaSession service declaration, and the existing v48/v58/v59 lifecycle decisions. Focus areas were service/process recreation, persisted queue semantics, task removal, paused restoration, interruption cleanup, and long-running callbacks.

## Defect corrected: empty queue was indistinguishable from legacy state

The v59 persistence layer stored queue IDs as a JSON array. An empty array was also the default returned when the key did not exist on older installations. During restore, both cases entered the compatibility path that rebuilt the full library queue.

If the user removed the final queue item, `PlaybackService.savePlaybackState()` wrote an empty queue snapshot while retaining the last-track resume metadata. After service/process recreation, restore interpreted the empty snapshot as an old pre-v59 state and rebuilt the library. That could resurrect a queue the user had deliberately emptied.

### v65 correction

- Persist an explicit `queue_snapshot_present` marker whenever a queue snapshot is written.
- Expose snapshot presence in `SavedPlayback`, retaining a compatibility check for installations where the queue key exists but the marker does not.
- During startup restore, if a queue snapshot exists and contains no media IDs, leave the bridge empty. Do not rebuild the library or restore the previous current track.
- Legacy saved states with no queue snapshot continue using the existing full-library fallback.
- Non-empty saved queues continue restoring exact stable-ID order and shuffle history.

## Existing lifecycle behavior confirmed by source inspection

- `PlaybackService` owns the `MediaSession`, `DualPlayerBridge`, Equalizer, sleep timer, media observers and periodic state checkpoints.
- Activity/Fragment lifetime is separate from playback-engine lifetime.
- The service returns `START_STICKY` for unexpected service/process recreation; restoration reconstructs state paused, requiring an explicit Play action.
- The v58 generation guard cancels stale asynchronous restores when a newer queue selection occurs.
- The v59 stable-ID queue and shuffle-history snapshots are retained for non-empty queues.
- The v48 task-removal branch avoids stopping a paused service while its service-owned sleep timer is active.
- `onDestroy` removes handler callbacks, unregisters observers/receivers, cancels the service scope, disposes the timer ticker, releases effects/session and releases both players.
- Headphone/Bluetooth disconnect handling remains owned by the bridge; no automatic resume policy was changed in this pass.

## Platform limits and outstanding device tests

A service-owned Handler countdown cannot guarantee that a sleep timer fires if Android reclaims the process while the service is non-foreground/paused. The persisted same-boot monotonic deadline can be evaluated when the service is recreated, but it cannot execute while the process is absent. Force-stop and reboot also have distinct Android semantics. No exact-alarm permission or new background execution mechanism was introduced.

Required physical tests:

1. Remove the final queued track, force-stop/relaunch, and confirm no old queue returns.
2. Upgrade from a pre-v59 saved state and confirm the compatibility fallback still restores the library.
3. Kill the process while playing; relaunch and confirm the queue/track/position restore paused, then Play resumes.
4. Remove the app task during active playback and during a paused active sleep timer.
5. Test calls, notification interruptions, Bluetooth disconnect/reconnect, and deliberate pause before focus returns.
6. Leave playback in background for an extended period and inspect service survival, audio continuity, battery use and timer behavior.

## Validation limits

This is a source-level audit. Android SDK/Gradle are unavailable in the workspace, so no Kotlin compilation, signed Release APK, emulator test, process-kill test, or physical-device test is claimed.

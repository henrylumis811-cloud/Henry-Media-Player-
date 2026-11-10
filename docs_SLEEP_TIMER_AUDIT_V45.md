# Sleep Timer Audit — v45

Version 3.28 (versionCode 31). Release-build workflow only; the configured GitHub release-signing secrets and release pipeline are not changed.

## Existing behavior verified in source
- User-selected duration is custom, from 1 minute through 23 hours 59 minutes; zero is rejected.
- The active deadline uses `SystemClock.elapsedRealtime()`, so wall-clock/time-zone changes do not alter the countdown.
- Deadline, boot count and original selected duration are persisted. Restore is accepted only when both boot counts are valid and equal; unavailable boot identity is not treated as same-boot proof.
- The timer is owned by `PlaybackService`, not by the Settings fragment. Leaving the screen does not cancel it.
- A one-second service-side tick updates state and invokes the expiry callback once; expiry clears the timer state before pausing the playback bridge.
- The Settings screen separately renders a one-second countdown, restores the active original duration, remembers the last selection, and removes its UI callbacks in `onDestroyView`.
- Timer expiry pauses through the playback bridge and does not directly manipulate crossfade state.
- Service destruction removes the in-memory ticker but leaves the persisted same-boot deadline available for restoration.

## Defect found and corrected
Previously, `cancel()` ignored the Boolean result of `SharedPreferences.commit()`. The UI could therefore report a successful cancellation even when durable removal failed, allowing the old deadline to reappear after service/process recreation. Cancellation now checks the synchronous commit result before stopping the live ticker or clearing in-memory state. On failure it reports that cancellation did not complete and leaves the running timer intact. The Settings message was changed so it no longer incorrectly blames service unavailability for every cancellation failure.

## Important platform/lifecycle limitation (not hidden)
This is not an independent OS alarm. The countdown callback runs inside `PlaybackService`. If Android kills/stops that service, the deadline can be restored when the service is recreated, but the timer cannot guarantee an exact-deadline pause while the service is absent. A timer restored after its deadline has already passed is cleared; restoration does not retroactively invoke a pause. This behavior is intentionally documented rather than represented as guaranteed background execution.

`onTaskRemoved()` stops the service when playback is already paused or the queue is empty. In that situation there is no active audio to stop at expiry; if the service is later recreated, an unexpired same-boot timer can be restored. If playback is active when the task is removed, the media service remains responsible for playback and timer callbacks.

## Release validation
- Source audit covered `SleepTimer`, `PlaybackService`, `MainActivity`, `SignalFragment`, the Settings layout, manifest, and the release workflow.
- Verified custom range, monotonic deadline, boot identity validation, synchronous start persistence, cancellation result handling, expiry ordering, service ownership, UI callback cleanup, and crossfade isolation.
- Android release compilation, actual signing with the user's GitHub secrets, and physical-device timing/lifecycle tests are not claimed here; those require the user's GitHub Actions run and device.

# Playback Service Reliability Pass — v48

Version 3.31 (versionCode 34). Release-only project configuration and existing GitHub signing secrets/workflow are preserved.

## Focused lifecycle defect corrected

`PlaybackService.onTaskRemoved()` previously stopped the service whenever playback was paused or the queue was empty. Because `SleepTimer` is owned by that service and its countdown callback runs on the service's main-thread Handler, removing the app task during an active timer could destroy the only live callback before the timer expired.

The task-removed path now checks both playback intent and timer state. When playback is not active but a sleep timer is running, it deliberately avoids both `stopSelf()` and the base `MediaSessionService.onTaskRemoved()` handling so the service can keep the timer callback alive. Normal task-removal handling remains unchanged when no timer is active, and active playback continues through the normal MediaSessionService path.

## Remaining platform limitation

This is a lifecycle correction, not an independent OS alarm. A paused service kept alive after task removal is still not guaranteed to survive arbitrary Android process reclamation, force-stop, reboot, or vendor-specific background restrictions. The same-boot persisted monotonic deadline can be restored if the service is recreated, but an expired deadline discovered during restoration is cleared rather than retroactively pausing playback. No exact-alarm permission or extra user-facing notification was introduced.

## Related timer behavior rechecked

- Duration remains 1 minute through 23 hours 59 minutes.
- Countdown remains based on `SystemClock.elapsedRealtime()`.
- Restore requires valid matching boot counts.
- Start and cancellation use synchronous preference commits and report failure.
- Expiry clears in-memory state before invoking the pause callback.
- The timer pauses through `DualPlayerBridge` and does not manipulate crossfade state.
- Settings UI callbacks are removed with the view lifecycle.

## Validation limits

Source-level checks cover the changed lifecycle branch, timer ownership, duration bounds, persistence ordering, and release-only version/workflow configuration. Android/Kotlin compilation, signed Release APK creation, process-reclamation tests, and physical-device timing tests have not been run in this workspace; those require the configured GitHub Actions release build and a device.

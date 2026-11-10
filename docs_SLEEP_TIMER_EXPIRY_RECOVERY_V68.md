# Sleep Timer Expiry Recovery — v68

Version 3.51 (versionCode 54). Release-only build configuration and signing workflow are unchanged.

## Finding

The service-owned sleep timer previously cleared its durable deadline before invoking the playback pause callback. A process death in that narrow interval could lose the expiry action: the timer would already appear completed, but playback might not yet have been paused.

## Change

- Added a durable `expiry_action_pending_v42` marker.
- When the live countdown reaches zero, the timer commits the pending marker before invoking `onFire`.
- If the process is recreated in the same boot after that marker was committed, restoration schedules the pending pause callback again.
- Successful cancellation and normal state clearing remove the marker alongside the deadline, boot count and selected duration.
- If clearing the marker fails after the callback, it remains durable; replaying a pause on a later same-boot recreation is safe.
- A timer whose deadline is merely found expired after service downtime, without the pending-action marker, is still cleared without retroactively pausing playback. This avoids unexpectedly pausing a newer listening session.

## Preserved behavior and platform boundary

- Monotonic `elapsedRealtime()` deadlines and boot-count validation remain in place.
- Custom duration limits remain 1 minute through 23 hours 59 minutes.
- The timer remains owned by `PlaybackService`; leaving the UI does not cancel it.
- No exact-alarm permission, wakeup alarm, or extra always-on service was introduced.
- Android may delay or prevent callback execution if it stops/reclaims the service. This is not an OS-scheduled exact alarm and does not guarantee a pause at the deadline while the service is absent.
- Timer expiry still pauses through the playback bridge and does not manipulate crossfade state.

## Validation

Static source checks cover: pending-marker persistence before callback, same-boot validation before replay, marker cleanup on cancellation/clear, and preservation of the no-retroactive-pause path. Android compilation, signed Release build, process-kill race testing and physical-device doze/battery tests are not claimed; these require GitHub Actions and a device.

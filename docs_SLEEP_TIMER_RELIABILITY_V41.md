# Sleep Timer Reliability Pass — v41

Version 3.25 (versionCode 28). This is a focused timer-only change; playback, crossfade, queue, and audio processing code are not intentionally changed.

## Changes
- A boot count of `-1` (or any unavailable/negative value) is never treated as a valid boot identity. Persisted monotonic deadlines are restored only when both saved and current boot counts are valid and equal.
- The timer's small preference record is committed synchronously when starting, so a successful Start response means the deadline has been written. A failed write clears the in-memory timer and reports failure to the UI.
- Settings remembers the last user-selected hour/minute duration and restores the active timer's original duration when the timer is running.
- Start and Cancel now report when the playback service is unavailable instead of silently doing nothing.
- Existing 1-minute minimum and 23-hour-59-minute maximum remain; zero duration is rejected.

## Lifecycle and platform limits
- The countdown callback is owned by `PlaybackService`. If Android destroys the service/process while playback is active, the same-boot deadline can be recovered when the service is recreated, but this implementation does not install an independent AlarmManager alarm. Therefore it does not claim a guaranteed pause at the exact deadline while the service is absent or suspended.
- If Android does not expose a valid boot count, the timer still works while the current service instance remains alive, but its deadline is not persisted. This is safer than restoring an elapsed-realtime deadline from a potentially different boot.
- A timer whose deadline has already elapsed when the service is recreated is cleared during restoration. The service itself may already have been stopped by Android; no separate delayed pause is claimed.

## Validation
- Source-level checks cover timer bounds, valid boot identity, synchronous start persistence, cancellation cleanup, duration restoration, and service-availability feedback.
- ZIP integrity and XML parsing are checked for this archive.
- Android compilation, installation, and physical-device timing tests require the project's GitHub Actions build and a device; they are not claimed as completed here.

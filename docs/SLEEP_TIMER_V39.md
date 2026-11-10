# Sleep Timer Reliability — v39

## Scope

This pass is limited to the sleep timer and its Settings presentation. It preserves the existing 15, 30, 45 and 60 minute presets and does not modify the playback transition or crossfade engine.

## Changes

- Replaced the in-memory `CountDownTimer` countdown with a monotonic `SystemClock.elapsedRealtime()` deadline.
- Persisted the deadline, selected preset and Android boot count in private app preferences.
- Restores an active timer when `PlaybackService` is recreated during the same boot. A reboot invalidates the old monotonic deadline safely instead of comparing clocks from different boots.
- Service destruction now disposes only the in-memory ticker; it no longer erases the saved timer.
- Expiry clears timer state before invoking the pause callback, so `isRunning()` and the selected preset cannot remain stuck active.
- Validates timer durations against the supported presets.
- Settings now keeps the original preset selected for the full countdown and displays a live `MM:SS` remaining-time status. The view refreshes on resume and once per second while visible.

## Behavior and limitations

The timer continues to count down against a monotonic deadline while the service/process is alive, including while the screen is off. If Android destroys and later recreates the service during the same boot, the remaining time is reconstructed from the saved deadline. If the timer expires while the service is completely absent, there is no independent alarm receiver in this pass; when the service next starts, the expired deadline is cleared. This avoids adding exact-alarm permissions or a second playback-control path that could interfere with Media3.

A reboot clears the timer. This is deliberate: a sleep timer should not unexpectedly carry over into a later boot or rely on a wall clock that may have changed.

## Validation

- XML parsing of `fragment_signal.xml`: passed.
- Static source checks for obsolete `CountDownTimer` usage and whitespace errors: passed.
- Android Gradle compilation, APK generation, and device/background lifecycle testing: not run in this workspace; confirm with the GitHub Actions release build and on-device test before treating the change as release-verified.

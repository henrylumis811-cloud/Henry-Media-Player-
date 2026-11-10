# RVH Music v58 — Playback and screen lifecycle audit

Version: 3.41
Version code: 44

## Scope
Reviewed Activity recreation, Fragment view lifecycle, Now Playing observers, MediaSession ownership, periodic playback checkpoints, and process-death session restoration. The UI remains a client of the service-owned playback bridge; playback is not tied to Activity or Fragment lifetime.

## Correction: stale session restoration race

`PlaybackService` rebuilds the saved library queue asynchronously after service creation. Before this correction, a slow library scan could finish after the listener had already selected a different queue and overwrite that newer choice with the previous session.

The restore operation now has a generation guard and cancellable job:

- A new queue selection invalidates and cancels an outstanding restore.
- The result is checked again on the main thread immediately before mutating the bridge.
- A non-empty queue also blocks a late restore.
- The restore's own queue callback is explicitly distinguished from a user queue change, so it does not cancel itself.
- Explicit queue clearing invalidates pending restoration as well.
- Cancellation is propagated rather than misinterpreted as a scan failure and triggering the single-track fallback.

This is a playback-state correctness fix: it prevents an old saved session from replacing a newer user action during service startup.

## Lifecycle behavior reviewed

- `MainActivity` relies on FragmentManager state restoration when recreated; it only installs the Library root on a genuinely new Activity state.
- Activity destruction releases its MediaController/listener, not the service-owned playback engine.
- `AltarFragment` stops its progress runnable, live-lyrics updates, visualizer and player listener in `onStop`; it reattaches them in `onStart` and releases view-scoped resources in `onDestroyView`.
- Artwork identity is reset when the Now Playing view is recreated, allowing the new ImageView to receive the current track artwork.
- The playback service saves current media identity and position periodically and at important playback callbacks, and again during service destruction/task removal.

## Known limits / device tests still required

- Saved playback state stores the current track and position, not the complete prior custom queue ordering. A process-death restore rebuilds the sorted library queue around the saved track. This is existing documented behavior, not a full queue snapshot.
- Fragment back-stack restoration and Now Playing view reattachment need actual rotation, background/foreground, and process-death tests on Android.
- Android SDK/Gradle are unavailable in this workspace. No Kotlin compilation, signed Release APK, emulator test, or physical-device test is claimed.

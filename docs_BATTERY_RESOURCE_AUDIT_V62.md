# Battery and background-resource audit — v62

## Scope

Reviewed the current v61 source for recurring UI work, visualizer capture/rendering, artwork decode/cache scheduling, beat-analysis scheduling, playback-service callbacks, and Now Playing/lyrics lifecycle cleanup.

## Finding and change

`VisualizerView.onDraw()` continuously scheduled another frame with `postInvalidateOnAnimation()` while audio was playing. That requests work at the display refresh rate (often 60 Hz or higher), even though the audio Visualizer FFT callback supplies new signal data at a much lower cadence. Each frame performs Aura shader setup and particle drawing, so rendering faster than the signal can change creates avoidable CPU/GPU work.

v62 caps Aura redraw scheduling at approximately 30 frames per second using a 33 ms delayed invalidation. FFT capture and signal analysis remain unchanged; playback timing, audio output, crossfade behavior, visualizer design, and the existing pause fade are untouched. The renderer still stops scheduling frames when released, and `AltarFragment` releases it in `onStop()` and `onDestroyView()`.

## Areas reviewed and retained

- Artwork loading already uses a bounded two-worker queue, deduplicated in-flight requests, persistent app-private disk artwork, size-specific memory caches, off-main-thread decoding/scaling, and memory-pressure trimming. No speculative cache expansion or worker increase was made.
- Beat-grid analysis is opt-in when Crossfade is enabled, deduplicated per media ID, serialized on a single background executor, and shut down with the bridge. This avoids decoding analysis windows while crossfade is disabled.
- Now Playing progress callbacks stop in `onStop()` and restart in `onStart()`; player listeners and visualizer resources are detached with the view lifecycle.
- Live lyrics stop their tick loop when Now Playing stops; service-owned sleep timer and playback callbacks remain service-scoped so navigation does not interrupt playback or timer semantics.
- PlaybackService removes handler callbacks, observers, receivers, scan jobs and service scope on destruction. DualPlayerBridge removes transition/watcher callbacks and shuts down beat analysis during release.

## Validation boundary

Static source inspection confirms the 30 fps scheduling path and existing lifecycle cleanup. XML resources are unchanged. This environment has no Android SDK/Gradle installation, so no Android compilation, signed Release build, frame-time measurement, thermal test, or physical battery profiling is claimed. A device run should compare Aura responsiveness and battery/temperature under the same playback duration before and after this change.

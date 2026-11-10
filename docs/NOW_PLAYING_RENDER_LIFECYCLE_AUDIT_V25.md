# RVH Music — Now Playing Rendering & Lifecycle Audit (v25)

## Scope
Audit started from the v24 source archive. Focus: Now Playing's Aura capture/render loop, fluid shader background, and live-lyrics timing while the app is backgrounded or the screen is not active.

## Findings and changes

1. **Live lyrics were scheduling work on every display frame.** `LiveLyricsController` used `Choreographer` continuously, even though active-line and word highlighting do not need 60–120 updates per second. Replaced this with a main-thread Handler tick at 100 ms (10 Hz), preserving playback-position-based timing and word-change detection. The tick is explicitly started/stopped with the Now Playing Fragment lifecycle.
2. **Live lyrics could continue ticking after the app entered the background.** `AltarFragment.onStop()` now stops the controller; `onStart()` resumes it. If a lyrics load was cancelled while backgrounded, its media ID is invalidated so foreground resume retries the load rather than leaving the lyrics surface empty.
3. **Aura could continue invalidating itself after its capture was released.** Added a render-active gate. `release()` now stops drawing/invalidation and detaches its player listener/capture; `attachTo()` re-enables rendering when Now Playing returns.
4. **The fluid background continuously invalidated itself while the app was backgrounded.** Added `setMotionEnabled()` and pause/resume it from `AltarFragment.onStop()` / `onStart()`. This is decorative only; it does not affect PlaybackService or audio output.
5. **Now Playing's Player.Listener was not removed.** The fragment now retains the listener reference, removes it on stop/destroy, and reattaches on start. Retry callbacks verify that the fragment is still started and its view still exists.
6. **Aura particle drawing allocated many RadialGradient shader objects per frame.** Replaced per-particle shader construction with lightweight anti-aliased glow/core circles. The overall Aura layout and audio-analysis pipeline remain intact.

## Explicitly preserved
- PlaybackService and the audio players continue independently of the Now Playing UI lifecycle.
- Aura remains the selected visual language; no new visualizer modes were added.
- Live lyrics still use the actual playback position as their timing authority.
- The Now Playing layout and transport controls were not redesigned.

## Validation
- Source-level checks: performed for callback cancellation, lifecycle gating, and listener removal/re-attachment.
- ZIP integrity: verified after packaging.
- XML parsing: all Android XML resources parsed successfully.
- Android Gradle compilation and device profiling: not available in this environment; GitHub Actions and real-device validation remain required.

## Device test checklist
- Play audio, open Now Playing, confirm Aura and live lyrics respond normally.
- Background the app for 30–60 seconds; audio must continue, but Aura/shader/live-lyrics UI loops must stop.
- Return to Now Playing; Aura and lyrics must resume without duplicate listeners or blank lyrics.
- Navigate away and back repeatedly; confirm no duplicated UI updates, memory growth, or stale artwork/lyrics.
- Test pause/resume, manual next/previous, crossfade, and low-memory behavior.

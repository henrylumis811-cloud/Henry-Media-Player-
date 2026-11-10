# RVH Music v63 — Audio engine and long-session resource audit

## Scope
Reviewed `DualPlayerBridge`, `EqualizerController`, `ReplayGainReader`, and the PlaybackService integration for audio-session ownership, output volume layering, focus handling, normalization, analysis scheduling, and service-lifetime resource growth.

## Findings and changes

### Bounded per-track state

The bridge previously retained BPM metadata, beat grids, and ReplayGain attenuation in ordinary maps keyed by media ID. Those maps had no eviction policy, so a long-running service that played a large library could retain metadata for every track visited. These are now access-ordered LRU caches capped at 128 tracks each. The active and recently used tracks remain likely cache residents; an evicted track can be analysed/read again when revisited.

### Bounded beat-analysis backlog

The prior single-thread executor had an unbounded pending-task queue. Although analysis is opt-in and low-priority, rapid queue changes over a long session could enqueue more work than the worker could finish. The executor now has one worker and a two-job queue. A rejected request clears its in-flight marker, allowing a later revisit to retry rather than permanently suppressing analysis.

### Deliberately preserved

- Shared audio-session ID and the two-player MediaSession bridge.
- Equalizer user settings and existing device capability fallback.
- ReplayGain policy: negative track gain attenuates; positive gain is not amplified, avoiding unsafe digital boosting.
- Crossfade equal-power curves, beat-sync speed matching, manual fade behavior, playback speed and focus/noisy-route handling.
- Existing foreground playback and wake-mode behavior.

## Validation status

Static source review and structural checks only. No Android SDK/Gradle toolchain is available in this workspace, so no Kotlin compilation, signed Release build, codec matrix test, Bluetooth test, frequency-response measurement, clipping measurement, or physical-device listening test is claimed. Run the configured GitHub Actions Release workflow and test wired/Bluetooth/speaker routes on the target phone before distribution.

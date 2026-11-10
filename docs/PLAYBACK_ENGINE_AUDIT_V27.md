# Playback Engine Audit — v27

Baseline: v26 navigation transaction audit. Scope: manual transition readiness, centralized audio focus for the dual-player architecture, ReplayGain interaction with fade curves, and playback lifecycle safety.

## Findings and changes

1. **Manual fade-in could run before the selected decoder was ready.** The old fade-in timer started immediately after `prepare()` and `playWhenReady=true`. On slower storage/codecs, the volume curve could finish while the decoder was still buffering, followed by audio appearing late at full level. v27 holds the selected track at zero volume until `STATE_READY` and `playWhenReady`, then starts the full fade-in duration.
2. **Audio focus had no actual owner.** Both ExoPlayers used `setAudioAttributes(..., false)`, disabling Media3 focus handling, and the service did not request focus itself. The existing focus-loss listeners therefore could not reliably protect the dual-player engine. v27 adds one focus owner in the bridge, shared by both decoders. It requests focus before starting playback, pauses on transient/permanent loss, resumes only after transient loss when playback was previously active, ducks to 20% on duckable loss, and abandons focus on user pause, stop, permanent loss, and release.
3. **Volume-layer consistency.** Focus ducking is included in the base gain. Reapplying ReplayGain during a manual fade now recomputes the active fade curve rather than overwriting it with a raw base volume. This prevents metadata arrival from bypassing a fade-in.
4. **Interruption safety.** User pause clears the transient-resume flag, so a later focus-gain event cannot unexpectedly restart playback.
5. Added `MODIFY_AUDIO_SETTINGS` permission for audio-focus and existing system-volume interactions.

## Preserved behavior

- Crossfade remains opt-in and applies only to natural end-of-track transitions.
- Manual Next/Previous, Library selection, and Queue selection remain non-overlapping fade-out → switch → fade-in operations.
- Beat-synchronized crossfade and Smart fallback strategy are not redesigned in this patch.
- Both ExoPlayers continue to share the same audio session ID; equalizer/visualizer ownership is unchanged.
- Playback speed and ReplayGain algorithms are otherwise unchanged.

## Verification status

- Static source assertions: passed for centralized focus request, transient-resume cancellation on user pause, readiness-gated manual fade-in, and single manifest permission entry.
- XML parsing: to be reported by the packaging check.
- ZIP integrity: to be reported by the packaging check.
- Android compilation and device audio tests: **not verified in this environment**. GitHub Actions and physical-device testing are required.

## Required device regression tests

1. Play → Next on fast local storage and on a large/slow audio file; confirm no silence-to-full-volume jump.
2. Start playback, receive a phone call or notification focus interruption; confirm pause and correct resume behavior.
3. Pause manually during transient focus loss; confirm focus gain does not restart playback.
4. Test duckable focus loss with a navigation/assistant prompt; confirm music ducks and restores.
5. Unplug wired headphones / disconnect Bluetooth during a fade; confirm no second player remains audible.
6. Crossfade OFF: Next changes track once, with no overlap.
7. Crossfade ON: automatic transition only; manual selection remains non-overlapping.
8. Change master volume and toggle ReplayGain during both fade phases; confirm no sudden level jump.
9. Test repeat-one, repeat-all, shuffle, queue edits during transition, and playback at 0.5×, 1×, and 2×.
10. Compare AirPods Pro output against another local player at matched device volume and normalization settings.

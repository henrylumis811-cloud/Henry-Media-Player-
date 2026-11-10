# Audio Focus Lifecycle Guard — v51

Version 3.34 (versionCode 37). This pass builds on the v50 single-owner audio-route/focus handling. The Release-only signing and publishing workflow is unchanged.

## Finding: stale focus callbacks after abandonment

`AudioManager` focus-change callbacks are delivered through the supplied handler. A callback can already be queued around the time RVH abandons its focus request, such as when the user presses Pause during a transient interruption. Before this pass, a late `AUDIOFOCUS_GAIN` callback unconditionally set the bridge's local `hasAudioFocus` flag to true. Although the callback did not necessarily restart playback (the explicit-resume flag had been cleared), the next Play command could then treat the stale local flag as a valid lease and skip a new `requestAudioFocus()` call.

The focus listener now ignores every focus-change callback while the bridge has no active focus lease. A genuine transient interruption keeps `hasAudioFocus` true while setting `focusTemporarilyLost`, so the corresponding gain callback remains accepted and the intended resume path still works. Permanent loss, user pause, output disconnection, queue end, stop, and release abandon the lease; callbacks arriving after those paths are ignored.

## Interruption behavior reviewed

- Incoming phone calls and other transient interruptions: pause the active player, remember whether it was actually playing, and resume only after a valid gain if the user has not paused or disconnected output.
- Navigation/notification duck requests: lower the bridge-managed volume to 20%; restore it on a valid gain.
- WhatsApp/other apps requesting transient focus: follow the focus type Android grants; RVH does not assume every interruption is a permanent stop.
- User Pause during an interruption: clear resume intent and abandon focus; a later stale gain cannot revive the lease or playback.
- User Play while focus is still temporarily unavailable: do not start without focus; a later gain does not auto-start unless the original playback intent remained valid. The user can explicitly press Play again.
- Permanent focus loss: pause and abandon; never auto-resume from a later callback.
- Bluetooth/headset unplug: v50 receiver clears resume intent, pauses both players, and abandons focus.

## Deliberate scope boundary

No delayed-focus acceptance was added. This avoids silently queuing a user Play request during a call or another exclusive interruption. RVH waits for focus to become available and requires an explicit Play action if the original playback intent was cancelled. No crossfade curves, beat analysis, ReplayGain, queue behavior, or audio effects were changed.

## Validation limits

Source review confirms the callback guard is evaluated before any focus-state mutation, transient loss retains the lease so legitimate gain callbacks are accepted, and all explicit abandonment paths clear the local lease. Version metadata and Release-only workflow are checked. Android SDK/Gradle are unavailable in this workspace, so Kotlin compilation, signed Release APK generation, and physical tests with calls, navigation prompts, WhatsApp playback, Bluetooth reconnection, and repeated pause/resume timing have not been run. Those remain necessary on-device verification.

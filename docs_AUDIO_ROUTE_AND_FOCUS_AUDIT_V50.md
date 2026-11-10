# Audio Route and Focus Reliability — v50

Version 3.33 (versionCode 36). This pass builds on v48 service-lifecycle and v49 dual-decoder recovery work. The Release-only signing/build workflow is unchanged.

## Findings and corrections

### 1. Two independent noisy-output handlers

Both physical ExoPlayers previously enabled Media3's `setHandleAudioBecomingNoisy(true)`. Because the bridge runs two decoders and can have both audible during a crossfade, each player maintained its own noisy-output handling. That split ownership made it harder to guarantee that a headset/Bluetooth disconnect cancelled the logical transition and invalidated the bridge's transient-focus resume intent as one operation.

Both players now disable their individual noisy-output receivers. `DualPlayerBridge` registers one application-context receiver for `AudioManager.ACTION_AUDIO_BECOMING_NOISY`, using `RECEIVER_NOT_EXPORTED` on Android 13+ and the compatible registration overload on older supported releases. On receipt it:

- clears `resumeAfterTransientFocusLoss`, so a later focus gain cannot restart music onto the phone speaker;
- cancels any crossfade/manual transition;
- pauses both physical players;
- abandons the shared audio-focus lease;
- updates the exposed player state and persisted playback callback.

The receiver is unregistered in `releaseAll()`.

### 2. Duck multiplier surviving a new listening session

The bridge applies its own 0.20 multiplier when it receives `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`. If the user paused and focus was abandoned before a corresponding focus-gain callback, the old multiplier could remain stored. A later successful focus request is not guaranteed to produce a separate gain callback for the new request, so the next playback session could remain unexpectedly quiet.

A newly granted focus lease now clears any stale duck multiplier and reapplies the current transition-aware volume curve. Abandoning focus also resets the multiplier, including the no-active-lease path.

## Behaviour intentionally retained

- One shared focus owner remains responsible for both ExoPlayers.
- Transient focus loss still pauses and resumes only when playback was active before the interruption and the user has not subsequently paused or disconnected the output.
- Permanent focus loss still pauses without automatic resumption.
- Ducking remains app-managed through the bridge volume layer, preserving the multiplier across fade ticks.
- Media3 playback attributes and the Release signing configuration are unchanged.

## Validation limits

Source-level review confirms single receiver ownership, API-level registration branches, unregister-on-release, both-player pause, transition cancellation, resume-intent clearing, focus abandonment, and duck-multiplier reset. XML and workflow checks and archive integrity checks are performed for this snapshot. This workspace has no Android SDK/Gradle build environment; Kotlin/Android compilation, signed Release APK generation, Bluetooth headset disconnect/reconnect tests, phone-call/navigation interruption tests, and physical-device listening tests have not been run.

# RVH Music v66 — Audio interruption lease-generation guard

Version: 3.49
Version code: 52

## Finding: a boolean focus guard did not distinguish successive focus leases

The existing v51 guard correctly ignored focus callbacks while no local focus lease was active. However, it used one reusable listener and a boolean `hasAudioFocus`. A callback queued by Android for an older request could arrive after the user had paused, abandoned that request, and quickly started playback again. By then the boolean could be true for the *new* request, allowing the stale callback to be mistaken for a callback belonging to the current session. A stale transient-loss callback could pause newly started playback; a stale gain callback could act on the wrong playback intent.

## v66 correction

- Create a distinct `OnAudioFocusChangeListener` for each focus request.
- Capture a monotonically increasing focus-lease generation in that listener.
- Accept a callback only when its captured generation still matches the current lease and the lease is active.
- Invalidate the generation before abandoning a lease, before clearing its listener/request references.
- Invalidate a denied request so any callback queued during that attempt cannot be accepted.
- Keep the existing transient interruption resume policy, explicit-pause cancellation, permanent-loss behavior, duck volume, shared two-player focus ownership and noisy-output pause behavior.

This is a narrow race-condition fix; it does not change the audio format, EQ, ReplayGain, fade curves, queue policy or playback UI.

## Source-level checks

- Each focus request creates a new listener with a captured generation.
- The callback checks both generation equality and active-lease state before dispatch.
- Abandonment increments the generation before invoking Android's abandon API.
- Failed requests also invalidate their generation.
- User pause still clears `resumeAfterTransientFocusLoss` before abandoning focus.
- Noisy-output handling still clears resume intent and pauses both physical players.
- The Release workflow still runs `clean assembleRelease`; all four signing-secret names remain present.

## Required device tests

1. Start playback, receive a call, then let the call end; verify expected transient resume.
2. Start playback, receive a call, press Pause during the call, then end the call; verify no automatic resume.
3. Start playback, trigger a transient interruption, press Pause and immediately Play after focus is available; verify an old callback cannot pause or restart the new session.
4. Repeat rapid Pause/Play around navigation prompts and notification sounds.
5. Disconnect Bluetooth/headphones during an interruption, reconnect, and verify no automatic speaker or headphone resume.
6. Repeat on Android 10+ and the target Tecno device, recording Android version and interruption source.

## Validation limits

Static source checks and archive integrity checks were performed. The workspace does not contain an Android SDK/Gradle executable, so Kotlin compilation, signed Release APK generation, emulator tests and physical-device interruption tests are not claimed.

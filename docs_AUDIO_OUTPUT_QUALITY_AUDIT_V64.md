# RVH Music v64 — Audio output quality audit

## Scope

Reviewed the two-player Media3 output path, shared Android audio session, audio attributes, Equalizer attachment, ReplayGain volume layer, focus/route handling, and whether RVH Music applies custom codec, sample-rate, resampling or offload policy.

## Findings

### Shared session and effect attachment

Both internal ExoPlayers are assigned the bridge's same generated audio-session ID. Both use the same `USAGE_MEDIA` / `AUDIO_CONTENT_TYPE_MUSIC` attributes, and ExoPlayer's own audio-focus and noisy-output handling is disabled in favor of the bridge's single focus owner and receiver. The Equalizer is created against that shared session. Android documents session-scoped insert effects as applying to the audio tracks in that session, so the architecture is appropriate for keeping the effect attached across the ping-pong player swap. This does not guarantee that every OEM audio-effect implementation grants RVH control or sounds identical across routes.

### EQ peak headroom correction

The app's friendly EQ controls can add positive gain to one or more frequency bands. Previously, the app changed those filter levels without compensating in the player gain stage. That can reduce digital peak headroom and may cause clipping on already-hot source material, depending on the device's effect implementation and signal chain.

The bridge now applies a conservative pre-attenuation derived from the highest positive band level, with an additional 1.5 dB margin. It is applied in `baseVolume`, so both internal players receive the same correction and it remains multiplied with master volume, per-track attenuation, focus ducking and existing fade curves. Disabling the EQ removes the compensation. This reduces clipping risk; it is not a true-peak limiter and cannot guarantee that every device's downstream DSP will never clip.

### ReplayGain policy

The current policy remains intentionally conservative: negative track gain is converted to attenuation; positive track gain is not amplified. This avoids increasing digital peaks without a true-peak-aware limiter. The bridge comment was corrected to reflect that actual policy.

### Bluetooth, sample rate and offload

RVH Music does not force a Bluetooth codec, sample rate, bit depth, preferred output device, or OEM sound mode. The output route and Bluetooth codec negotiation remain under Android and the connected device's control. The project does not install a custom PCM resampler or enable a custom audio-offload mode in its player builder. Therefore, source inspection cannot establish whether a given phone/headphone combination uses a particular Bluetooth codec, resampling path, DSP chain, or offload state. Those require runtime/device diagnostics.

The `ACTION_AUDIO_BECOMING_NOISY` receiver still pauses both players and prevents an unplugged route from unexpectedly resuming through the phone speaker. No routing behavior was changed in this pass.

## Changes

- Added an EQ-headroom callback from `EqualizerController` to `DualPlayerBridge`.
- Added a shared EQ headroom multiplier to the bridge's common volume calculation.
- Recomputes physical levels whenever EQ enablement or band levels change, including while a transition is active.
- Corrected the ReplayGain comment; no change to gain policy.
- Version: 3.47 (versionCode 50).

## Validation and limitations

Static source checks only. This workspace does not have an Android SDK/Gradle toolchain, so Kotlin compilation, signed Release build, and physical listening tests are not claimed. Required device tests: compare EQ off/on at high master volume with near-full-scale tracks; check for distortion and perceived loudness; repeat during a crossfade; test phone speaker, wired headphones, Bluetooth earbuds and Bluetooth speakers; confirm EQ remains active on both sides of a crossfade; compare the same source track against another player at matched loudness. AirPods Pro and the target Tecno device need direct listening/diagnostic checks before any claim about their quality issue can be made.

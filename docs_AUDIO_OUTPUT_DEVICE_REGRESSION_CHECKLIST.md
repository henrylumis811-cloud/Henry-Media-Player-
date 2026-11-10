# RVH Music — Audio output device regression checklist

This checklist is for physical-device validation of the v64 audio path. Static source inspection cannot verify OEM audio-effect behavior, Bluetooth codec negotiation, or perceived sound quality.

## Before testing

- Install the signed Release APK built from the v64 source.
- Use the same locally stored lossless or high-bitrate reference tracks throughout the comparison.
- Disable phone-wide EQ, Dolby/sound enhancement, spatial audio, adaptive sound, and other third-party audio processing where possible. Record anything that cannot be disabled.
- Keep Android media volume and RVH master volume fixed for comparisons. Match perceived loudness before comparing tonal quality; louder often sounds subjectively better.
- Record phone model, Android version, headphone/speaker model, Bluetooth codec shown by Android (if available), and whether battery saver is enabled.

## Device-route matrix

Run each route separately:

1. Built-in phone speaker.
2. Wired headphones or USB-C DAC, if available.
3. Bluetooth earbuds/headphones, including AirPods Pro if available.
4. Bluetooth speaker.

For every route, test:

- EQ disabled, then EQ enabled with Balanced.
- Bass Boost, Vocal Clarity, Treble Boost, and at least one combined preset.
- Master volume at approximately 50%, 80%, and the highest normally used level.
- A quiet recording, a dynamically compressed/loud recording, and a track with strong bass and high-frequency content.
- Playback for at least 20 minutes to detect route instability, crackling, unexpected volume changes, or thermal/battery-related behavior.

## Transition tests

For each route, test all of the following with EQ disabled and enabled:

- Natural end-of-track transition with crossfade disabled.
- Natural end-of-track transition with Smart crossfade.
- Beat-synchronised transition when a confident beat analysis is available.
- Manual Next and Previous while a track is playing.
- Selecting a different track from Library and Queue.
- Changing master volume during the first half of a crossfade.
- Changing EQ settings during a crossfade.
- Pausing, resuming, and receiving a phone call during a transition.
- Disconnecting Bluetooth during playback; verify playback pauses and does not unexpectedly resume through the phone speaker.

## Expected behavior

- No audible click, crackle, or sudden level jump when playback starts or a route changes.
- No unintended overlap on manual Next/Previous or Library/Queue selection.
- Automatic crossfade remains smooth, without a sustained volume hole or post-transition level dip.
- EQ remains active on both sides of the automatic crossfade.
- Changing master volume during a transition affects both sides consistently.
- Disabling EQ removes RVH's EQ headroom attenuation; enabling positive EQ gain lowers the player gain conservatively.
- Bluetooth disconnect pauses playback rather than unexpectedly switching to the phone speaker.
- No player remains audibly active after pause, stop, or route loss.

## AirPods Pro comparison

If RVH Music still sounds worse than another player:

1. Use the exact same local audio file in both apps.
2. Disable EQ and all sound enhancements in both apps where possible.
3. Match loudness by ear or with an external measurement; do not compare at unequal volume.
4. Confirm both apps use the same AirPods listening mode and spatial-audio setting.
5. Repeat with crossfade off and with EQ off to isolate the normal playback path.
6. Note whether the problem is reduced bass, harsh treble, narrow stereo image, distortion, dropouts, or simply lower loudness.

Do not change codecs, sample rates, bit depth, or add DSP based only on subjective impressions from unmatched tests.

## Report template

For each failure, record:

- Phone and Android version:
- RVH Music version/build:
- Output device and connection type:
- Track/file format:
- EQ state and settings:
- Crossfade mode:
- Master volume:
- Exact steps to reproduce:
- What was heard or observed:
- Does the same file behave correctly in another player at matched loudness?

## Scope and limitations

The v64 source review verified the shared audio-session configuration, common player-volume layer, conservative EQ headroom calculation, and existing route/focus ownership. It did not verify actual hardware output, Bluetooth codec selection, OEM effects, or the AirPods Pro report. A successful signed Release build and these physical tests remain necessary before declaring audio quality resolved.

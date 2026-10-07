# RVH Music Playback Regression Checklist

Run these checks on a physical Android device after each playback-engine change.

## Crossfade OFF
- [ ] Manual Next fades out the current track, changes tracks once, and fades in the next track.
- [ ] Manual Previous behaves the same way.
- [ ] Selecting a track directly from the library uses the manual fade path.
- [ ] Selecting a queue item starts that item and uses the manual fade path.
- [ ] Natural end advances without Beat Sync or Smart Crossfade starting early.

## Crossfade ON
- [ ] Beat Sync is selected when both tracks have usable beat information.
- [ ] Smart Crossfade is selected when beat matching is unavailable or unsuitable.
- [ ] Adaptive fallback occurs only when the preferred transition cannot be prepared.
- [ ] The track analysed and prebuffered is the track that actually plays.

## Playback state and edge cases
- [ ] Shuffle does not change the target between preparation and transition.
- [ ] Repeat One repeats the current track without selecting another queue item.
- [ ] Repeat All wraps correctly at the end of the queue.
- [ ] Pause/resume during preparation or a transition does not leave audio muted.
- [ ] Rapid Next/Previous presses do not leave a player stuck fading.
- [ ] Very short tracks and decoder/load failures recover without stopping playback.
- [ ] Background playback, notification controls, Bluetooth and headset buttons work.

## Release
- [ ] GitHub Actions build job succeeds.
- [ ] APK artifact is present and downloadable.
- [ ] Push to `main` updates the `rvh-latest` release assets.
- [ ] Install the generated APK over the previous debug build and smoke-test playback.

Do not mark a playback item verified from source inspection alone. Record device, Android version, and result for each run.

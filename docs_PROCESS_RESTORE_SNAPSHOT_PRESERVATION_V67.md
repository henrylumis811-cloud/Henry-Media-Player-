# Process restoration snapshot preservation — v67 (3.50)

## Finding

`restoreLastSessionIfFresh()` previously used `restoreSingleTrackFallback(saved)` for every exception thrown while scanning MediaStore. That included permission loss and transient provider/query failures.

The fallback loads only the saved current item. Loading it invokes the bridge's normal queue/state callbacks, which can persist that one-item queue over the last durable multi-track queue snapshot. A temporary inability to scan the library could therefore permanently discard the user's queue ordering and shuffle history.

## Changes

- Permission failures now leave the persisted session untouched.
- Other scan/provider failures also leave the persisted session untouched rather than invoking the destructive one-item fallback.
- A successful scan that returns a genuinely empty library retains the existing single-track fallback behavior.
- When audio permission is granted while the service is alive, `refreshMediaLibraryAfterPermissionChange()` retries restoration if the bridge has no loaded media items.
- A queue already loaded or selected by the user is never replaced by this permission-triggered retry.

## Preserved behavior

- Restored sessions remain paused at the saved position until the listener presses Play.
- Queue ordering, stable media IDs, shuffle history and recently-shuffled avoidance remain handled by the existing restore path.
- The intentional-empty-queue marker behavior from v65 remains unchanged.
- No playback, crossfade, EQ, ReplayGain, UI or navigation behavior was changed.

## Validation and limitations

Source-level checks should verify that the scan-exception branches do not call `restoreSingleTrackFallback()` or `savePlaybackState()`, and that the permission-grant retry is guarded by `bridge.mediaItemCount == 0`.

No Android SDK/Gradle toolchain is available in this workspace, so this change has not been compiled or exercised through actual process death, permission revocation/grant, or MediaStore provider failure on a device. Those runtime scenarios remain required release checks.

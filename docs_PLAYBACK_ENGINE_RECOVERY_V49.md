# Playback Engine Error Recovery — v49

Version 3.32 (versionCode 35). This pass builds on the v48 service lifecycle correction and keeps the Release-only build/signing workflow unchanged.

## Defects addressed

The bridge owns two ExoPlayer instances. The previous listener only reacted when the active decoder raised `onPlayerError`. An error from the idle decoder was ignored, including when it was the incoming player in a crossfade. The crossfade runnable waits for the incoming player to reach `STATE_READY`; if it has already entered an error state, that readiness condition can remain false indefinitely and the runnable can continue polling.

The listener now routes errors from either physical player through one recovery handler:

- An idle/prebuffer decoder failure is recorded without interrupting the audible outgoing track. If it was the incoming side of a live crossfade, the transition is cancelled and the outgoing player is restored.
- Automatic next-track selection excludes decoder-failed media IDs for the current queue session, including cached next-target plans. This prevents the watcher from immediately selecting the same failed prebuffer again.
- If the active decoder fails, any overlap is cancelled and playback advances directly to the next healthy item. A fade from an errored decoder is deliberately not attempted.
- If there is no healthy next item, the failed player is paused, silenced, playback intent is cleared, and audio focus is abandoned.
- An explicit user selection clears that item's failure marker, allowing a deliberate retry instead of permanently blacklisting a file. Replacing/restoring a queue retains only failure markers for items that remain in that queue.

## Behavioural boundaries

- The failure marker is in-memory and scoped to the current bridge/queue session; it is not persisted across service/process recreation.
- This is decoder-error recovery, not a guarantee that every damaged, unsupported, or inaccessible file can be played.
- Audio focus policy and Bluetooth/noisy-output handling were inspected but not changed in this pass. The bridge still requests one shared focus lease for both physical players, and both ExoPlayers retain Media3's becoming-noisy handling.

## Validation limits

Source inspection confirms the two-player error routing, failure-marker invalidation of cached next targets, manual retry path, and no-next focus release. XML/YAML and release-workflow checks are performed on the archive. Android/Kotlin compilation, signed Release APK generation, simulated decoder failures, Bluetooth headset tests, and physical-device playback tests have not been run in this workspace.

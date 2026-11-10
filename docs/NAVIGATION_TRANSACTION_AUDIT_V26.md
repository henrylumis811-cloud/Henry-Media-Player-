# RVH Music — Navigation Transaction Audit (v26)

## Scope
Audited the existing Fragment shell and its edge-swipe back container from the v25 checkpoint. This is a targeted correctness/performance pass, not a wholesale navigation rewrite.

## Findings and fixes

1. **Repeated Now Playing layers were possible.** `openNowPlaying()` only checked the currently visible Fragment. If an older `AltarFragment` was already lower in the back stack (for example, Now Playing → Settings → mini-player tap), another `AltarFragment` could be added. The method now returns to the existing `altar` back-stack entry instead of creating a duplicate.
2. **Rapid taps could enqueue duplicate destinations.** Fragment transactions are committed asynchronously. Repeated taps before the first transaction was applied could enqueue multiple copies. A pending-destination guard now serializes shell navigation until FragmentManager reports a back-stack change.
3. **State-saved transactions were not guarded.** Shell navigation now checks `FragmentManager.isStateSaved` before committing or popping from UI entry points, avoiding illegal state changes after Activity state has been saved.
4. **Existing destinations are reused.** Queue, Playlists, Settings and Lyrics continue to pop back to their existing named destination rather than constructing duplicate screen instances. The current replace/back-stack model is preserved to avoid destabilizing nested playlist state and the custom swipe-back gesture.

## Deliberately not changed

- No `show()/hide()` retained-fragment migration. It would require a broader audit of view lifecycle, menu visibility, player listeners, adapter ownership, nested playlist navigation and predictive Back behavior.
- No change to the 120ms shell transitions or the Now Playing visual design.
- No change to PlaybackService, crossfade, audio focus, or player ownership.
- No replacement of the custom left-edge swipe gesture. It continues to avoid clickable controls, SeekBars, EditTexts and horizontal row gestures.

## Regression checklist

- Library → Now Playing → Settings → mini-player: returns to the existing Now Playing instance; no duplicate visualizer/listeners.
- Now Playing → Queue → Now Playing: returns to one Now Playing entry.
- Library → Queue → Library via Add/Browse: returns to Library root.
- Library → Playlists → playlist detail → Back: nested playlist state remains intact.
- Rapid repeated taps on Queue/Settings/Playlists: no duplicate fragments are enqueued.
- Swipe from left edge: commits Back; short/vertical gestures cancel; controls and seek bars remain interactive.
- System Back and toolbar Back return to the same previous destination.
- Background playback remains owned by PlaybackService and is unaffected by navigation.

## Validation
- Source-level navigation guards and duplicate-Now-Playing path inspected.
- Archive integrity and Android XML parsing are checked during packaging.
- Android Gradle compilation and real-device gesture/transition profiling still require GitHub Actions and device testing.

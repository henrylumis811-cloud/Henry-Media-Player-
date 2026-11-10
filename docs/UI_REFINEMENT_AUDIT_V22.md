# RVH Music — UI responsiveness and quality audit v22

Baseline: `RVH_Music_background_audio_stability_v21.zip`

## Implemented in v22

- Reduced shell fragment enter/exit/pop animations to a 120 ms micro-transition with restrained travel. There are no artificial waiting timers between destinations; the remaining motion is only a short directional cue.
- Removed the Library RecyclerView's entrance layout animation, which replayed when returning to the Library and made the list feel delayed.
- Removed the Library's 250 ms playback-marker polling loop. The current-track indicator now updates from MediaController playback events.
- Moved Library filtering, grouping and sorting to `Dispatchers.Default`, with cancellation/generation checks so stale searches cannot overwrite newer results.
- Removed the Queue's one-second refresh loop. Queue refreshes are now driven by player timeline/track events and direct local mutations. MainActivity also refreshes Queue after MediaController connection and playback events, covering the case where Queue opens before the controller is ready.
- Replaced the Lyrics screen's per-display-frame Choreographer callback with a lifecycle-bound 120 ms Handler tick. It stops while Lyrics is paused and resumes when visible.
- Removed the duplicate initial MediaStore scan in Playlists (`onViewCreated` plus `onResume` previously both triggered it).
- Made Queue's “Add songs” action unwind to the Library root even when Queue was opened from Now Playing or another nested destination.
- Removed an explicit `Activity.recreate()` after `AppCompatDelegate.setDefaultNightMode`; AppCompatDelegate already manages recreation for a runtime night-mode change, so the extra call could cause redundant recreation/flicker.
- Added tap-to-skip to the cinematic splash so it does not force users to wait for the full intro.
- Increased the Now Playing shuffle/repeat and Queue drag/info touch areas to at least 48 dp where the controls were undersized.
- Incremented Android version to `versionCode 11`, `versionName 3.8` so the next debug APK can update-install over v21.

## Research standards used

- Android frame rendering: target under 16 ms for 60 Hz and avoid main-thread work that blocks frames. Slow/frozen frames commonly surface during RecyclerView scrolling and screen transitions.
- Android app quality: transitions should not flicker or delay, back navigation should work from every screen, and touch targets should be at least 48 dp.
- Fragment transitions: Animator-based transitions are compatible with predictive Back; the app already uses Animator resources rather than legacy Animation resources.

## Audit findings / remaining work

- Fragment destinations are still created with `replace()` and put on the back stack. That is functionally simple but recreates views on return. A future architecture phase could use retained destination instances with `show()/hide()` or a Navigation component graph, but it must be designed carefully around the persistent Now Playing layer, playlist nested detail, edge-swipe dismissal and predictive Back. Not changed in this pass to avoid destabilizing navigation.
- Now Playing still updates its progress/time UI every 500 ms while visible. That is acceptable for a progress display, but should be profiled on lower-end hardware before reducing further.
- Playlist detail currently reconstructs its song adapter when rendered. This is a candidate for adapter reuse and DiffUtil-based updates.
- Analytics rendering and Equalizer view construction should be profiled with a large listening history / slow device. No unmeasured redesign was made.
- Splash animation remains intentionally cinematic; it can now be skipped with a tap.
- Visual layout review from XML is not a substitute for screenshot/device validation at multiple font scales, screen sizes and 60/90/120 Hz refresh rates.

## Verification limits

- All Android XML resources parse successfully.
- ZIP integrity verified.
- Static source checks performed for removed Library/Queue polling loops, Lyrics frame callback, animation durations, and version increment.
- Gradle/Android compilation and runtime frame profiling were not available in this environment. GitHub Actions must confirm compilation; device tests must confirm real frame pacing and interaction behavior.

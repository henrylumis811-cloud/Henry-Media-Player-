# RVH Music UI Refinement Audit — v68

## Principles locked
- Library is the browsing home; its inline search is the sole Library search affordance.
- The mini-player is a persistent bridge into Now Playing, not another content screen.
- Now Playing is one composition: artwork → identity → transport → timeline → one live lyric → secondary actions.
- Live lyrics are transparent, one-line, and playback-position driven.
- Queue/Playlists/Lyrics use the shell header for context and avoid duplicate titles unless a nested playlist needs its own local identity.
- List rows favor spacing and hierarchy over repeated elevated cards.
- Custom action targets are >=48dp; visible glyphs can stay compact via padding.
- Photo-facing surfaces use explicit light text tokens in both theme modes.
- All visible current-track surfaces prefer the pending manual target during the fade handoff.

## Refinements applied
- Correct semantic back arrow.
- Removed redundant Library header search visibility.
- Removed duplicate Queue heading.
- Flattened Queue/Playlist/list rows.
- Upgraded small hit targets to 48dp.
- Replaced text-heavy PLAY/PAUSE with a circular primary media control.
- Split elapsed and total timeline readouts.
- Removed word-highlight size changes that could remeasure the line and create jitter.
- Word-timed lyric spans are positioned by finding the actual rendered token text.
- Full Lyrics frame work is throttled while Live Lyrics remains frame-driven.
- More menus are contextual and omit the current destination.
- Nested Playlists navigation now reuses the persistent shell back/title instead of creating a second header.
- Lyrics editing/sync actions are blocked during a sub-second manual handoff so timestamps cannot attach to the outgoing track.
- Mini-player, Library, Now Playing and Lyrics share pending-track identity during manual fades.


## Final interaction refinement
- Physical and shell Back now share the same nested-playlist behavior, preventing an inconsistent hierarchy between system and app navigation.
- Mini-player and tertiary Now Playing controls meet the 48dp touch-target requirement without enlarging their visible glyphs.


## Release-readiness finding
The project currently declares compileSdk/targetSdk 34. Current Google Play targeting requirements have moved to API 36. This should be handled as a dedicated platform/insets migration, not a blind version bump, because newer Android versions enforce edge-to-edge behavior that can affect the translucent shell/header geometry.

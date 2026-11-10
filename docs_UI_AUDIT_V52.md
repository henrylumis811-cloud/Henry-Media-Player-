# RVH Music UI Audit — v52 (3.35)

## Scope
Focused corrections from the source-based UI audit of the v51 snapshot. Existing visual direction and navigation decisions are preserved.

## Findings and changes

### Now Playing secondary-action row
- The action row contains Shuffle, Repeat, Queue, Lyrics, Playlists and Settings.
- Shuffle and Repeat each used a 48dp icon followed by an 18dp label, but their weighted parent cells were only 52dp high. The label could therefore be clipped/overflow the cell.
- The row is now 68dp high and each action cell 66dp high, giving the icon and label their required combined vertical space plus a small row margin.
- All six existing actions and their order remain unchanged. This is not a bottom-navigation redesign; no bottom-navigation cards or new navigation destinations were introduced.

### Library favorites accessibility
- The favorites filter now initializes its visual and accessibility state consistently.
- Its content description distinguishes the inactive action (“Show favorites only”) from the active action (“Show all songs; favorites filter is on”).
- The selected state is exposed through `isSelected`.

## Explicitly preserved
- No bottom-navigation cards. Their absence is intentional and is not treated as a defect.
- Existing shell/header/back-stack navigation and contextual destination controls.
- Full-screen Library, persistent mini-player, and current Now Playing visual identity.
- Aura-only visualizer, transparent single-line live lyrics, and flat Settings layout.

## Validation and limitations
- Source-level layout dimensions and click wiring reviewed.
- Android XML parsing and ZIP integrity checks performed for this snapshot.
- No Android SDK/Gradle build, emulator rendering, accessibility scanner, or physical-device screenshot test was available in this workspace. Narrow-screen and short-height runtime rendering therefore remain to be verified on-device.

# UI responsive-layout audit — v53

## Scope

Focused responsive pass based on the v52 source. This change does not redesign the shell, add bottom-navigation cards, or alter the established navigation decisions.

## Now Playing artwork composition

- Converted `art_visualizer_frame` to a ConstraintLayout so its children can size against the actual available frame.
- Outer ring: 94% of frame width, maximum 250dp.
- Inner ring: 85% of frame width, maximum 226dp.
- Artwork: 72% of frame width, maximum 190dp and minimum 112dp.
- All elements remain centered and square; visualizer fills the frame.
- Existing artwork appearance, ring drawables, title/artist hierarchy, transport controls and lower controls are retained.

This removes the previous fixed 250dp/226dp/190dp children overflowing a smaller visualizer frame. The actual result still needs emulator/device rendering on short-height, narrow-width and landscape configurations.

## Library toolbar

- Sort, view, add-to-playlist and favorites toolbar targets reduced from 48dp to 44dp with 4dp inter-control spacing.
- Search field has a 96dp minimum width.
- During multi-selection, sort/view/favorites are hidden because they are disabled in that state; the add-to-playlist action and selection count remain visible. Controls return when selection ends.
- No new overflow menu or navigation affordance was introduced.

## Validation

- XML parsing and structural checks performed after edits.
- Kotlin source reviewed around `updateSelectionUi` to ensure controls are restored when selection ends.
- Release-only build configuration retained; version 3.36, code 39.
- No Android SDK/Gradle toolchain is available in this workspace, so no signed Release APK, emulator screenshot, or physical-device visual test is claimed.

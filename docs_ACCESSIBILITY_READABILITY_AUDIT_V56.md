# RVH Music v56 — readability and font scaling audit

## Scope
This pass follows the v55 accessibility structure review. It concentrates on small text, fixed-height text containers and contrast in the light and dark themes.

## Changes
- Library toolbar row and search field can grow beyond their minimum height instead of forcing text into a fixed 48dp row.
- Toolbar icon controls retain 48dp by 48dp touch targets; horizontal spacing is reduced and the search field minimum width is 80dp so the four-action selection state can fit on a 320dp-wide screen with the toolbar margins.
- Library status labels are increased from 8–11sp to 10–12sp and their row can grow instead of clipping at 24dp.
- Mini-player artist metadata increased from 10sp to 12sp.
- Now Playing action captions increased from 8sp to 10sp; their text containers can grow, and the action row was increased from 68dp to 76dp to accommodate larger font settings while preserving 48dp icon controls.
- Lyrics status, queue current-track marker and playlist-picker metadata/status labels were enlarged.
- Settings section labels and small action labels were enlarged.
- Analytics card labels and chart date/day labels were enlarged.
- Added `accent_cyan_text`: `#00627A` in light theme and `#00E5FF` in dark theme. Library text labels use this text-specific colour; icon/control accent colours remain unchanged.

## Contrast check
The light-theme `accent_cyan_text` (#00627A) against `bg_base` (#EDF2F7) has a contrast ratio of approximately 6.2:1, exceeding WCAG AA's 4.5:1 requirement for normal text. Existing `text_primary` (#101720) and `text_secondary` (#48566A) against the light base also exceed 4.5:1. Dark-theme text colours retain their existing high-contrast palette.

## Deliberately unchanged
- No bottom-navigation cards or navigation redesign.
- No colour changes to vivid icon, seekbar or playback-state accents.
- No Now Playing visual redesign, audio behaviour, crossfade or queue semantics changes.

## Validation limits
XML well-formedness, static font-size/layout checks, contrast calculations, version metadata, release workflow and ZIP structure are checked in this source pass. This environment has no Android SDK/Gradle installation, so it cannot produce a signed Release APK or verify actual rendering at Android font scales 1.0x, 1.3x, 1.5x or 2.0x. Those require GitHub Actions and device/emulator testing.

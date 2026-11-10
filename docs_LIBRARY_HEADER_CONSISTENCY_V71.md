# Library header consistency and dropdown polish — v71

**App version:** 3.54
**Version code:** 57
**Base:** v70 (3.53 / code 56)

## Problem observed

A cold launch could show the Library with the secondary-screen back arrow and without the app icon/menu control. Returning from Now Playing showed the intended Library header. The root Library transaction is not added to the FragmentManager back stack, so the back-stack listener does not report its initial commit. `updateShellForDestination()` could therefore run while the container was still empty and no later event corrected the header.

## Changes

- The empty initial container is treated as the Library root for header presentation. This prevents a cold-launch flash of the dead-end back arrow.
- The initial root fragment transaction now runs the shell update after commit.
- The shell header is reconciled on activity resume as a lifecycle recovery path.
- The Library menu button now uses a dark circular surface, subtle cyan outline, cyan down chevron, and platform ripple feedback.
- The chevron rotates while the menu is open and returns on dismissal, including outside dismissal and item selection.
- Existing menu entries and their actions remain unchanged.

## Validation

- Source-level review confirms the Library toolbar is selected for both the pre-commit empty state and committed Library state.
- All `GlassPopup.show` call sites remain compatible; the optional dismissal callback defaults to null.
- XML resources are parsed and ZIP integrity is checked during packaging.
- Android SDK/Gradle are not available in this workspace. No Android compilation, signed Release build, emulator run, or physical-device verification is claimed.

## Release workflow

Release-only GitHub Actions workflow retained. All four signing secret names remain unchanged. No signing credentials or keystore are included in the project archive.

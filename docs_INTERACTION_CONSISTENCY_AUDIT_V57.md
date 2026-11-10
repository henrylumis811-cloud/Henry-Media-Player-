# RVH Music v57 — Interaction consistency audit

Version: 3.40
Version code: 43

## Scope
Reviewed shell navigation/back handling, edge-swipe dismissal, queue drag/swipe actions, playlist reorder/removal, and adapter click identity handling. This pass intentionally preserves the established navigation and visual design; bottom-navigation cards were not reintroduced.

## Corrections

### Edge-swipe hit testing
`SwipeDismissFrameLayout` previously mapped a pointer into nested children by subtracting only each child's left/top position. That does not match Android's touch dispatch mapping for scrolled parents or transformed children. A control inside a scrolled/transformed container could therefore be missed by the interactive-control guard, allowing the shell's edge-back gesture to intercept a touch intended for that control.

The hit-test now includes parent scroll offsets and inverse child matrices before descending into nested groups. Non-invertible transforms are safely skipped.

### Repeated/cancelled swipe gesture state
A cancelled swipe animates the content back to rest. A new gesture arriving before that animation completed could inherit a partially translated content view and have its commit distance measured against the old translation. The gesture now cancels any return animator and normalizes translation, scale and alpha at ACTION_DOWN. Commit distance is measured relative to the current gesture's starting translation, not the absolute view translation.

## Preserved behavior
- System back remains primary on gesture-navigation Android devices.
- In-app swipe remains left-edge to right only.
- Vertical scrolling and horizontal row actions retain their existing ownership rules.
- Queue swipe-to-remove and drag-handle reorder remain unchanged.
- Playlist drag reorder and left-swipe removal remain unchanged.
- Shell destination reuse and the existing double-back exit behavior remain unchanged.
- No bottom-navigation cards or navigation redesign.

## Validation and limitations
- Reviewed MainActivity shell destination/back-stack paths and the swipe-dismiss implementation.
- Checked the coordinate conversion and gesture-reset logic statically.
- Confirmed release-only Gradle workflow and version metadata.
- XML parsing, ZIP integrity, root-level archive structure and forbidden signing/build artifacts checked during packaging.
- Android SDK/Gradle are not available in this workspace. No Kotlin/Android compilation, signed Release APK, emulator gesture test or physical-device test is claimed. In particular, edge-swipe interaction with TalkBack and OEM gesture navigation still needs device verification.

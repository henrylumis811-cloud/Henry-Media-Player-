# RVH Music v61 — Storage permission and removable-volume resilience

## Scope
Audit runtime audio permission changes, MediaStore access failures, removable shared storage events, and recovery after access is restored. Preserve the existing MediaStore stable-ID queue/playlist model.

## Changes

### Explicit permission gate
- `MusicScanner.hasAudioPermission()` checks `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` on Android 12 and earlier.
- `scan()` fails with `SecurityException` before querying when permission is absent. This prevents permission denial from looking like a valid empty library.

### User recovery path
- Library empty state exposes a dedicated 48dp music-access action.
- When access is revoked, Library rows are hidden and browsing actions disabled; the last successful in-memory snapshot is retained rather than discarded.
- Activity rechecks access in `onResume`, covering changes made in Android Settings. A restored grant triggers a new scan and service reconciliation.
- The action requests permission while Android allows a runtime prompt, then opens app details Settings after the prompt is permanently suppressed. Notification permission remains independent.

### Removable storage
- The scanner continues querying `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`, the synthetic external volume, instead of querying each volume independently. This preserves the app's existing numeric MediaStore ID and content-URI assumptions while including indexed mounted shared external volumes.
- PlaybackService listens for mounted, unmounted, eject, removed, bad-removal and shared-storage events as an additional reconciliation trigger. The existing MediaStore ContentObserver remains primary.
- Failed scans and permission loss do not prune the live queue. A successfully completed scan remains required before removed queue IDs can be reconciled.

### Active playback safety
- Permission revocation does not clear the current queue or stop an already active decoder directly. Existing decoder error recovery remains responsible if the provider denies a subsequent open/read.
- New queue creation from the Library is blocked while audio permission is absent.

## Validation
- Android XML resources parsed successfully.
- Manifest permission declarations and API-level permission selection inspected.
- Permission-denied path, Settings return path, grant-triggered rescan and queue-preservation guards inspected.
- Dynamic storage receiver registration/unregistration inspected.
- Release workflow remains `clean assembleRelease`, validates all four configured signing secrets, verifies the APK signature, and publishes the signed Release APK.
- No debug build task introduced.

## Limitations requiring device testing
- Physical SD-card mount/eject timing differs across OEM MediaStore providers.
- A removable volume may take time to be indexed after mount; the observer/broadcast refresh is debounced but no device-specific indexing completion guarantee exists.
- Permission revocation while an already-open decoder is reading is OS/provider dependent.
- No Android SDK/Gradle installation is available in this workspace, so no signed Release APK or emulator/device test is claimed.

## Version
- Version name: 3.44
- Version code: 47

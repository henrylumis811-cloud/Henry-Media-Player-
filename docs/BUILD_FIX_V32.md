# RVH Music 3.16 — v32 build fix

The v31 Release build reached Kotlin compilation and failed because `PlaylistPickerAdapter.kt` referenced `View.VISIBLE` and `View.GONE` without importing `android.view.View`.

Fix: added the missing Android View import. No artwork-loading, playback, navigation, or UI behavior was changed.

Version: versionCode 19, versionName 3.16.

Validation: source-level import/reference check and ZIP integrity check. Android Release compilation and signing must still be confirmed by GitHub Actions.

# Build and Release Configuration Audit — v43

Version: 3.27 (versionCode 30)

## Confirmed defects in v42

1. `app/build.gradle` configured debug builds to use `../debug.keystore`, but that file was not part of the source archive. A clean checkout therefore had no guaranteed debug signing key, despite the README documenting `assembleDebug`.
2. The rolling GitHub Release notes hard-coded version 3.14 / versionCode 17, so a successful current release could be published with misleading old version metadata.
3. `docs/RELEASE_SIGNING_SETUP.md` also stated that 3.14 / code 17 was the current version.

## Corrections

- Removed the nonexistent custom debug signing configuration. Gradle now uses its standard per-user debug key for debug builds; no private or machine-specific debug keystore is required in the source archive.
- Release signing remains unchanged: CI restores the dedicated release keystore from GitHub Actions secrets, uses it only on the runner, verifies the APK signature, and removes the restored keystore afterward.
- GitHub Release notes now read `versionName` and `versionCode` directly from `app/build.gradle`, with non-empty checks, instead of embedding a stale version string.
- Updated release-signing documentation and README checkpoint to v43 / 3.27 / code 30.

## Audit scope and validation

- Reviewed the v42 artwork loader, scroll gate, all artwork-consuming adapters, mini-player, Now Playing, playlist hero/catalog, and MediaItem-to-Song conversion.
- Confirmed artwork extraction for app artwork surfaces is centralized in `ArtworkLoader`; `TagEditorActivity` and `TrackInfoDialog` still use metadata extraction for their separate metadata/detail workflows, not for list-row artwork binding.
- Parsed all 91 Android XML files successfully.
- A resource-reference scan found only expected Android platform-resource references (`button1`, `button2`, `button3`, `ic_media_play`, `ic_media_pause`, `btn_star_big_on`, `btn_star_big_off`, `fade_in`, `fade_out`, `holo_red_dark`) as unresolved app-local symbols.
- Confirmed no source-archive dependency on a committed debug keystore remains.
- Confirmed workflow release-note version is derived from the Gradle version fields.

## Not verified in this environment

- Android Gradle compilation, APK assembly, APK signature verification, and physical-device behavior were not run because this workspace has no Gradle executable, Android SDK, or project Gradle wrapper.
- The release workflow still requires the four configured GitHub Actions secrets for the release keystore and passwords.
- Artwork persistence is app-private: cached JPEGs remain until app data is cleared or the app is uninstalled. The cache intentionally has no automatic size/age eviction. Android can evict decoded bitmaps from RAM; in that case the saved JPEG is decoded again, but the original audio file should not need artwork extraction again.
- Existing cached covers are not automatically invalidated if a user edits/replaces embedded artwork for an existing album ID.

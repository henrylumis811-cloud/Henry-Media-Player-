# Release-only project audit — v44

Audit target: source archive `RVH_Music_build_audit_v43_ROOT.zip`.
App version is intentionally unchanged: **3.27 (versionCode 30)**. This is a project/workflow audit checkpoint, not a new app feature release.

## User's build policy

RVH Music is built and distributed as a signed **Release** APK only. Debug APK generation is not a project deliverable and is not used as evidence that the production build works. The existing four GitHub Actions secrets are already configured and must be preserved:

- `RVH_UPLOAD_KEY_BASE64`
- `RVH_STORE_PASSWORD`
- `RVH_KEY_ALIAS`
- `RVH_KEY_PASSWORD`

No secret values or private keystore are stored in this archive. No key rotation or secret recreation is required for this audit.

## Release path inspected

- GitHub Actions workflow: `.github/workflows/android-build.yml`.
- Trigger: push to `main` or manual `workflow_dispatch`.
- Runner: `ubuntu-latest`; JDK 17; Gradle 8.7.
- Signing secrets are checked before build; the Base64 keystore is restored to `app/rvh-music-release.jks` on the ephemeral runner.
- The workflow invokes `gradle --no-daemon --stacktrace clean assembleRelease`.
- It requires the Release APK at `app/build/outputs/apk/release/app-release.apk`, verifies the APK signature with Android `apksigner`, writes a SHA-256 checksum, and uploads the APK/checksum as the `RVH-Music-release-apk` Actions artifact.
- On `main`, it creates or updates the rolling `rvh-latest` GitHub Release and derives the displayed version from `app/build.gradle`.
- `app/build.gradle` uses the dedicated release signing configuration and reads the store password, key alias, and key password from environment variables. `applicationId` remains `com.henrylumis.mediaprayer`; version is 3.27/code 30.

## Corrections made in this audit

- Removed obsolete README instructions for building a debug APK. The documented workflow now reflects the actual Release-only process.
- Removed a stale `.gitignore` comment claiming a debug keystore was intentionally committed. Local debug keystores are now ignored, like other local signing material.
- Removed debug-build wording from the current release-signing guide and clarified that the already-configured secrets are retained.
- Updated the current playback regression checklist to install/test the signed Release APK rather than a debug build.
- Added this audit record. No application source, playback behavior, release key, secret name, secret value, or app version was changed.

## Risks / points requiring CI evidence

1. A successful build has not been demonstrated by this local audit. The actual GitHub Actions run must complete `assembleRelease`, `apksigner verify`, artifact upload, and (for a `main` run) rolling Release publication.
2. This workspace cannot read repository secret values or query the user's GitHub Actions settings. The workflow's four secret names match the previously configured names, but their live presence/value cannot be independently verified here.
3. `apksigner verify` confirms APK signature validity. This source audit does not independently compare the signing certificate fingerprint against a separately recorded trusted fingerprint.
4. The release workflow relies on the Android SDK/build-tools preinstalled on the GitHub-hosted Ubuntu runner. The runner's live image and installed API/build-tools versions are not available in this offline source audit.
5. `workflow_dispatch` from a branch other than `main` builds and uploads the signed Actions artifact but intentionally does not publish/update `rvh-latest`.
6. The rolling release uses a fixed tag, `rvh-latest`, and force-updates it on subsequent `main` builds. This is intentional latest-release behavior, not immutable versioned release history.

## Static validation performed

- ZIP structure and integrity checked; project files are at archive root.
- All 91 Android XML files parse successfully.
- Release workflow and Gradle configuration reviewed together for task name, APK path, signing file path, secret names, artifact name, release tag, and version source consistency.
- No `.jks`, `.keystore`, `.p12`, `.pfx`, or `local.properties` file is included.
- No secret values are present in the source archive.

## Not performed

No Android Gradle compilation, signed APK generation, certificate verification, GitHub Actions execution, GitHub Release publication, or physical-device test was performed in this workspace. Those must not be inferred from static checks.

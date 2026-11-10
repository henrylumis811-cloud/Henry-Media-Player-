# RVH Music signed Release APK setup

The release build is signed with the dedicated RVH Music release key. The four repository Actions secrets are already configured for this project; do not recreate or rotate them as part of routine source updates. The private keystore and passwords are intentionally not included in this repository.

## Existing GitHub Actions repository secrets

The following four repository secrets are already configured. Preserve their existing values; routine project updates do not require recreating or rotating them:

- `RVH_UPLOAD_KEY_BASE64`: entire single-line value from `RVH_UPLOAD_KEY_BASE64.txt` in the private signing bundle.
- `RVH_STORE_PASSWORD`: keystore password from the private bundle.
- `RVH_KEY_ALIAS`: `rvh-music-release`.
- `RVH_KEY_PASSWORD`: key password from the private bundle.

Do not paste these values into issues, commits, workflow logs, or chat. Do not upload the `.jks` file to the repository.

## Build and publish

Push to `main` or manually run **RVH Music — Build Signed Release APK** from the Actions tab. This is the supported production build path; the project is maintained and distributed as a signed Release APK. The workflow restores the keystore only on the runner, builds `assembleRelease`, verifies the APK signature, uploads the `RVH-Music-release-apk` Actions artifact, and publishes `RVH-Music-release.apk` to the rolling `rvh-latest` GitHub Release.

The workflow removes the temporary keystore from the runner after APK verification. GitHub-hosted runners are ephemeral, but the workflow still explicitly removes the file.

## Permanent key custody

Keep at least two secure offline backups of `rvh-music-release.jks` and its passwords. Losing the private key can prevent ordinary in-place updates signed with this identity. Never regenerate or replace this key for future RVH Music updates.

## Versioning

The current version is defined in `app/build.gradle`; the release workflow reads `versionName` and `versionCode` from that file when composing GitHub Release notes. Increase `versionCode` for every subsequent Play Store-compatible release. Keep `applicationId` unchanged.

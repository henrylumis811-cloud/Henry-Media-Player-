# RVH Music v28 — AltarFragment lifecycle compile fix

## CI failure addressed
GitHub Actions reported two Kotlin compilation errors in `AltarFragment.kt`: unresolved reference `isStarted` in delayed visualizer and player-listener retry guards. `Fragment` does not expose an `isStarted` property.

## Correction
- Imported `androidx.lifecycle.Lifecycle`.
- Replaced both invalid `isStarted` checks with `lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)`.
- Kept the existing retry delay, binding checks, attachment guards, and onStop cleanup. The retry callbacks therefore only continue while the Fragment lifecycle is STARTED or RESUMED.
- Incremented versionCode to 17 and versionName to 3.14.

## Verification
- Both unresolved references are removed.
- Both retry guards use the Fragment Lifecycle state API.
- ZIP integrity checked after packaging.
- Android Gradle compilation still requires a new GitHub Actions run; this patch is not represented as a successful build until CI confirms it.

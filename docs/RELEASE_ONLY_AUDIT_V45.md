# Release-only audit checkpoint — v45

App version: **3.28 (versionCode 31)**. This checkpoint contains the sleep-timer cancellation reliability correction documented in `docs_SLEEP_TIMER_AUDIT_V45.md`.

The project remains Release-build only. Existing GitHub Actions signing secret names and the release signing workflow are preserved; no debug build is used as a deliverable or validation substitute. The release workflow continues to use `assembleRelease`, restore the configured private keystore on the runner, verify the signed APK, and publish the Release artifact.

No private keystore or secret values are included in this archive. Android Release compilation, CI signing, and physical-device testing have not been performed in this workspace.

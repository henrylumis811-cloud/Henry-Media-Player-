# RVH Music v34 — Artwork Pipeline Cleanup

- Removed empty adapter `close()` methods that performed no cleanup, plus their obsolete call sites.
- Kept the process-wide artwork loader owned by the shared singleton; screen teardown no longer pretends to close it.
- Replaced the synchronized `ConcurrentHashMap` with a regular map because every access is already guarded by the same monitor.
- Added per-request identity to in-flight artwork work. An older queued worker can no longer remove a newer request registered for the same album key.
- Preserved scroll-aware request suppression, cache reuse, visible-row rebind on idle, and holder identity checks.
- No playback, navigation, layout, or visual design changes.

Version: 3.18 (versionCode 21).

Validation: source-level inspections and ZIP integrity only. Android/Gradle compilation and on-device scrolling still require CI/device confirmation.

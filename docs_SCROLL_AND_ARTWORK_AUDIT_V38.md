# RVH Music v38 — artwork request and group-cover reliability pass

Version: 3.22 (versionCode 25)

## Additional risks found after v37

1. The v37 artwork executor used a fixed two-thread pool with an unbounded work queue. Repeated fast flings could enqueue more decode work than the device could finish promptly.
2. Even with a scroll pause flag, queued tasks could survive a fling and begin after the list had already returned to idle. Those tasks were no longer useful for the current visible rows.
3. Album and artist group artwork trusted the first song. If that track lacked embedded art, a later track in the same group could still contain the correct cover.

## v38 changes

- Replaced the unbounded artwork executor queue with a bounded queue (48 waiting requests, two active workers).
- Capped callback accumulation per coalesced artwork key at 32.
- On scroll pause, invalidate the current request epoch, clear queued obsolete work, and clear stale in-flight coalescing entries. Running decodes may finish, but stale results are not delivered to recycled rows.
- New idle binds can retry requests cleanly after a fling; rejected work also removes its in-flight marker so it cannot become stuck.
- Callback delivery now requires the exact request still to own its key and its scroll epoch to remain current.
- Album/artist groups now try multiple candidate tracks, preferring longer tracks first, rather than relying solely on the first track for embedded artwork.
- Artwork decode requests are track-scoped while successful memory/disk results remain album-scoped. This prevents two visible tracks sharing an album ID from coalescing behind the first track when that first track has no embedded art.
- Disk temporary files are track-unique, avoiding collisions when different tracks from the same album finish decoding concurrently.
- Version advanced to 3.22 / code 25.

## Validation and limits

- ZIP integrity and targeted source consistency checks are required before handoff.
- This workspace does not provide an Android SDK/Gradle build environment, so compilation and signed APK creation remain unverified here.
- Device tests are still required: rapid repeated flings, immediate reverse flings, stopping while requests are queued, Albums/Artists with mixed artwork/no-art tracks, Queue scrolling, and returning to previously visited rows.
- Do not consider scrolling fully fixed until GitHub Actions passes `clean assembleRelease` and the target phone has been tested.

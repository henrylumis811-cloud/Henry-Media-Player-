# Sleep Timer Custom Duration — v40

## User-facing change
- Removed the fixed Off / 15m / 30m / 45m / 60m preset chips.
- Replaced them with independent hour and minute wheel selectors.
- Users can choose any duration from 1 minute through 23 hours 59 minutes.
- Start Timer applies the selected duration; Cancel immediately clears it.
- The initial selection is 00h 00m, so no preset duration is imposed.
- The existing live MM:SS countdown remains visible while active.

## Timer implementation
- SleepTimer accepts a duration in milliseconds and validates the supported range.
- The monotonic elapsed-realtime deadline and boot-count guard remain in place.
- The selected duration is persisted as a Long under a new preference key to avoid a SharedPreferences type collision with v39's integer value.
- Service destruction disposes only the in-memory ticker; it does not erase the persisted deadline.
- Expiry clears timer state before pausing playback; crossfade/transition code is untouched.

## Version
- versionName: 3.24
- versionCode: 27

## Verification
- Layout XML parsed successfully.
- Static checks confirmed fixed preset references were removed and all new view IDs exist.
- Android Gradle compilation and device behavior remain unverified in this environment.

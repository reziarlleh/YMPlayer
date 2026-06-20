# YMPlayer 0.4.1

Debug build for Android 10+ (`minSdk 29`), versionCode 41.

## What changed

- Embedded SideBar edge-swipe and button behavior are aligned with the
  standalone SideBar project; YMPlayer keeps the larger panel buttons.
- Added a settings checkbox for embedded SideBar auto-hide.
- Fixed invisible audio quality buttons in settings.
- Persisted the last player state: source, queue, track, play mode, shuffle
  state, and approximate position.
- Fixed stale MediaSession artwork after shuffle track changes for launchers
  such as CarWebGuru.
- My Wave now expands one next track at a time instead of loading larger
  batches ahead.

## APK

The APK for this release is:

- `YMPlayer-v0.4.1-debug-b41.apk`

SHA-256:

```text
7ED211082518D204BA3A82B1C8DAEEA0FA3B38BC93D173CBEE68E0837D7E6D4F
```

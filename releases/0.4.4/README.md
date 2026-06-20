# YMPlayer 0.4.4

Debug build for Android 10+ (`minSdk 29`), versionCode 44.

## What changed

- Removed the active Poweramp-era cache sync path. Permanent Yandex Music cache
  sync now goes through the standalone YMPlayer repository and stays limited to
  global liked tracks.
- Moved `YandexTrackCache` into the neutral `dev.petrov.yaplay.cache` package
  without changing existing on-device cache directories.
- Deleted unused Poweramp provider/tree-picker classes and obsolete
  `tree_picker_*` strings.
- The main player screen now reads embedded artwork from local files when there
  is no remote cover URL.
- Settings now show whether the YMPlayer Accessibility service for the
  embedded SideBar power menu is enabled.

## APK

The APK for this release is:

- `YMPlayer-v0.4.4-debug-b44.apk`

SHA-256:

```text
518FFA86E49A134BC67F5DF9B5524F1789BB65D7443112D91BB6293764F14F33
```

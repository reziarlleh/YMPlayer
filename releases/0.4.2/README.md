# YMPlayer 0.4.2

Debug build for Android 10+ (`minSdk 29`), versionCode 42.

## What changed

- Embedded SideBar power now opens the Android power/reboot menu through
  YMPlayer's accessibility service instead of sending the TS18 sleep key.
- Added a settings shortcut to Android Accessibility settings for enabling the
  YMPlayer power-menu service.
- Playback position is saved periodically while playing/prepared and also on
  pause, stop, source switch, and service shutdown.
- Selecting My Wave, offline cache, or a playlist now stops current playback
  and waits for Play before starting the selected source.
- Offline cache, Yandex playlists, and local playlists remember their last
  track, index, position, and queue mode.

## APK

The APK for this release is:

- `YMPlayer-v0.4.2-debug-b42.apk`

SHA-256:

```text
A9DA3D540BFA4C3B96C375BA12DA44BE838BDE6993A1627F5DB8CC540FD258DB
```

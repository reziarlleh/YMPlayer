# YMPlayer 0.5.2

Debug build for Android 10+ (`minSdk 29`), versionCode 62.

## APK

- File: `YMPlayer-v0.5.2-debug-b62.apk`
- Version: `0.5.2`
- Build type: `debug`
- SHA-256: `8DEB53DE1603AE39C8C91F2A27404E16BDE1B6CD40D382D6A4D38ACC8488C145`

## Changes

- Hardened Clip Wave startup and one-next preloading against unavailable video
  items.
- Kept clip feedback attached to the correct rotor session across restarts and
  previous/next navigation.
- Added permanent real-cover sidecars for liked-track audio.
- Favorite synchronization now restores missing covers for already cached
  tracks without redownloading valid audio.
- Downloaded audio is not modified, and the YMPlayer logo remains display-only.

## Verification

- `lintDebug` and `assembleDebug`
- APK package/version/minSdk inspection with `aapt`
- APK signature verification with `apksigner`
- Real Yandex account, region, VH stream variants, Clip Wave likes, and offline
  artwork still require testing on the target phone and TS18 head unit.

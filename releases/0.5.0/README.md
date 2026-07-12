# YMPlayer 0.5.0

Debug build for Android 10+ (`minSdk 29`), versionCode 60.

## APK

- File: `YMPlayer-v0.5.0-debug-b60.apk`
- Version: `0.5.0`
- Build type: `debug`
- SHA-256: `536E3458EA6DB0E6B69287E459275919C401C329EA2F7B85107761698C181461`

## Changes

- New full-screen Clip Wave backed by the Yandex Music Android TV video rotor.
- Adaptive HLS/DASH playback through Media3 with official preview fallback.
- Auto-hiding artist/title overlay with previous, play/pause, next, like, and
  close controls.
- Dynamic current-plus-one-next queue, background next-manifest preparation,
  and empty-session restart.
- Clip likes update the linked Yandex Music track and the existing liked-track
  auto-cache.
- Dedicated clip MediaSession with metadata and artwork for system/car
  controls.

## Verification

- `.\gradlew.bat :app:lintDebug :app:assembleDebug --no-daemon`
- `aapt dump badging`: `versionName=0.5.0`, `versionCode=60`, `minSdk=29`
- `apksigner verify --verbose`: APK Signature Scheme v2 verified
- Signing certificate matches YMPlayer 0.4.12, so an in-place update is
  supported.

The video API and stream availability still require validation on the user's
real Yandex account, region, phone, and TS18 head unit. YMPlayer does not bypass
DRM-protected streams.

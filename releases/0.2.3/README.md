# YMPlayer 0.2.3

Archived working 0.2.x build.

- APK: `YMPlayer-v0.2.3-debug-b21.apk`
- Package: `dev.petrov.yaplay`
- Version code: `21`
- Minimum Android: API 29 / Android 10
- Build date: 2026-06-14

Verification:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `apksigner verify --verbose`
- `aapt dump badging`

Notes:

- Library now shows only Yandex Music account playlists from the user's
  collection.
- Removed unconfirmed recommendations shortcuts and the unfinished My Wave
  mood/activity filter.

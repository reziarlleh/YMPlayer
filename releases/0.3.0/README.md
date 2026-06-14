# YMPlayer 0.3.0

Archived working 0.3.x build.

- APK: `YMPlayer-v0.3.0-debug-b30.apk`
- Package: `dev.petrov.yaplay`
- Version code: `30`
- Minimum Android: API 29 / Android 10
- Build date: 2026-06-14

Verification:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `apksigner verify --verbose`
- `aapt dump badging`

Notes:

- Adds current-track insertion into Yandex Music account playlists.
- Adds creation of Yandex Music account playlists from YMPlayer.
- Adds local app-only playlists with file/folder import through Android SAF.
- Adds local playlist playback as a separate source.

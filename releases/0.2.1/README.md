# YMPlayer 0.2.1

Archived working 0.2.x build.

- APK: `YMPlayer-v0.2.1-debug-b19.apk`
- Package: `dev.petrov.yaplay`
- Version code: `19`
- Minimum Android: API 29 / Android 10
- Build date: 2026-06-14

Verification:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `apksigner verify --verbose`
- `aapt dump badging`

Notes:

- Adds the first Library screen and playlist source foundation.
- My Wave filters and Search are UI entry points in this build; backend
  behavior is planned for later 0.2.x builds after endpoint validation.

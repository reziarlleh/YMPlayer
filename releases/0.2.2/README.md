# YMPlayer 0.2.2

Archived working 0.2.x build.

- APK: `YMPlayer-v0.2.2-debug-b20.apk`
- Package: `dev.petrov.yaplay`
- Version code: `20`
- Minimum Android: API 29 / Android 10
- Build date: 2026-06-14

Verification:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `apksigner verify --verbose`
- `aapt dump badging`

Notes:

- Adds car-launcher album-art metadata improvements.
- Adds online next-track prefetch for My Wave and playlist playback.
- Adds EQ/DSP launch support.
- Adds SideBar long-tap power menu request.

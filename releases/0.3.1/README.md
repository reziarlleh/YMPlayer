# YMPlayer 0.3.1

Archived working 0.3.x build.

- APK: `YMPlayer-v0.3.1-debug-b31.apk`
- Package: `dev.petrov.yaplay`
- Version code: `31`
- Minimum Android: API 29 / Android 10
- Build date: 2026-06-14

Verification:

- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `apksigner verify --verbose`
- `aapt dump badging`

Notes:

- Adds best-effort embedded artwork enrichment for local imported files.
- Searches public metadata/artwork sources when the local file has no embedded
  cover.
- Writes artwork into local file tags only when the SAF provider grants write
  access and the audio format is supported by the tag writer.
- Uses local embedded artwork in MediaSession and notification metadata.

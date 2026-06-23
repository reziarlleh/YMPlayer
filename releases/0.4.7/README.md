# YMPlayer 0.4.7

Debug APK for testing the embedded SideBar power button on Android 10+ devices.

## APK

- File: `YMPlayer-v0.4.7-debug-b47.apk`
- Version: `0.4.7`
- Version code: `47`
- Package: `dev.petrov.yaplay`
- SHA-256: `FAF22643E10057854CBDD5131136F6DAAB7DF0D65A78C654991E3E2C1FC7EA8F`

## Changes

- Restored a minimal optional YMPlayer accessibility service only for the
  embedded SideBar power button.
- Added settings status plus a button that opens Android Accessibility settings
  so the service can be enabled manually, like in the standalone SideBar app.
- The SideBar power button first tries Android `GLOBAL_ACTION_POWER_DIALOG`
  through the enabled service, then falls back to the TS18 reboot activity and
  Android shutdown confirmation request.
- The service does not retrieve window content and is not used for playback,
  Yandex Music authorization, search, playlists, or cache behavior.

## Test Notes

On some phones Google Play Protect may still warn about or block debug APKs that
declare an accessibility service. This build is primarily for checking the
SideBar power menu path on the TS18 head unit.

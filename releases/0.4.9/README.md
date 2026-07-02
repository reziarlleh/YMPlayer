# YMPlayer 0.4.9

Debug APK for testing separated embedded SideBar shutdown and reboot buttons on Android 10+ TS18 devices.

## APK

- File: `YMPlayer-v0.4.9-debug-b49.apk`
- Version: `0.4.9`
- Version code: `49`
- Package: `dev.petrov.yaplay`
- SHA-256: `2A6522312DA0CD27D3B5DB6B426E39965C1EABF410590E854570217AD1065F31`

## Changes

- Removed YMPlayer AccessibilityService from the current APK.
- Removed the SideBar power-service status row and the Accessibility settings
  shortcut from YMPlayer settings.
- Changed the original embedded SideBar power button to request shutdown through
  Android `ACTION_REQUEST_SHUTDOWN`.
- Added a separate embedded SideBar reboot button using the confirmed TS18
  reboot UI path from 0.4.8.
- Kept direct `com.nwd.action.ACTION_MCU_POWER_OFF` unused because it is not a
  confirmation request.

## Test Notes

The reboot button should keep the behavior confirmed in 0.4.8. The main new
test is whether the power button opens a shutdown/power-off confirmation on the
TS18 head unit.

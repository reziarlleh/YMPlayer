# YMPlayer 0.4.8

Debug APK for testing embedded SideBar power-button fallbacks on Android 10+ TS18 devices.

## APK

- File: `YMPlayer-v0.4.8-debug-b48.apk`
- Version: `0.4.8`
- Version code: `48`
- Package: `dev.petrov.yaplay`
- SHA-256: `EF73B698F0440BF551B53E02F0BCC9421E40E160DFC2F792A4175380625D42C7`

## Changes

- Kept the working optional YMPlayer accessibility-service path for Android
  `GLOBAL_ACTION_POWER_DIALOG`.
- Added a second TS18 reboot UI candidate:
  `com.nwd.toolallinone.app/com.nwd.tools.reboot.RebootActivity`.
- Added launcher-mediated TS18 start requests through
  `com.nwd.ACTION_REQUEST_START_ACTIVITY`, `com.nwd.action.ACTION_START_ACTIVITY`,
  and `com.nwd.action.ACTION_START_NWD_ACTIVITY` with `extra_package_name` and
  `extra_class_name`.
- Kept Android `ACTION_REQUEST_SHUTDOWN` as the last fallback.
- Did not use direct `com.nwd.action.ACTION_MCU_POWER_OFF` or the old
  `extra_key_value=0` path, because this build tries to open a confirmation UI,
  not immediately power off or sleep the head unit.

## Test Notes

Test the embedded SideBar power button with the YMPlayer accessibility service
both enabled and disabled. The main question for this build is whether the TS18
launcher-mediated request can open the firmware reboot UI after the head unit
turns the accessibility service off.

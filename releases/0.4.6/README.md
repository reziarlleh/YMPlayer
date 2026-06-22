# YMPlayer 0.4.6

Debug build for Android 10+ (`minSdk 29`), versionCode 46.

## What changed

- Added a best-effort embedded SideBar power button without
  `AccessibilityService`.
- First power path: open the TS18 firmware activity
  `com.android.launcher/com.nwd.tools.reboot.RebootActivity`, found in the
  TS18 SystemUI APK.
- Fallback power path: Android `ACTION_REQUEST_SHUTDOWN` confirmation dialog.
- Removed the old TS18 `extra_key_value=0` power fallback because it sleeps the
  display instead of opening the power/reboot menu on the test unit.
- The APK still contains no `AccessibilityService` and no
  `BIND_ACCESSIBILITY_SERVICE`.

## APK

The APK for this release is:

- `YMPlayer-v0.4.6-debug-b46.apk`

SHA-256:

```text
5615F07F30FFD52E3ED2634370090A816B5D17E34A69AF319965776F4F0722DD
```

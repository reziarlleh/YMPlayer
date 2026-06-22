# YMPlayer 0.4.5

Debug build for Android 10+ (`minSdk 29`), versionCode 45.

## What changed

- Removed YMPlayer's `AccessibilityService` after Google Play Protect blocked
  the APK on a Redmi Note 14 Pro.
- The accessibility path was also removed because TS18 firmware can disable
  such services, making the SideBar power-menu button unreliable.
- Kept the embedded SideBar overlay, edge pull-out behavior, volume, mute,
  home, back, and hide controls.
- The embedded SideBar power button is temporarily disabled and shows an
  explanatory message instead of using the removed accessibility shortcut.
- Returned to a single install-safe debug APK.

## APK

The APK for this release is:

- `YMPlayer-v0.4.5-debug-b45.apk`

SHA-256:

```text
B6EC206C284DAC7335D3B6513CA5561FDE8A1F6574D98C262AA99242A5E3F2AE
```

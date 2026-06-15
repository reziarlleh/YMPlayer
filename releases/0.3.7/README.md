# YMPlayer 0.3.7

Debug build for Android 10+ (`minSdk 29`), versionCode 37.

## What changed

- My Wave starts from the first received rotor-session batch instead of waiting
  for a large initial queue.
- After playback starts, YMPlayer preloads the next audio file and loads more
  My Wave batches in the background.
- Settings now include separate quality profiles for online playback cache and
  permanent liked-track cache.

## APK

The APK for this release is:

- `YMPlayer-v0.3.7-debug-b37.apk`

# YMPlayer 0.4.3

Debug build for Android 10+ (`minSdk 29`), versionCode 43.

## What changed

- Fixed sticky artwork in CarWebGuru when a track has no cover URL, no embedded
  local artwork, or a failed cover download.
- MediaSession metadata and notifications now always publish an artwork bitmap:
  the real cover when available, otherwise the YMPlayer launcher icon.
- The main player cover resets to the YMPlayer icon immediately while a new
  cover URL is loading.
- Source selection with no current track also publishes default YMPlayer artwork
  instead of leaving stale MediaSession metadata.

## APK

The APK for this release is:

- `YMPlayer-v0.4.3-debug-b43.apk`

SHA-256:

```text
BA3FCB763EE082EB2F278A8E5D8E8304010B0FC06C1A13EAB38DB14B01F15942
```

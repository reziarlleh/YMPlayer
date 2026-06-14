# YMPlayer

YMPlayer is an unofficial Android 10+ Yandex Music player focused on My Wave,
offline liked tracks, and car-launcher use. It replaces the previous Poweramp
bridge experiment while keeping the useful Yandex Music login, cache, and
diagnostics code.

The package name is still `dev.petrov.yaplay` so test devices can upgrade older
YaPlay builds without losing the saved token or cache.

## What works now

- OAuth device login or manual OAuth token paste.
- Standalone MediaSession/MediaBrowser playback service visible to system media
  launchers such as CarWebGuru.
- Main player screen with cover art, title, album, artist, highlighted source
  selection, large round transport controls, like/dislike beside the title, and
  queue mode cycling for offline playback.
- Passive playlist source selection from the main player screen.
- Swipeable Library screen focused on the user's own Yandex Music collection
  playlists plus local app-only playlists.
- Real Yandex Music search from the Library screen with track, album, and
  artist result playback.
- Add the current Yandex Music track to an existing account playlist, or create
  a new Yandex Music playlist and add the track to it.
- Local playlists stored only in YMPlayer, with audio files or folders imported
  through Android's system file picker for local storage and USB drives.
- Best-effort local artwork enrichment: when an imported local file has no
  embedded cover, YMPlayer can search public metadata sources and write artwork
  into the file tags if the storage provider grants write access.
- Bitmap cover metadata for car launchers plus notification large icon artwork.
- Next-track prefetch for online My Wave and playlist playback.
- EQ/DSP button with optional explicit equalizer package setting.
- TS18-native volume and mute broadcasts with Android `AudioManager` fallback
  for non-TS18 devices.
- My Wave playback through the Yandex Music rotor API with playback feedback,
  background prefetch, and automatic load-more near the end of the current
  queue.
- Offline playback from YMPlayer's permanent liked-track cache.
- Persistent cache sync for the global Yandex Music "Liked tracks" collection.
- Temporary limited playback cache for non-liked stream playback.
- Manual local cache cleanup from the app UI.
- Foreground cache sync service with notification and cancel action.
- Optional cache sync constraints: Wi-Fi only and charger only.
- Copyable in-app diagnostics log for device testing.
- Optional built-in SideBar overlay, enabled with a settings checkbox and
  shown/hidden from the main screen. It reuses the TS18 volume/mute/home/back
  control model from the SideBar project.
- Optional auto-cache for newly liked tracks.
- Android battery/autostart settings shortcuts for devices that aggressively
  stop background media apps.
- New YMP launcher icon preview at `artwork/ymplayer_icon_preview.png`.

## How to use

1. Install `app/build/outputs/apk/debug/YMPlayer-v0.3.3-debug-b33.apk` on an
   Android 10+ device.
2. Open YMPlayer and sign in to Yandex.
3. Use Sync Favorites to pre-download the global "Liked tracks" collection.
4. Start My Wave or Cache directly from the main YMPlayer screen.
5. Swipe to Library to search Yandex Music, select a Yandex Music playlist, or
   create/import a local playlist.
6. In CarWebGuru or another media launcher, choose YMPlayer as the media app if
   it appears in the launcher media source list.

## Cache model

Permanent storage is only for tracks from the global Yandex Music "Liked tracks"
collection. It does not matter where the like was set: My Wave, search, album,
or playlist. During sync, YMPlayer loads that single collection and removes
tracks that are no longer liked.

Tracks that are not liked may be used through a separate temporary playback
cache. This cache is limited and is not treated as user-owned offline storage.
Removing a like deletes that track from the permanent liked cache. Dislike sends
negative feedback, removes the track from favorites if needed, and deletes its
local audio files. Clear local cache removes permanent liked downloads,
temporary playback cache, and cover cache.

Direct reuse of files downloaded by the official Yandex Music app is not
implemented. On Android 10+ those files are inside the official app sandbox and
may be encrypted or transformed.

## My Wave

My Wave is dynamic, not a static playlist. YMPlayer opens a rotor session,
requests an initial queue, starts playback, and prefetches more tracks as the
queue approaches the end. It sends `radioStarted` and `trackStarted` feedback
to keep the radio session alive; if a session load-more unexpectedly returns no
tracks, it opens a fresh My Wave seed instead of treating that as the natural
end of the list. Shuffle is intentionally disabled for My Wave.

## Built-in SideBar

The previous external SideBar keep-alive path is no longer the primary model.
YMPlayer now contains its own overlay service. Enable it in Settings ->
Integrations and grant Android's "draw over other apps" permission when
prompted. The main screen has a quick SideBar button near Settings to show or
hide the panel. The panel keeps larger round TS18 control buttons for power,
volume up/down, mute, home, back, and hide.

## TS18 volume controls

On TS18-like devices, YMPlayer follows the same native control contract used by
the SideBar project: it sends `com.nwd.action.ACTION_KEY_VALUE` with only
`extra_key_value` for mute/volume, or
`com.nwd.can.action.ACTION_PLATFORM_SEND_CAN_VOLUME` when
`can_use_amp_volume_key == 1`. It also requests the native volume display with
the TS18 launcher action `com.nwd.ACTION_REQUEST_VLOUME_DISPLAY`.

If YMPlayer does not detect a TS18 environment, the volume buttons fall back to
standard Android music-stream volume control.

## Diagnostics

The app keeps a small rolling diagnostics log in private storage. Use Copy
diagnostics after failed login, cache sync, My Wave loading, playback, or
SideBar restart attempts. Clear diagnostics resets only this log.

## Login notes

Murglar's FAQ has useful troubleshooting notes for Yandex login/token issues:
https://murglar.app/docs/ru/ru-faq.html#login-issues

YMPlayer currently supports Yandex device login and manual OAuth token paste.

## Build

Use JDK 17 and the Gradle wrapper:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE ".codex\toolchains\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug lintDebug --no-daemon
```

The debug APK is expected at:

```text
app/build/outputs/apk/debug/YMPlayer-v0.3.3-debug-b33.apk
```

## Release history

Working APKs that are useful for rollback or real-device comparison are archived
under `releases/<version>/`. User-facing changes are tracked in `CHANGELOG.md`;
planned work is tracked in `ROADMAP.md`. Do not archive broken intermediate
builds.

## Notes

This project is a personal, unofficial integration. It is intended for use with
the user's own Yandex Music subscription and does not attempt to bypass DRM,
encryption, or Android application sandboxing.

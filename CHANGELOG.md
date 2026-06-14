# YMPlayer Changelog

This file tracks only working, testable versions. Broken or experimental
intermediate builds should not be added here.

## 0.3.3 - 2026-06-14

Search playback foundation.

- Added real Yandex Music search from the Library page.
- Search results now show separate track, album, and artist sections.
- Track results start playback directly as a search queue.
- Album results load album tracks and start album playback.
- Artist results load artist top tracks and start artist playback.
- Added search source labels to the player status area.
- Favorite-artist actions remain in research until the Yandex endpoint is
  validated on a real account.

## 0.3.2 - 2026-06-14

Local playlist control, DSP selection, and SideBar touch polish.

- Renamed the release notes file from `WHATSNEW.md` to `CHANGELOG.md`.
- Added the built-in non-deletable `Local favorites` playlist. Liking a local
  file adds it there; unliking or disliking a local file removes it from there.
- Added local playlist deletion and local favorites clearing with confirmation.
- Added Yandex Music account playlist deletion with confirmation and library
  refresh after successful API deletion.
- Local playlist playback now skips missing or inaccessible files, such as
  tracks from a removed USB drive. If no file in the queue is accessible,
  playback stops instead of retrying forever.
- Reworked the EQ/DSP button to open a chooser of detected DSP/EQ apps instead
  of falling through to Android sound settings.
- Adjusted the built-in SideBar: power tap now sends the same TS18 power key
  broadcast as the standalone SideBar, the edge hotspot is 5 physical pixels,
  and the collapsed handle opens only by swipe.

## 0.3.1 - 2026-06-14

Local artwork enrichment.

- Added best-effort embedded artwork enrichment for imported local files.
- If a local file has no embedded cover, YMPlayer searches public metadata
  sources and tries to write the found artwork into the file tags.
- The process is silent when no artwork is found, there is no internet, the
  provider is read-only, or the format cannot be tagged.
- Local embedded artwork is now also used in YMPlayer's MediaSession metadata
  and notification artwork when available.

## 0.3.0 - 2026-06-14

Playlist editing and local playlists.

- Added a main-player action to add the current Yandex Music track to an
  existing account playlist.
- Added creation of a new Yandex Music account playlist from YMPlayer and
  immediate insertion of the current track.
- Added a separate Local playlists section in Library.
- Added app-only local playlists that can import audio files or scanned folders
  through Android's system picker, including USB/storage providers that grant
  persistent read access.
- Added local playlist playback through the same transport controls, with
  shuffle/repeat available because local lists are static queues.

## 0.2.3 - 2026-06-14

Library cleanup.

- Removed the local "Recommendations" and "Favorites" shortcut blocks from the
  Library screen.
- Left the Library screen focused on real Yandex Music account playlists from
  the user's collection.
- Removed the unfinished My Wave mood/activity filter button from the player
  screen until a stable Yandex Music endpoint is validated.

## 0.2.2 - 2026-06-14

Car UI, SideBar, and responsiveness polish.

- Added bitmap album art into MediaSession metadata and notification large icon
  so car launchers have a better chance to receive cover art.
- Added background prefetch of the next online track for My Wave and playlists.
- Moved expensive cache status calculation off the main UI thread.
- Improved status text contrast on dark surfaces.
- Added an EQ/DSP button; on wide landscape screens it sits in the main
  playback control row.
- Added optional equalizer package setting plus standard Android audio-effects
  panel fallback.
- Added SideBar power-button long tap to request the system power menu when the
  firmware allows it.

## 0.2.1 - 2026-06-14

Library and playlist source foundation.

- Added a passive playlist source button on the main player screen.
- Added playback service support for user playlist queues.
- Added a second Library screen reachable by horizontal swipe.
- Added Library sections for recommendations, favorites, and user playlists.
- Added on-demand loading of user playlists from the Yandex Music account.
- Added playlist selection without automatic playback; Play starts the selected
  playlist when the player is stopped.
- Added a My Wave filter button as a UI entry point for later validated filters.
- Added a Search entry point for the next 0.2.x search implementation.

## 0.2.0 - 2026-06-14

Baseline standalone YMPlayer release.

- Promoted the project from the Poweramp bridge experiment to a standalone
  Android 10+ Yandex Music player.
- Added the main player screen with cover art, track title, artist, album,
  like/dislike controls, large transport controls, and highlighted source
  selection.
- Added My Wave playback through the Yandex Music rotor API with session
  feedback and load-more/prefetch behavior.
- Added offline playback from YMPlayer's own permanent liked-track cache.
- Kept permanent offline storage limited to global Yandex Music liked tracks.
- Added a separate temporary playback cache for non-liked tracks.
- Added cache sync, cache cleanup, diagnostics, Yandex login/token settings,
  and automatic cache of newly liked tracks.
- Added a built-in optional SideBar overlay for TS18-style head units.
- Added Android MediaSession/MediaBrowser playback service support for car
  launchers such as CarWebGuru.
- Added buttons for Android battery and autostart settings.

## Versioning Policy

- Patch versions are for fixes and incremental improvements on the current
  standalone player foundation.
- Minor versions are for significant working additions, such as library/search
  workflows, playlist playback, or validated My Wave filters.
- Archive only working APKs that are worth returning to during real-device
  testing.
- Keep APK file names informative: `YMPlayer-v<version>-<variant>-b<code>.apk`.

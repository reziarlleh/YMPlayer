# YMPlayer Changelog

This file tracks only working, testable versions. Broken or experimental
intermediate builds should not be added here.

## 0.4.3 - 2026-06-20

Artwork fallback fix for CarWebGuru and YMPlayer.

- Fixed sticky artwork in CarWebGuru when the next track has no cover URL, no
  embedded local artwork, or a failed cover download.
- MediaSession metadata and playback notifications now always publish an artwork
  bitmap: the real cover when available, otherwise the YMPlayer launcher icon.
- The main player screen now resets to the YMPlayer icon immediately when a new
  cover URL starts loading, so it no longer shows the previous track cover while
  waiting for a failed or slow cover request.
- Source selection without a current track now also publishes default YMPlayer
  artwork instead of leaving old MediaSession metadata intact.

## 0.4.2 - 2026-06-20

Power menu, playback-position restore, and passive source switching.

- Changed the embedded SideBar power button to use YMPlayer's accessibility
  service and Android `GLOBAL_ACTION_POWER_DIALOG`, matching the working
  power-menu path from the standalone SideBar instead of sending the TS18 power
  key that puts some head units to sleep.
- Added a settings shortcut to Android Accessibility settings for enabling the
  YMPlayer power-menu service.
- Added periodic playback-position persistence while a track is prepared or
  playing, plus an explicit save on pause, stop, source switch, and service
  destroy.
- Source switching now stops the current playback and only selects the next
  source. The selected My Wave/cache/playlist starts when Play is pressed.
- YMPlayer now remembers the last track, queue index, position, and queue mode
  per offline cache, Yandex playlist, and local playlist.

## 0.4.1 - 2026-06-20

Player state, SideBar parity, artwork, and lighter My Wave prefetch.

- Restored the embedded SideBar button behavior and edge-swipe model from the
  standalone SideBar project while keeping YMPlayer's larger panel buttons.
- Added an embedded SideBar auto-hide checkbox in settings.
- Fixed the audio-quality settings buttons being invisible because they were
  laid out with row-only zero-width parameters in the vertical settings list.
- Persisted the current playback source, queue, selected track, play mode,
  shuffle state, like state, and approximate position so the UI can restore the
  last player state after app restart or orientation changes.
- Reset MediaSession and notification artwork per current track before loading
  a new bitmap, preventing stale covers in launchers such as CarWebGuru during
  shuffle playback.
- Reduced My Wave expansion to one next track at a time: YMPlayer starts from
  the first track and only asks the rotor API for the next item when needed.

## 0.4.0 - 2026-06-15

Visible audio quality settings and milestone bump.

- Promoted YMPlayer to 0.4.0 after the accumulated player, search, local
  playlist, My Wave, and cache-quality changes became substantial enough for a
  minor-version milestone.
- Moved audio quality controls into a dedicated `Audio quality` settings
  section near the top of the settings dialog.
- Made online and permanent-cache quality controls more visible with separate
  accent-colored full-width buttons.
- Added an explanatory hint that changed quality applies to new downloads, while
  existing cached files keep their current media quality until cache refresh.

## 0.3.7 - 2026-06-15

My Wave startup and audio quality controls.

- My Wave now starts from the first received rotor-session batch instead of
  waiting until the initial queue is expanded to the full target size.
- After the first track is prepared, YMPlayer preloads the next audio file and
  loads more My Wave batches in the background.
- Added separate quality settings for online playback cache and permanent
  liked-track cache.
- Quality profiles select the closest available Yandex Music download variant:
  Auto/Maximum, Economy 128, Standard 192, and High 320.
- Existing cached files are reused as-is; changed quality applies to new
  temporary downloads and to liked tracks downloaded after cache refresh.

## 0.3.6 - 2026-06-15

Search UI polish.

- Reworked the search entry field into a rounded search bar with the action
  button inside the frame.
- Replaced plain text search results with media-style result cards.
- Added mini cover artwork for track, album, and artist search results.
- Added explicit result actions: play track, add found track to a Yandex Music
  playlist, play album, and play artist top tracks.
- Adding a found track to a playlist can use an existing account playlist or
  create a new Yandex Music playlist.

## 0.3.5 - 2026-06-15

Built-in local media browser.

- Replaced separate local "Add files" and "Add folder" actions with one Add
  action.
- Added YMPlayer's own SAF-based browser for previously granted storage roots.
- The browser shows folders and audio files with checkboxes, supports folder
  navigation, and imports the selected files and folders in one pass.
- Added persistent storage-root history, while keeping Android's system folder
  picker only for the first permission grant to a new local/USB storage root.
- Folder selections still integrate with local playlist folder refresh and
  removed-track exclusions from 0.3.4.

## 0.3.4 - 2026-06-15

Local playlist management polish.

- Added local playlist rename from the Library screen.
- Added a local track list dialog with per-track removal.
- Removed local folder tracks are now remembered as exclusions so they do not
  reappear after folder refresh.
- Folder imports now store their source folder URI for future refreshes.
- Added refresh for imported local folders.
- Local playlist rows now show both track count and imported folder count.

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

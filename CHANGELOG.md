# YMPlayer Changelog

This file tracks only working, testable versions. Broken or experimental
intermediate builds should not be added here.

## 0.4.12 - 2026-07-11

TS18 shutdown confirmation and vendor power-off path.

- Added a YMPlayer-owned confirmation overlay for the embedded SideBar power
  button, so confirmation no longer depends on firmware allowing a third-party
  app to open Android SystemUI GlobalActions.
- Added the NWD privileged system-property bridge found in the factory TS18
  libraries: after explicit confirmation YMPlayer mirrors the factory
  `nwd_system_prop` record and requests
  `sys.powerctl=shutdown,userrequested` through
  `com.nwd.action.ACTION_SET_SYSTEM_PROP`.
- Added guarded direct Android `PowerManager`/`SystemProperties` attempts for
  firmware variants that expose them, plus a launcher-mediated shutdown
  activity request.
- Replaced the old single `extra_key_value=0` fallback with the factory NWD
  power-event sequence `DOWN -> LONGPRESS -> UP` using `extra_key_type`.
- Kept playback code and the already confirmed separate reboot button
  unchanged.

## 0.4.11 - 2026-07-03

Artwork stability after ACC/sleep resume.

- Added a shared artwork cache used by the main screen, playback notification,
  and MediaSession metadata, so CarWebGuru can reuse already downloaded covers
  after the head unit wakes up instead of seeing the YMPlayer logo while the
  network is still recovering.
- Added bounded retry for current-track artwork when a cover request fails
  during wake-up or delayed storage/network availability.
- Decode remote and embedded artwork with a size cap before putting bitmaps into
  UI, notification, and MediaSession metadata to reduce memory pressure and
  binder-size risk on Android car launchers.
- Kept the 0.4.10 SideBar shutdown/reboot buttons unchanged for testing.

## 0.4.10 - 2026-07-03

TS18 shutdown request path from firmware analysis.

- Investigated the provided TS18 3.1 firmware image and confirmed that the
  visible power menu is Android SystemUI `GlobalActions`, while the old SideBar
  power-key fallback only sends the TS18/NWD power key and can put the unit to
  sleep.
- Added non-Accessibility shutdown handling through hidden Android
  `StatusBarManager.showGlobalActions()` /
  `IStatusBarService.showGlobalActionsMenu()` before the old
  `ACTION_REQUEST_SHUTDOWN` fallback.
- Kept the confirmed separate TS18 reboot button and its launcher-mediated
  `RebootActivity` path unchanged.
- Kept direct `com.nwd.action.ACTION_MCU_POWER_OFF` disabled: firmware analysis
  showed it is a power-off state/event broadcast, not a confirmation UI request.

## 0.4.9 - 2026-07-02

Separate SideBar shutdown and reboot buttons, with Accessibility removed again.

- Removed YMPlayer `AccessibilityService`, its manifest declaration, XML
  metadata, settings status, and the button that opened Android Accessibility
  settings.
- Changed the embedded SideBar power button to request shutdown through
  Android `ACTION_REQUEST_SHUTDOWN`.
- Added a separate embedded SideBar reboot button that uses the confirmed TS18
  reboot UI path from 0.4.8.
- Kept direct `com.nwd.action.ACTION_MCU_POWER_OFF` unused because it is a
  direct firmware power-off signal, not a confirmation request.

## 0.4.8 - 2026-07-01

TS18 SideBar power-menu fallbacks that do not rely only on Accessibility.

- Kept the working optional `GLOBAL_ACTION_POWER_DIALOG` path through the
  manually enabled YMPlayer accessibility service.
- Added a second TS18 reboot UI candidate from the factory launcher table:
  `com.nwd.toolallinone.app/com.nwd.tools.reboot.RebootActivity`.
- Added launcher-mediated TS18 start requests for the reboot UI through
  `com.nwd.ACTION_REQUEST_START_ACTIVITY`, `com.nwd.action.ACTION_START_ACTIVITY`,
  and `com.nwd.action.ACTION_START_NWD_ACTIVITY` with `extra_package_name` and
  `extra_class_name`.
- Kept the Android `ACTION_REQUEST_SHUTDOWN` fallback, but still avoids direct
  MCU power-off and the old `extra_key_value=0` sleep path.

## 0.4.7 - 2026-06-23

Optional SideBar power-menu service.

- Restored a minimal optional YMPlayer accessibility service only for the
  embedded SideBar power button.
- The service is not enabled automatically and is not required for normal
  player playback. Settings show its current status and open Android
  Accessibility settings, matching the standalone SideBar flow.
- The power button first uses Android `GLOBAL_ACTION_POWER_DIALOG` when the
  service is enabled, then falls back to the TS18 reboot activity and Android
  shutdown confirmation request.
- The service does not retrieve window content and is not used for playback,
  library, Yandex Music authorization, or cache behavior.

## 0.4.6 - 2026-06-22

Best-effort SideBar power menu without AccessibilityService.

- Added a non-accessibility SideBar power path based on the TS18 SystemUI
  finding: first YMPlayer tries to open
  `com.android.launcher/com.nwd.tools.reboot.RebootActivity`.
- If the TS18 reboot activity is unavailable or blocked by firmware, YMPlayer
  falls back to Android's hidden `ACTION_REQUEST_SHUTDOWN` confirmation dialog.
- Removed the old TS18 `extra_key_value=0` power fallback from YMPlayer because
  on the test head unit it turns the screen off instead of opening the power
  menu.
- The APK still contains no `AccessibilityService` or
  `BIND_ACCESSIBILITY_SERVICE`.

## 0.4.5 - 2026-06-22

Install-safe build after Google Play Protect blocked the APK.

- Removed `AccessibilityService` from YMPlayer. It triggered Google Play
  Protect hard blocking on a Redmi Note 14 Pro and was unreliable on TS18 head
  units because the firmware can disable accessibility services.
- Kept the embedded SideBar overlay, edge pull-out behavior, volume, mute,
  home, and back controls.
- The embedded SideBar power button is temporarily disabled in this build and
  shows an explanatory message instead of using an unreliable accessibility
  shortcut.
- Kept version and APK names informative: `YMPlayer-v0.4.5-debug-b45.apk`.

## 0.4.4 - 2026-06-20

Legacy cleanup and local artwork polish.

- Removed the active Poweramp-era cache sync path: `CacheSyncService` now uses
  the standalone YMPlayer repository and keeps permanent sync limited to global
  liked tracks.
- Moved `YandexTrackCache` into the neutral `dev.petrov.yaplay.cache` package
  while preserving the existing on-device cache directories.
- Deleted unused Poweramp provider/tree-picker classes and obsolete
  `tree_picker_*` strings.
- The main player screen now reads embedded artwork from local files when there
  is no remote cover URL.
- Settings now show whether the YMPlayer Accessibility service needed for the
  SideBar power menu is enabled.

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

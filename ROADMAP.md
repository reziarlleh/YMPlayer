# YMPlayer Roadmap

## 0.2.x Stabilization

- Keep the current standalone player behavior stable while the source model is
  expanded.
- Introduce a shared playback source model for My Wave, offline cache,
  playlists, search results, albums, and artists.
- Keep source switching passive: selecting a source must not automatically
  start playback.
- Preserve Android 10+ compatibility (`minSdk 29`) for all additions.

## Completed In 0.2.1

- Added the first playlist source model for user playlists.
- Added a second Library screen reachable by horizontal swipe.
- Added Library entries for Yandex Music account playlists.
- Added a main-screen playlist selector that does not auto-start playback.

## Completed In 0.2.3

- Removed unconfirmed recommendations and local shortcut blocks from Library.
- Removed the unfinished My Wave mood/activity filter UI.
- Clarified that "My playlists" means playlists from the user's Yandex Music
  collection, not local YMPlayer-only lists.

## Completed In 0.3.0

- Added current-track actions for Yandex Music account playlists.
- Added creation of a Yandex Music playlist and immediate insertion of the
  current track.
- Added a separate Local playlists section for app-only playlists.
- Added local file and folder import through Android's system picker with
  persistent read permissions for local storage and USB providers.
- Added local playlist playback through YMPlayer's playback service.

## Completed In 0.3.1

- Added best-effort cover enrichment for local files that do not already have
  embedded artwork.
- Added public metadata lookup for local artwork and tag writing when storage
  providers grant write access.
- Added local embedded artwork extraction for MediaSession and notification
  artwork.

## Completed In 0.3.3

- Added real Yandex Music search from the Library page.
- Added track result playback.
- Added album result playback through album track queues.
- Added artist result playback through top-track queues.

## Completed In 0.3.4

- Added local playlist rename.
- Added local playlist track viewing and per-track removal.
- Added imported-folder refresh for local playlists.
- Added folder source tracking and per-track exclusions so refresh does not
  restore tracks the user removed from a local playlist.

## Completed In 0.3.5

- Replaced separate local file/folder import buttons with a single Add action.
- Added a built-in checkbox browser for saved Android SAF storage roots.
- Kept the system folder picker only as the Android permission-grant step for
  new storage roots, including USB drives.

## Completed In 0.3.6

- Polished the search dialog with a rounded search bar.
- Added mini cover artwork to track, album, and artist search result rows.
- Added explicit search-result actions, including adding a found track to a
  Yandex Music playlist.

## Completed In 0.3.7

- Changed My Wave startup to play the first received batch immediately.
- Moved extra My Wave batch loading and next-track audio preloading to
  background work after playback starts.
- Added separate quality profiles for online playback and permanent liked-track
  cache downloads.

## Completed In 0.4.0

- Promoted the app to the 0.4 line after substantial player, library, search,
  local playlist, My Wave, and cache-quality work.
- Moved audio quality controls to a dedicated top-level settings section.
- Made online and permanent cache quality selection visually prominent.

## Completed In 0.4.1

- Restored embedded SideBar edge-swipe and button behavior from the standalone
  SideBar implementation, keeping YMPlayer's larger panel buttons.
- Added a settings checkbox for SideBar auto-hide.
- Fixed invisible audio quality buttons in settings.
- Added persisted player state for the current source, queue, selected track,
  play mode, and playback position.
- Fixed stale MediaSession artwork after shuffle track changes.
- Reduced My Wave queue expansion to one next track instead of larger batches.

## Completed In 0.4.2

- Moved embedded SideBar power-menu handling to YMPlayer's accessibility
  service so the button opens the system power/reboot dialog instead of sending
  a TS18 sleep key.
- Added periodic and lifecycle-based playback-position persistence.
- Made source switching passive: selecting My Wave, cache, or a playlist stops
  current playback and waits for Play before starting the selected source.
- Added remembered last track, index, position, and queue mode per offline
  cache, Yandex playlist, and local playlist.

## Completed In 0.4.3

- Fixed sticky cover art in CarWebGuru by always publishing either real artwork
  or the YMPlayer default icon in MediaSession and notifications.
- Reset the main player cover to the default icon immediately while a newly
  selected track cover is loading.

## Completed In 0.4.4

- Removed the active Poweramp-era cache synchronization path and routed liked
  cache sync through the standalone YMPlayer repository.
- Moved the reusable Yandex track cache class into a neutral cache package
  without changing existing storage directories.
- Deleted unused Poweramp provider/tree-picker classes and obsolete strings.
- Added embedded local-file artwork loading on the main player screen.
- Added a visible settings status for the YMPlayer Accessibility service used
  by the embedded SideBar power menu.

## Completed In 0.4.5

- Removed YMPlayer's AccessibilityService after Google Play Protect blocked the
  APK and TS18 reliability concerns made the service unsuitable as a core
  dependency.
- Kept the embedded SideBar overlay and regular controls, but temporarily
  disabled the SideBar power-menu action.
- Returned to a single install-safe debug APK instead of requiring users to
  choose between safe and full builds.

## Completed In 0.4.6

- Added a best-effort SideBar power path without AccessibilityService.
- Used the TS18 SystemUI finding that the firmware quick settings open
  `com.android.launcher/com.nwd.tools.reboot.RebootActivity`.
- Added Android `ACTION_REQUEST_SHUTDOWN` as a fallback.
- Removed the old TS18 key-value power fallback because it sleeps the display
  instead of opening the power menu on the test unit.

## Completed In 0.4.7

- Restored a minimal optional accessibility service for the embedded SideBar
  power button, matching the standalone SideBar flow.
- Added settings status plus a button that opens Android Accessibility settings
  for manually enabling the YMPlayer service.
- Kept the service narrow: no window-content retrieval, no automatic enable
  attempt, and no dependency for playback or Yandex Music features.
- Power button order is now accessibility `GLOBAL_ACTION_POWER_DIALOG`, TS18
  `RebootActivity`, then Android `ACTION_REQUEST_SHUTDOWN` fallback.

## Completed In 0.4.8

- Added TS18 power-menu fallbacks for cases where the head unit disables the
  optional accessibility service.
- Added the second factory reboot UI component
  `com.nwd.toolallinone.app/com.nwd.tools.reboot.RebootActivity`.
- Added launcher-mediated start requests through
  `com.nwd.ACTION_REQUEST_START_ACTIVITY`, `com.nwd.action.ACTION_START_ACTIVITY`,
  and `com.nwd.action.ACTION_START_NWD_ACTIVITY` with `extra_package_name` and
  `extra_class_name`.
- Avoided direct `ACTION_MCU_POWER_OFF` and the known TS18 sleep key path because
  the desired behavior is a confirmation request, not immediate power-off/sleep.

## Completed In 0.4.9

- Removed the current AccessibilityService implementation, manifest entry, XML
  metadata, settings status, and Accessibility settings shortcut.
- Kept the confirmed TS18 reboot request path and moved it to a separate
  embedded SideBar reboot button.
- Changed the original embedded SideBar power button to request shutdown through
  Android `ACTION_REQUEST_SHUTDOWN`.
- Kept direct `ACTION_MCU_POWER_OFF` disabled because it is not a confirmation
  request.

## Completed In 0.4.10

- Investigated the provided TS18 3.1 firmware image for a shutdown request path.
- Confirmed SystemUI `GlobalActions` is the firmware power-menu UI and old
  SideBar's reliable power dialog depended on `GLOBAL_ACTION_POWER_DIALOG`.
- Added non-Accessibility `StatusBarManager` /
  `IStatusBarService.showGlobalActionsMenu()` attempts before the old Android
  `ACTION_REQUEST_SHUTDOWN` fallback.
- Left the working separate TS18 reboot button unchanged.
- Kept direct `ACTION_MCU_POWER_OFF` disabled because firmware analysis shows it
  is a power-state event, not a confirmation UI.

## Completed In 0.4.11

- Added a shared artwork cache for the main screen, notification, and
  MediaSession metadata.
- Added bounded artwork retry after wake-up/network/storage timing failures.
- Downsampled remote and embedded artwork before passing bitmaps to Android
  UI, notification, and MediaSession surfaces.
- Kept the 0.4.10 TS18 SideBar shutdown/reboot behavior unchanged for the next
  real-device test.

## Completed In 0.4.12

- Added an app-owned SideBar shutdown confirmation overlay.
- Added the factory NWD `ACTION_SET_SYSTEM_PROP` bridge for
  `sys.powerctl=shutdown,userrequested` after explicit confirmation.
- Added guarded Android shutdown fallbacks and a typed NWD
  `DOWN -> LONGPRESS -> UP` power-event sequence.
- Kept the confirmed reboot button and playback implementation unchanged.

## Planned 0.4.x Features

- Keep 0.4.x focused on stabilization while the user tests 0.4.12 on a real
  device.
- Current audit and prioritized fix list: [PROJECT_AUDIT.md](PROJECT_AUDIT.md).
- Next cleanup priority is reducing stale background UI updates with a shared
  executor and simple cancellation tokens for search/import/source-loading
  operations.
- Confirm on device that the 0.4.12 SideBar shutdown path powers off the TS18
  after the YMPlayer confirmation instead of only putting the display to sleep.
- Improve local playlist management with optional folder diff details and
  better large-playlist editing ergonomics.
- Add a favorite-artist action only when a stable Yandex Music endpoint is
  confirmed.

## Research Items

- My Wave mood/activity filters.
- My Wave seeded by track or artist.
- Favorite artist add/remove endpoints.

These features depend on unofficial Yandex Music API behavior and must be
validated on a real account before they are treated as stable.

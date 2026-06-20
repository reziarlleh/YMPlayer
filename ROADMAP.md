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

## Planned 0.4.x Features

- Improve local playlist management with optional folder diff details and
  better large-playlist editing ergonomics.
- Add a favorite-artist action when a stable Yandex Music endpoint is confirmed.

## Research Items

- My Wave mood/activity filters.
- My Wave seeded by track or artist.
- Favorite artist add/remove endpoints.

These features depend on unofficial Yandex Music API behavior and must be
validated on a real account before they are treated as stable.

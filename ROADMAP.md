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

## Planned 0.3.x Features

- Improve local playlist management with optional folder diff details and
  better large-playlist editing ergonomics.
- Add a favorite-artist action when a stable Yandex Music endpoint is confirmed.

## Research Items

- My Wave mood/activity filters.
- My Wave seeded by track or artist.
- Favorite artist add/remove endpoints.

These features depend on unofficial Yandex Music API behavior and must be
validated on a real account before they are treated as stable.

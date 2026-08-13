# Changelog

All notable changes to ABC Music Manager are documented here. Most recent at the top.

## Unreleased

- Replaced Set Play **Broadcast** rooms with **named sessions** (D1 registry, per-session Durable Objects, R2 zips up to 2 MB)
- Moved relay URLs, tokens, and zip retention into SQLite schema **v13** (opening a Python v12 DB in Java migrates and will not round-trip)
- Set Play / Band Assistant / browser `/playback` now use **Sessions (or Connect) | Playback | Parts** inner tabs
- Share **Play Only** (`/playback?set=CODE`) vs **Download and Play** (`#p=PIN`); member PIN is zip-only and shown once
- Relay HTTP uses `Authorization: Bearer` (relay token); WebSocket protocol is `set_play_state_v2`
- Redeploy wizard empties R2 zips from the D1 session list before deleting the worker, Durable Objects, D1, and R2 bucket
- Set Play zip upload/download default to the configured Sets folder; downloaded files use the set name
- Compact Band Assistant web header (inline tabs, sticky header and Current/Next), download ZIP in the header, 8-tint Parts columns with a dim/selected left accent, and an Instruments needed popup
- Browser `/playback` Playback tab pins the band layout to the bottom (resizable splitter; cards scale to fit); only Your players and the setlist scroll
- Set Play / Band Assistant Parts **Players** control matches **Your players** on Playback (same selection, linked); web Parts also has **Selected players only**
- Band Assistant **Download ZIP** shows **Extracted folder name** (defaults to the session name; editable)

## Version 0.3.2b

- Added MIDI panic (double-click Stop) to silence hanging notes
- Added User Guide with in-app viewer
- Added Help → About (version, MIT license, third-party credits)
- Aligned Maven project version with 0.3.x numbering
- Updated developer docs for Java as the active full-function edition (parity inventory, README, architecture, storage notes)
- Added drag-and-drop (and Move up / Move down) to rearrange the ABC playback queue
- Added saving the current playback queue as a setlist
- Added a Layouts tab on Song detail to assign parts per band; matching layouts are copied onto a setlist when the song is added and then stay independent

## Version 0.3.1b

- Fixed an issue with relay workers not being able to be deployed from built executables due to the required scripts and worker not being bundled with the app.

## Version 0.3.0b

- First Java-based full-function port of the former Python version
- Uses same/similar UI elements as Maestro and ABC Player
- Utilizes the same playback engine and soundfont used by Maestro and ABC Player (used under MIT License). Result is a sound that is nearly identical to that of Maestro and ABC Player themselves.

## Version 0.2.9b

- Changed relay + code model for set playback/band assistant sharing to a single link model
- Set playback links provided by band leaders can either be pasted into the app (Band Assistant page) or be opened directly in the browser. NOTE: existing relays created before v0.2.9b will need to be redeployed.
- Fix in Windows .exe: Set selection in playback mode should no longer close the dropdown popup when opening a folder

## Version 0.2.8b

- Fixed a Foreign key error that was caused by re-exporting a file that was named incorrectly, causing file name, song name, and export timestamp to all change.

## Version 0.2.7b

- Setlist Editor
  - FIX: Clear Setlist will now also refresh the view, showing that the list was cleared.
  - FIX: Adding new setlist puts new set at the top of current folder instead of bottom.
- Band Editor
  - FIX: Changing a player assignment no longer requires delete and add new, removing the need to reassign parts to the updated player

## Version 0.2.6b

- ABC Playback
  - Fixed error preventing playback in compiled executable

## Version 0.2.5b

- ABC Playback:
  - Corrected ABC to Midi velocities
  - Added limiter/compresser support to reduce clipping during playback

## Version 0.2.4b

- Documentation:
  - ADD: Structured in-app User Guide (Help → User Guide); README trimmed to point at the guide.

## Version 0.2.3b

- Set Play:
  - FIX: Part Change Highlighting is now based on next selected song vs. currently playing song, not next selected song vs. previous song in the list.

## Version 0.2.2b

- Setlist and Set Play
  - FIX: Setlist Editor was not fully deleting sets if that set had a Set Play playback history, leaving behind an empty set instead
  - FIX: Newly created setlists were not showing up in Set Play until app restart (Due to list only refreshing on startup)
  - FIX: Set Play no longer requires a band layout for sets to be loaded.
  - ADD: Ability to clear a setlist
  - IMPROVE: Set Play Set selection combo box now has a tree view for folders and sets
  - IMPROVE: Replaced "Add Song" dialogue with a proper, filterable song table similar to Library view

## Version 0.2.1b

- Added: Set Play feature
  - Select and load setlist
  - Broadcast set playback status via a user-created Cloudflare Relay (Cloudflare account required; even heavy usage under normal circumstances will never come close to exceeding free-tier usage)
  - Cloudflare Relay URL defined in Settings; multiple relays can be defined and used for different bands
  - Song list with checkboxes for skip, next, Current, and Played
  - Advancing a song marks the currently selected Current song as Played, the currently selected next song as Current, and the next song in the setlist that is not marked skip as next
  - Band layout with part change highlighting
  - List of band member names; selecting member(s) highlights them on the band layout (not broadcast; client only)
  - Option to automatically mark songs played in Library Playback History
- Added: Band Assistant
  - Select relay and enter the seven-character code from the band leader, then Connect
  - Read-only view of what the band leader is broadcasting, including the song list with skip, next, current, and played fields and the band layout with instrument change highlighting
  - List of band member names; selecting member(s) highlights them on the band layout (not broadcast; client only)
- Added: Settings → Set Playback tab
  - Add band-leader-provided relay URLs for coordinated set playback
  - Generate a WebSocket relay URL to share with band members also using the app
  - Redeploy a Cloudflare Relay in the unlikely event that Cloudflare resource limits are reached
- Note: Creating a relay requires a free Cloudflare account and must be set up and logged in before launching the relay setup wizard. At the appropriate step, the wizard opens the system default browser to authenticate with Cloudflare. A relay can only be created after this authentication completes.

## Version 0.1.11b

- Added: Optional Part Renaming for Exported Sets using customizable pattern
- Added: Setlist export now optionally exports an ABC Player compatible playlist
- Added: String Find and Replace during CSV export to allow for shortening part names
- Added: CSV now exports with an appendix, listing players and required instruments for the set
- Fixed: Student Fiddle now properly named Student's Fiddle

## Version 0.1.10b

- Added part hinting to setlist editor:
  - Previous and next part numbers in player card headers
  - Tooltip on card indicating last instrument player used
  - When instrument differs from previous song that a player played in, part number is colored orange
- Tooltip over warning icon in setlist editor indicating what is triggering the warning to be present
- Fixed table column widths in setlist editor not always restoring correctly
- Fixed issue with window size and position not always returning to previous state

## Version 0.1.8b

- Fixes for Setlist not allowing delete of songs or setlist after files moved
- Setlist copy menu:
  - Clarified appending and prepending setlists by renaming them to append to/prepend to setlist
  - Added Prepend/append from setlist
  - Added Copy setlist
- Player level cap increased from 150 to 250 (some level of future-proofing)
- Song import process enhancements (further iteration planned after tester feedback)

## Version 0.1.7b

- Temporary icon set

## Version 0.1.6b

- Updated documentation to fix errors and include AI/LLM tool use transparency.

## Version 0.1.5b

- Further addressing of Setlist item move crash

## Version 0.1.4b

- Fixed a bug that was causing songs that were dragged and dropped in Setlist Editor to lose action column buttons
- Addressed a crash condition with setlist song drag and drop

## Version 0.1.3b

- Custom order (drag and drop) for bands
- Duplication of bands
- Schema documented in SCHEMA.md. Migration logic created for future updates to be able to port database forward across larger version gaps
- Minor bugs fixed.

## Version 0.1.2b

- Fixed: Status and Transcriber filters failed to open after first use.

## Version 0.1.1b – Initial Beta test

# Library

The **Library** tab is your main song browser: filter, sort, play, edit metadata, and add songs to setlists.

---

## Library table columns

| Column | Description |
|--------|-------------|
| **Actions** | **Play** (left half) starts playback with this song; **Add to queue** (right half) appends without stopping |
| **Title** | Song title (from ABC / Maestro tags) |
| **Composer(s)** | Composer line |
| **Duration** | Length (mm:ss) |
| **Last played** | Most recent play log entry |
| **Playback History** | Quick actions: mark played now, set play time, open history log |
| **Parts** | Number of ABC parts (hover to see part numbers and names) |
| **Rating** | Click stars to set 1–5 (click again to clear) |
| **Set** | Shows if the song is in an upcoming set; click to jump to setlist |
| **Status** | Custom status badge; click to change |
| **Transcriber** | Person who transcribed the song |
| **Edit** | Open [Song detail](song-detail-and-layouts.md) |

Click column headers to sort (where supported). Right-click a column header to show or hide columns. Column widths, order, and hidden columns are remembered across sessions.

The same Library columns can be turned on in the [setlist editor](setlists.md#timing) and on the bandleader’s [Set Play](set-play.md#status) list. Status, rating, playback history, and last played always refer to the original song. Band Assistant does not show them.

---

## Filters {#filters}

### Main filter row

- **Title / Composer** — text search across both fields
- **More Filters** — shows or hides the side filter panel
- **Reset Filters** — select and apply the **Default** preset from [Library Filters](settings/default-filters.md)
- **Clear Filters** — select **None** and show everything (most permissive)

### Filters panel (Status / In set / Rating and more)

When **More Filters** is open, the side panel includes:

- **Preset** — **Default**, **None**, then any presets you have saved. After you change filters, the box shows `<- Modified ->` until you load a preset again
- **Save To...** — overwrite **Default** or a named preset, or type a new preset name
- **Status** — one or more statuses
- **In set** — Yes / No / Either
- **Rating** — from / to range
- **Duration** — min/max range
- **Last played** — time or date range (including Never)
- **Parts** — min/max part count
- **Transcriber** — multi-select

**None** matches **Clear Filters**, including clearing Title / Composer. Other presets do not change that search box. Manage preset names and values in [Library Filters](settings/default-filters.md).

If the table looks empty, try **Clear Filters** or the **None** preset before assuming the library has no songs.

---

## Inline actions

- **Actions** — Play or Add to queue
- **Playback History → Now** — mark played at the current time
- **Playback History → Set…** — set most recent playback time
- **Playback History → History…** — open the playback history dialog
- **Rating stars** — set rating in place
- **Set column** — menu to open containing setlist(s)
- **Status column** — pick a status from your configured list
- **Edit** — open [Song detail](song-detail-and-layouts.md)

---

## Context menu

Right-click a row. If that row is already part of a multi-selection, the menu keeps the selection and the actions below apply to every selected song, from the top of the table downward. Right-click a row that is not selected to act on that song alone. **Edit** always opens the song under the pointer.

- **Play** — start playback with the first selected song and append the rest to the queue
- **Add to queue** — append the selected songs without replacing the current playlist. Open **Parts / Playlist** on the [playback bar](playback.md#playlist) to rearrange the queue or save it as a setlist.
- **Edit** — open Song detail for the song under the pointer
- **Rating** — set 1–5 stars, or **Clear**. Choosing the rating those songs already share clears it.
- **Status** — set the status on every selected song
- **Playback History**
  - **Played Now** — log a play at the current time
  - **Set Last Played Time...** — log a play at one chosen time for every selected song
  - **History** — open the playback history dialog when one song is selected
- **Add to setlist** — add the selected songs to an unlocked setlist. **New Set...** at the bottom (under a divider) creates a set and adds them.

---

## Empty library state {#empty-library}

If no songs match your filters, you see an empty-state message with **Open User Guide**.

If you truly have no indexed songs, follow [First-time setup](getting-started.md#getting-started-checklist).

---

## Double-click

Double-click a row to open **Song detail**.

---

## Play history

From the Library **Playback History** column you can:

- Mark a song as played now
- Set last played to a specific time
- Open the full play history editor

Song detail also exposes play history actions. Set Play can auto-log plays when advancing songs ([Set Play](set-play.md)).

---

[← User Guide home](index.md) · [Playback](playback.md) · [Setlists](setlists.md)

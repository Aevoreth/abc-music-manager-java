# Bands

The **Bands** tab has two sub-tabs: **Bands** (rosters and layouts) and **Players** (characters and instruments).

---

## Concepts

| Term | Meaning |
|------|---------|
| **Band** | A named group (e.g. your regular ensemble) |
| **Player** | A character with instrument capabilities |
| **Band layout** | Grid placement of players for a performance formation |
| **Song layout** | Maps parts to players for one song + one band (edited on the song’s Layouts tab) |

Part assignment for a song can be prepared in [Song detail → Layouts](song-detail-and-layouts.md#song-layouts). Show-specific copies are edited on [Setlists](setlists.md#set-part-assignment).

---

## Bands tab {#band-layouts}

### Band list

- **Add Band** — create a new band
- **Duplicate** — copy the selected band
- Drag bands in the list to reorder

### Band editor

- **Name** and **Notes**
- **Save** / **Delete**
- **Band members** — assign players from your roster
- **Band layouts** — one or more layout grids per band

### Layout grid

Drag player cards onto a snapped grid. Cards use an internal grid size for placement.

Right-click a card and choose **Change Player** to swap the character on that slot while keeping part assignments for songs that use this layout.

Layouts are reused across songs and setlists. A setlist can reference one band layout for part assignment and Set Play display.

Unsaved name/notes changes may prompt when you leave the page.

---

## Players tab {#players}

Manage **Characters** (players):

- Create or edit — name, instruments, proficiency
- Add players to the current band from the Bands tab

Instrument proficiency indicates whether a player can perform on an instrument class and its variants (e.g. all fiddles).

---

## Using layouts elsewhere

| Feature | Needs band layout? |
|---------|-------------------|
| Setlist part UI | Optional but recommended |
| Set Play band grid | Optional; part highlighting needs assignments |
| Playback stereo (`band_layout` mode) | Uses layout positions when that stereo mode is selected |

Setlists and Set Play can load without a band layout, but part-assignment features are unavailable unless one is defined.

---

[← User Guide home](index.md) · [Setlists](setlists.md) · [Song detail](song-detail-and-layouts.md)

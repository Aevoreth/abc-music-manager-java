# Setlists

The **Setlists** tab organizes folders, individual sets, songs, timing, and exports.

---

## Setlist tree

Left panel: folders and setlists. Drag to reorder or move between folders.

**Toolbar:**

| Button | Action |
|--------|--------|
| **Add folder** | Create a folder in the tree |
| **Add setlist** | Create a setlist (opens the details dialog) |
| **Import ABCP** | Import a local ABC Player playlist (see below) |
| **Delete** | Delete the selected folder or setlist |
| **Export** | Open [Set export](set-export.md) for the selected setlist |

**Set context menu** (right-click a setlist):

- **Duplicate setlist...**
- **Prepend to...** / **Append to...** — copy this set’s songs onto another set
- **Prepend from...** / **Append from...** — pull songs from another set into this one
- **Export set...** — open [Set export](set-export.md)
- **Export to ABCP...**
- **Lock setlist** / **Unlock setlist**
- **Delete setlist...**

Folders have no separate context menu; use the toolbar to add or delete them.

---

## Set details {#create-setlist}

When a set is selected, the right side shows read-only details plus **Edit**, **Export**, and delete (trash).

Click **Edit** to open the setlist details dialog (**Edit setlist** / **New setlist** / **Duplicate setlist**):

| Field | Purpose |
|-------|---------|
| **Set Name** | Setlist title |
| **Band Layout** | Optional; enables part UI and Set Play grid |
| **Set Date** / **Time** | Event scheduling info |
| **Target Duration** | Planned length of the set |
| **Switch Delay (s)** | Default seconds between songs (changeover) |
| **Set Notes** | Free text |
| **Locked (songs and order cannot be edited)** | Prevents song list edits; also excludes the set from Library **Add to setlist** |

Confirm with **OK**. There is no separate Save button on the main panel.

You can also create a setlist from the current ABC playback queue: open **Parts / Playlist** on the [playback bar](playback.md#playlist) and choose **Save as setlist…**. The new set is added under Unfiled.

On the details panel, computed timing shows **Raw Duration**, **Actual Duration** (with delays), and **Time remaining**. A **Locked** badge appears when the set is locked.

---

## Songs in set {#timing}

- **Add song** — filterable picker (similar to Library)
- **Remove** — remove the selected song(s)
- **Move up** / **Move down** — reorder (drag-and-drop also works when unlocked)
- Play icon (first column) — preview from this song in the set
- Warning icon (when present) — hover for part-assignment issues

Song list edits for unlocked sets are applied as you go.

---

## Part assignments {#set-part-assignment}

Available when a band layout is selected for the set (panel titled **Part assignments**):

- Select a song in the set's song list
- Click a player card and assign a part for that song (one part per player)
- Duplicate parts highlight in red
- Orange text can mean a part change from the previous song (normal) or that the player lacks proficiency for that instrument
- If assignments have issues, a warning icon may appear in the song list; hover for details

Completing assignments for all songs in the set gives the most value for Set Play and export.

---

## Import ABCP {#import-abcp}

**Import ABCP** loads Maestro / ABC Player playlist files (`.abcp`).

Before import, the app shows a notice: ABCP import is intended for playlists **you created locally** in ABC Player (paths must match songs already indexed in your library). It is not a shared-setlist interchange format.

See [Import and export formats](import-export.md).

---

## Locked setlists {#locked-setlists}

When locked:

- Songs and order cannot be edited on the Setlists page
- The set does not appear in the Library context menu **Add to setlist**

Toggle lock from the set tree context menu or the details dialog checkbox.

---

[← User Guide home](index.md) · [Set export](set-export.md) · [Set Play](set-play.md)

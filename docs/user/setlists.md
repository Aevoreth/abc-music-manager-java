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
| **Set Code** | Optional short code for `$SetCode` on [set export](set-export.md) (filenames and part renaming); use it to tag songs and limit duplication issues within a songbook |
| **Band Layout** | Optional; enables part UI and Set Play grid |
| **Set Date** / **Time** | Event scheduling info |
| **Target Duration** | Planned length of the set |
| **Switch Delay (s)** | Default seconds between songs (changeover) |
| **Set Notes** | Free text |
| **Locked (songs and order cannot be edited)** | Prevents song list edits; also excludes the set from Library **Add to setlist** |

Confirm with **OK**. There is no separate Save button on the main panel.

You can also create a setlist from the current ABC playback queue: open **Parts / Playlist** on the [playback bar](playback.md#playlist) and choose **Save as setlist…**. The new set is added under Unfiled.

On the details panel, computed timing shows **Raw Duration**, **Actual Duration** (with delays), and **Time remaining**. A **Locked** badge appears when the set is locked.

**Optimize instruments** and **Undo last optimization** sit under the set name. See [Optimize instruments](#optimize-instruments).

---

## Songs in set {#timing}

- **Add song** — filterable picker (similar to Library)
- **Remove** — remove the selected song(s)
- **Move up** / **Move down** — reorder (drag-and-drop also works when unlocked)
- Play icon (first column) — preview from this song in the set
- Warning icon (when present) — hover for part-assignment issues
- **Library columns** — right-click a column header to show or hide columns from the [Library](library.md) table: **Last played**, **Playback History**, **Rating**, **Set**, **Status**, **Transcriber**, and **Edit**. They are hidden until you turn them on. Status, rating, playback history, and last played read and update the original song, so a change here shows up in the Library as well. **Set** jumps to a setlist that contains that song. **Edit** opens song detail. If the song is not in this library, the cell is blank.

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

If the song already has a [library layout](song-detail-and-layouts.md#song-layouts) for the set’s band, those assignments are copied onto the setlist item when the song is added (or when you later choose that band for the set). The setlist copy is independent: changing the song’s layout later does not update this set, and changing this set does not update the song.

---

## Import ABCP {#import-abcp}

**Import ABCP** loads Maestro / ABC Player playlist files (`.abcp`).

Before import, the app shows a notice: ABCP import is intended for playlists **you created locally** in ABC Player (paths must match songs already indexed in your library). It is not a shared-setlist interchange format.

See [Import and export formats](import-export.md).

---

## Optimize instruments {#optimize-instruments}

**Optimize instruments** proposes part assignments that reduce instrument changes from one song to the next. Song order, ABC files, song layouts, and stage positions stay as they are.

The dialog shows the band layout, the saved weights and target, how many parts are pinned, and a search budget for this run only. Choose Quick (2 seconds), Normal (10 seconds), Thorough (60 seconds), or Custom and a number of seconds. That budget is not saved back to the band. Search runs in the background. The score column updates with the best valid assignment found so far, and the part changes are listed when the search stops. **Cancel** keeps that assignment. Closing the dialog also stops the search. Nothing is written until you choose **Apply**.

The comparison lists raw change counts separately from weighted costs, and it says the result is not proven optimal. **Apply** saves the whole preview as one operation. **Undo last optimization** restores that operation, including after a restart. If songs, pins, or assignments changed after the apply, undo stops and explains the conflict instead of overwriting those edits.

On the band optimizer rules, **Prefer** adds a miss penalty when that player plays a different instrument. **Always, when the song has it** assigns that instrument on every song that includes it. Songs that do not include the instrument are left free. If more players are set to always take it than the song has parts, the higher **Priority** takes a part and the others may play any other part. Equal priority lets the search choose who takes it. A part pinned to someone else is not available.

A locked setlist can still be previewed. Apply stays off, and undo is refused, until you unlock the setlist.

## Locked setlists {#locked-setlists}

When locked:

- Songs and order cannot be edited on the Setlists page
- The set does not appear in the Library context menu **Add to setlist**

Toggle lock from the set tree context menu or the details dialog checkbox.

---

[← User Guide home](index.md) · [Set export](set-export.md) · [Set Play](set-play.md)

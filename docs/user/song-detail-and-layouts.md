# Song detail and parts

Open **Song detail** from the Library **Edit** column, the row context menu, or by double-clicking a row.

---

## Basic Info tab {#basic-info}

Shows fields from the ABC file and app-managed metadata:

- **Title** and **Composer(s)** — editable; Save can write these back into the ABC file
- **Transcriber**, duration, export timestamp, part count — displayed from the file
- **Rating** and **Status**
- **Play history** — mark played, open history dialog

Click **Save** to persist changes. Title/composer (and part edits from the Parts tab) can update both the database and the ABC file on disk.

---

## Parts tab

Lists ABC parts. You can edit part metadata used by the app and reorder parts by dragging rows (order is written back to the ABC file on Save when parts changed).

A part-name template and variable list help format part titles consistently for export and Songbook.

---

## Notes and Lyrics tab

Store notes and lyrics in the app database. Useful for set notes or performance reminders.

---

## Raw ABC tab {#raw-abc}

**Advanced:** edit the underlying ABC text.

Edit carefully — incorrect changes can break the file.

If the file changed on disk since you opened the dialog, the app warns you about conflicts before saving.

Use **Save to file** on this tab to write the raw text and re-parse metadata.

---

## Song layouts {#song-layouts}

The **Layouts** tab stores part assignments for this song, one layout per band.

- **Add layout…** — choose a band that does not already have a layout for this song
- Click a player card to assign an ABC part (saved immediately)
- **Delete** — removes this song’s layout for that band. Setlists that already imported the layout keep their own copy

When you later add the song to a setlist whose band matches one of these layouts, those assignments are copied onto the setlist item. Editing the library layout afterward does not change existing setlists, and editing a setlist does not change the song.

See [Setlists → Part assignments](setlists.md#set-part-assignment) and [Bands](bands.md).

Band layouts still affect Set Play’s up-next grid and (when stereo mode is `band_layout`) playback panning.

---

[← User Guide home](index.md) · [Bands](bands.md) · [Library](library.md)

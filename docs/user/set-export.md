# Set export

Export a setlist to a folder or zip for performers, with optional renaming rules and CSV part sheets.

Open from the Setlists toolbar **Export**, the set details **Export** button, or the set tree context menu **Export set...**.

The dialog title is **Export Set**; confirm with **Export**.

---

## Export Settings tab

- Destination folder or zip
- Options for including ABC files, ABCP playlist, and related outputs
- Uses your **Set Export directory** from [Folder rules](settings/folder-rules.md) when applicable

---

## ABC File Renaming tab {#filename-patterns}

Define filename patterns for exported ABC files. Common variables:

| Variable | Meaning |
|----------|---------|
| `$FileName` | Original filename without `.abc` |
| `$SongIndex` | 1-based position in setlist (e.g. `001`) |
| `$PartCount` | Number of parts |
| `$SongComposer` | Composers (C: field) |
| `$SongTranscriber` | Transcriber (Z: field) |
| `$SongLength` | Duration in `mm_ss` format (for filenames) |
| `$SongTitle` | Title (T: field) |

You can also choose how spaces in expanded variables are replaced.

---

## Part Renaming tab {#part-renaming}

Rename individual parts within ABC files when exporting multi-part sets. Variables include the file-renaming set plus:

| Variable | Meaning |
|----------|---------|
| `$PartInstrument` | Made-for instrument (from `%%made-for`) |
| `$PartName` | Unmodified `%%part-name` value |
| `$PartTitle` | Original part T: line |
| `$PartNumber` | Part number (X: value) |
| `$PlayerAssignment` | Player assigned to this part in the setlist band layout |
| `$Numeration` | `1`, `2`, … when multiple parts share the same `%%part-name`; empty if unique |
| `$SongLength` | Duration as `m:ss` (e.g. `2:05`) |

`$PlayerAssignment` only works if you defined a band layout and assigned parts — useful so part names include who should play them.

---

## CSV Part Sheet tab {#csv-part-sheet}

Generate a CSV reference sheet for musicians (parts, instruments, assignments).

---

## CSV Part Renaming tab

Optional find-and-replace pairs applied to part names in the CSV export (helps save space in the spreadsheet).

---

## Player Column Order tab

Reorder columns on the CSV part sheet to match your band's preferences.

---

## Tips

- Export after editing setlist details (**OK** in the Edit dialog) and finishing song/part assignments
- Set **Set Export directory** in settings so exported copies are not scanned into the library
- For ABCP-only sharing, use **Export to ABCP...** from the set context menu ([Import and export](import-export.md))

---

[← User Guide home](index.md) · [Setlists](setlists.md)

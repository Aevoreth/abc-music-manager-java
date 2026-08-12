# Playback bar

The playback bar stays visible at the **bottom** of the main window while you use the app (similar layout to ABC Player).

Playback converts ABC to MIDI internally and plays it through a synthesizer using the `LotroInstruments.sf2` soundfont from the Maestro project.

---

## Toolbar controls {#toolbar-controls}

| Control | Action |
|---------|--------|
| **Scrub bar** | Seek within the current song |
| **Previous** | Go to the previous queue item (or restart when appropriate) |
| **Play / Pause** | Start, pause, or resume playback |
| **Stop** | Stop playback; double-click for MIDI panic (silences hanging notes) |
| **Next** | Next track in the playlist |
| **Parts / Playlist** | Open the parts mute/solo and queue dialog |
| **Tempo** | Slider (about 50%–200%; snaps near 100%) |
| **Stereo** | Stereo width 0–100 (**0 = mono**, **100 = full stereo**) |
| **Volume** | Slider 0–100 |

Default volume, tempo, stereo mode, and stereo width come from [ABC Playback settings](settings/abc-playback.md).

---

## Playlist {#playlist}

Open **Parts / Playlist** to see the current queue on the right.

- **Drag** a row to rearrange the queue (playback of the current song is not interrupted)
- **Move up** / **Move down** — same reorder, using the selected row
- **Double-click** a row to play that song
- **Save as setlist…** — create a new setlist (Unfiled) from the current queue order; opens the same details dialog as [New setlist](setlists.md#create-setlist). Songs are copied in queue order; part assignments are not copied.

Rearranging the playback queue does not change an existing setlist. Use **Save as setlist…** if you want to keep the new order.

---

## Mute and solo {#mute-solo}

Open **Parts / Playlist** to see all parts for the current song and the queue. Mute or solo individual parts while listening. Shift-click mute can unmute all parts. The playlist side can be rearranged and saved as a setlist; see [Playlist](#playlist).

---

## Stereo {#stereo}

**Stereo mode** (in Settings → ABC Playback) affects how panning is calculated:

- **maestro** — Maestro default panning
- **maestro_user_pan** — use user pan directives from the ABC
- **band_layout** — pan from player positions on the active band layout (when a layout applies)

The bar’s **Stereo** slider is width only: **0 = mono**, **100 = full stereo**.

---

## Soundfont {#soundfont}

Playback uses a configurable soundfont (SF2). Leave the path empty in Settings to use automatic lookup (often under `%LOCALAPPDATA%\MaestroCommon`) or a download prompt.

If playback fails silently, check [Troubleshooting → Playback](troubleshooting.md#playback-no-sound). If a note keeps sounding after Stop, [double-click Stop for MIDI panic](troubleshooting.md#playback-hanging-notes).

---

[← User Guide home](index.md) · [Library](library.md) · [Settings → ABC Playback](settings/abc-playback.md)

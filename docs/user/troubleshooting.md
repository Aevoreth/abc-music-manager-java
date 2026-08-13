# Troubleshooting

Quick fixes for common issues. Each item links to detailed topics where available.

---

## Library is empty

This is normal after a fresh install. Some initial setup is required:

1. Complete [First-time setup](getting-started.md#getting-started-checklist)
2. Run **File → Scan Library…**
3. Click **Clear Filters** on the Library tab ([filters](library.md#filters))
4. Use **Open User Guide** on the empty state if you need the checklist again

---

## Scan finds no songs

- Verify **File → Settings… → Folder rules → LOTRO Directory** points to the folder containing `Music\` and not the `Music\` folder itself
- Check that paths are not listed as excluded directories
- Confirm ABC files exist under Music

---

## Playback: no sound {#playback-no-sound}

- Open **File → Settings… → ABC Playback** and set or browse for a soundfont
- Leave path empty to trigger default lookup / download
- Check system volume and that another app is not holding exclusive audio

See [Playback bar](playback.md) and [ABC Playback settings](settings/abc-playback.md).

---

## Playback: hanging notes {#playback-hanging-notes}

If a note keeps sounding after Stop, double-click **Stop** to send MIDI panic (all notes off on every channel).

---

## ABC file not found when playing

The database path may be stale if files moved. Re-scan the library. If duplicates were resolved, ensure the kept copy still exists and was not moved to the Recycle Bin.

---

## PluginData write failed

- Confirm account targets are **enabled** ([Account targets](settings/account-targets.md))
- Verify LOTRO directory and PluginData paths
- Read the red lines in the **Write PluginData** log dialog

---

## Set Play relay won't connect {#relay-issues}

- Leader must **Create session** (or Reconnect) and share **Play Only** or **Download and Play**
- Assistants: paste that link into Band Assistant or open it in a browser (no Settings relay required)
- Bare session codes still need the **same relay URL** as the leader under Settings → Set Playback
- URL format for relays: `wss://…` or `https://…` with **no trailing slash**
- The relay must have a **token**; a 401 usually means the token does not match Cloudflare (redeploy to issue a new one)
- Deploy or verify your Cloudflare worker ([Set Playback relays](settings/set-playback-relays.md))
- Browser **404** on `/playback`: redeploy the worker so it includes the playback page assets
- Prefer `*.workers.dev`; custom domains need Bot Fight / WAF skipped for the worker or `/api/*`

---

## Band Assistant can't join

- Prefer pasting the full share link from the bandleader
- If using a bare code, confirm the correct Relay is selected
- Confirm set/room code spelling
- Leader session must be active (named session connected)
- Try **Reconnect** on both sides

---

## Filters hiding all songs

Use **Clear Filters** (show everything) or **Reset Filters** (restore defaults from [Default filters](settings/default-filters.md)).

---

## Window off-screen / wrong monitor

**File → Settings… → Appearance → Reset window geometry**

---

[← User Guide home](index.md)

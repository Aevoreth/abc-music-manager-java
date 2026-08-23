# Set Play

**Set Play** is the bandleader view for running a live set: track current/next songs, skip items, advance the show, and optionally host a **named session** so band assistants can follow along.

Open from the **Set Play** tab. Inner tabs: **Sessions | Playback | Parts**.

---

## Sessions {#sessions}

1. Choose a **Setlist** and click **Load set** (local only). Required before you can create a relay session. Solo rehearsal: load a set and stay on Playback — no session needed.
2. Select a **Relay** (configured under [Set Playback settings](settings/set-playback-relays.md)) that has a **relay token**.
3. Click **Create session**. You will be asked for a session name and date/time (session metadata only — this does **not** change the setlist’s date/time). Prefill uses the setlist values, or **now + 7 days** if empty. Duplicate names are allowed after a warning.
4. Copy **Play Only** (watch playback) and/or **Download and Play** (includes the 6-digit zip PIN in the URL fragment). The PIN is shown once and cannot be recovered from Cloudflare.

If you are already connected, **Load set** updates the local Playback/Parts views only. Use **Republish** to replace the hosted song list, reset NOW/NEXT/played/skip, and delete the zip.

Other session actions (relay token required): **Reconnect**, **Rename** (code and URLs stay the same), **Republish**, **Upload zip** (existing `*.zip`, max 2 MB; chooser starts in the set-export folder), **Clear session**, **Delete session**.

Zip download expires after the session date/time (America/New_York) plus that relay’s **retention days** (default 14). Playback continues after the zip expires. Changing retention does not rewrite existing zips. There is no minimum remaining life if you upload late.

**Reconnect** when the local setlist is missing or different: the app warns and hosts from the relay **snapshot**. Advance still works; the band grid follows NEXT from the snapshot’s per-song layouts. Play history is skipped for songs that are not in this library. If the same setlist is in this library, Set Play reloads it from the database instead of using incomplete snapshot rows.

---

## Loading a set {#load-set}

1. On **Sessions**, choose a **Setlist** from the tree combo
2. Click **Load set**

Sets can load **without** a band layout; part-assignment UI is not used in that case.

---

## Song status {#status}

Each song has one primary **status** (shown as a badge) plus an optional **Skip** flag:

| Badge | Meaning |
|-------|---------|
| **NOW** | The song being performed now (at most one) |
| **NEXT** | Marked as up next (at most one) |
| **✓** | Already performed this session |
| **SKIP** | Excluded from automatic next-song selection |
| *(blank)* | Pending — not yet current, next, played, or skipped |

**Skip** is the only per-row checkbox. Change Current / Next / Played via the row **Actions** menu (button or right-click), or **double-click** a row to set it as Next. Each row also has a small **layout** button that opens a preview of that song’s band assignments:

| Action | Effect |
|--------|--------|
| **Layout button** | Preview that song’s formation and part assignments |
| **Double-click row** | Set as NEXT |
| **Set current** | Make this song NOW (clears Next/Skip/Played on that row) |
| **Set next** | Make this song NEXT (clears Current/Skip/Played on that row) |
| **Mark played** / **Clear played** | Toggle session played; Mark also logs to library play history. Clear only removes the session flag (library history is kept). |
| **Log at time…** | Log a library play at a chosen time |

Skipping a song that is Current or Next clears that pointer and rescans Next when needed.

---

## Advance song {#advance-song}

**Advance song** (large button on Playback, also on Parts) is the primary live control:

1. Current → Played
2. Next → Current
3. Next unskipped song in list → Next

Enable **Mark songs as played automatically** to write play history to the Library when advancing. Useful for live performances. Uncheck this when you are simply testing a set during private rehearsal. Songs that are not in this library are not logged.

---

## Mark set as played

**Mark set as played (all non-skipped)…** logs play history for every non-skipped song in one step (with confirmation).

---

## Band layout panel {#band-grid}

When the loaded set has a band layout, the grid shows player positions as soon as the set is opened. Cards use the **NEXT** song’s part/instrument assignments when one is set; otherwise they show the formation with placeholder parts (same as the setlist editor with no assignments yet). **Clear session** only resets NOW/NEXT/played/skip — the formation stays. Each song row has a layout button that previews that song’s assignments without changing NEXT. In the browser Playback tab the grid stays pinned to the bottom of the page; drag the splitter above it to resize. Player cards scale down so the full formation fits in the pane. Only **Your players** and the setlist scroll.

**Part change highlighting** compares the **next** selected song to the **current** song (instrument/part changes between them).

The player name list (**Your players** on Playback, or **Players** on Parts) highlights selected members on the grid and draws a stronger tint accent on their Parts columns (local only — not sent to the relay). Useful to remind you which player(s) you are controlling.

---

## Parts {#parts}

The **Parts** tab is a CSV-style table using Player Column Order and CSV part-renaming rules (same as set export). Without a layout, columns are **Part 1…N**. All player columns are shown by default. Each column has a tint wash and a left accent in that tint (dimmer until you select the player; bolder and more saturated when selected). **Selected players only** (app and browser) hides columns for players who are not selected. **Players** (app and browser) is the same list as **Your players** on Playback. **Instruments needed…** lists unique catalog instruments per player.

Assistants see the same table read-only.

---

## Named sessions (Cloudflare relay) {#broadcast}

Live sync for [Band Assistant](band-assistant.md) (app or browser):

1. Configure (or create) a relay in [Set Playback settings](settings/set-playback-relays.md) — **only the bandleader** needs this, including the **relay token**
2. Load a set, then **Create session**
3. Share **Play Only** or **Download and Play**

Assistants can open the link in a browser or paste it into Band Assistant — they do not need to configure the relay themselves. A session code is enough to **watch**; the PIN is only for zip download.

Set Play works locally without a session; the relay is only needed for assistants.

**Note:** Existing relays must be **redeployed** once (Settings → Set Playback). Redeploy wipes the worker, D1 session list, R2 zips, and issues a new token. Prefer `*.workers.dev`; on a custom domain, skip Bot Fight / WAF for the worker hostname or `/api/*` (the Bearer token does not bypass Bot Fight).

---

[← User Guide home](index.md) · [Band Assistant](band-assistant.md) · [Setlists](setlists.md)

# Band Assistant

**Band Assistant** lets band members follow a bandleader's Set Play session. You can use the in-app tab, or open the bandleader’s share link in any browser — no app install and no relay configuration required for assistants.

Inner tabs: **Connect | Playback | Parts**.

---

## In-app vs standalone {#standalone-mode}

- **In app:** open the **Band Assistant** tab
- **Browser:** open the share link the bandleader copies from Set Play (`https://…/playback?set=CODE` or `…#p=PIN`). The page is served by the bandleader’s Cloudflare relay after they deploy/redeploy the worker that ships with the app.
- **Standalone window:** launch the app with `--assistant` (for example `ABC-Music-Manager.exe --assistant`)

---

## Connecting {#connect}

1. Leader creates a **named session** on [Set Play](set-play.md#broadcast) and shares **Play Only** or **Download and Play**
2. **Either:**
   - Open the link in a browser, or
   - On **Band Assistant → Connect**, paste the full link (or code) and click **Connect**
3. If the link includes `#p=`, the download PIN is filled in automatically. Watching playback does **not** require a PIN.

**Bare room code:** you can still paste only the code if you select the same **Relay (for bare code)** as the leader (configured under [Set Playback settings](settings/set-playback-relays.md)). Standalone `--assistant` has no library database — paste a full link.

Use **Reconnect** after connection drops. **Disconnect** leaves the session. If the leader deletes the session, clients show **session ended** and controls disable.

If the browser shows **404** on `/playback`, the leader’s worker was deployed before this page existed — ask them to **redeploy** from Settings → Set Playback.

---

## Download ZIP {#download-zip}

Download is shown when a PIN is present **and** the session has a zip attached.

- **Browser:** download the zip file only
- **In-app Band Assistant (full app):** **Save ZIP as…**, or **Download & Extract** into a folder named after the zip (under the set-export directory). If that folder exists, you confirm deleting it first. Paths are checked before write (no `..` / absolute paths). After extract, **Write PluginData** is offered for enabled account targets.
- **`--assistant` standalone:** Save and Extract still work; PluginData is hidden (no library database)

---

## What syncs {#what-syncs}

Assistants receive the leader's Set Play state (read-only):

- Current / Next summary banners
- Song list with a **status badge** (`NOW` / `NEXT` / `✓` / `SKIP`) and row coloring
- Loaded setlist identity
- Band layout grid and part-change highlighting
- Parts table (Player Column Order / CSV renames)

Assistants cannot edit status; only the bandleader changes Current, Next, Skip, and Played.

---

## What does not sync

Selecting player names to highlight on the grid is **local to each client** (leader, assistant app, or browser) and is not broadcast. Useful to remind you which player(s) you are controlling.

---

[← User Guide home](index.md) · [Set Play](set-play.md) · [Set Playback relays](settings/set-playback-relays.md)

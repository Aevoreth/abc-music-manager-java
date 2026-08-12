# First-time setup

This guide walks through the minimum steps to get ABC Music Manager indexing your music and ready to use.

---

## Install and launch

- **Download:** Get the latest build from [ABC Music Manager (Java) Releases](https://github.com/Aevoreth/abc-music-manager-java/releases).
- **Windows zip:** Unpack `ABC-Music-Manager-<version>.zip` and run `ABC-Music-Manager.exe`.
- **Windows MSI:** Install with the `.msi` package, then launch from the Start menu.
- **From source (developers):** Requires JDK 21 and Maven. From the repository root run `mvn -pl abcmm-app -am package -DskipTests`, then `mvn -pl abcmm-app exec:java`.

Your app data (library database and preferences) is stored under `~/.abc_music_manager/` (see [Data and backups](data-and-backups.md)). Keep that folder backed up.

---

## Getting started checklist {#getting-started-checklist}

### 1. Set your LOTRO directory {#lotro-directory}

Go to **File → Settings… → Folder rules → LOTRO Directory** and set **Lord of the Rings Online directory**.

This is usually something like:

`C:\Users\<you>\Documents\The Lord of the Rings Online\`

The app may auto-detect this path on first run. This folder contains your **Music** library and **PluginData** for Songbook files, as well as game settings and other data the game needs.

→ [Folder rules (details)](settings/folder-rules.md)

### 2. Set Export directory (recommended)

On **Folder rules → Set Directory**, set **Set Export directory** if you export sets to a dedicated folder.

That folder is **not** scanned for the library (to avoid duplicate entries) but will be included when writing Songbook data.

### 3. Extra Folder rules

On **Folder rules → Exclude Directories**, you may add folder excludes so certain paths are not scanned into the library. Those excludes can optionally still be included in Songbook data export.

→ [Folder rules → Excluded directories](settings/folder-rules.md#excluded-directories)

### 4. Configure account targets {#account-targets}

Go to **File → Settings… → Account targets** and click **Scan Account Targets**.

If your LOTRO folder is set correctly, the app finds each game account under PluginData and creates a target for writing `SongbookData.plugindata`.

Enable the accounts you want to update (all are enabled by default).

→ [Account targets (details)](settings/account-targets.md)

### 5. Scan your library {#scan-library}

Choose **File → Scan Library…**.

The scanner indexes ABC files from your LOTRO Music folder (and other configured paths). Re-scan whenever you add new music. Large libraries can take a few moments.

→ [Duplicates and maintenance](duplicates-and-library-maintenance.md) if scan reports duplicates.

### 6. Verify playback {#first-playback}

Open the **Library** tab and click **Play** on a song.

Playback uses the Maestro ABC engine and the `LotroInstruments.sf2` soundfont. If Maestro is installed via its MSI package, the soundfont is often found automatically. If no soundfont is configured, you may be prompted to download or browse for it. See [ABC Playback settings](settings/abc-playback.md).

→ [Playback bar](playback.md)

### 7. Write PluginData (optional)

When your library looks correct, use **File → Write PluginData…** to update Songbook files for enabled accounts. This feature is intended to replace Songfiller.hta or Songbooker.

→ [PluginData / Songbook](plugindata.md)

---

## What to do next

| Goal | Start here |
|------|------------|
| Rate and categorize songs | [Library](library.md) + [Statuses](settings/statuses.md) |
| Build a show setlist | [Setlists](setlists.md) |
| Assign parts to band members | [Bands](bands.md) + [Setlists → Part assignments](setlists.md#set-part-assignment) |
| Run a live event | [Set Play](set-play.md) |
| Share set play status with bandmates | [Band Assistant](band-assistant.md) + [Set Playback relays](settings/set-playback-relays.md) |

---

[← User Guide home](index.md)

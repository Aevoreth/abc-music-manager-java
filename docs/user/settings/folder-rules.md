# Settings — Folder rules

**File → Settings… → Folder rules**

Paths control scanning, exports, and Songbook generation. Sub-tabs: **LOTRO Directory**, **Set Directory**, **Exclude Directories**.

---

## Lord of the Rings Online directory {#lotro-directory}

The main LOTRO folder (typically under Documents). Contains:

- `Music\` — scanned for the library
- `PluginData\` — account folders for Songbook plugindata

Browse to set the path. The app may auto-detect a default path on first run.

---

## Set Export directory {#set-export-directory}

Single folder used for set exports. **Not scanned** for the library (avoids duplicate entries from exported copies).

Included when generating [PluginData](../plugindata.md) unless excluded below.

---

## Excluded directories {#excluded-directories}

Paths listed here are **not indexed** in the library.

| Column | Meaning |
|--------|---------|
| **Path** | Folder to skip during scan |
| **Songbook** | If enabled, still include files under this path in plugindata export |

Use add/remove controls to manage entries.

---

[← User Guide home](../index.md) · [First-time setup](../getting-started.md)

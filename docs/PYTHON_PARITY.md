# Python ↔ Java Parity Inventory

The **Python** edition ([Aevoreth/abc-music-manager](https://github.com/Aevoreth/abc-music-manager))
is the **current stable release** (documented here as of local inspection of v0.2.9b).

The **Java** edition in this repository is a prototype under development. This
matrix inventories capabilities; it is **not** a mandate to implement everything now.

| Area | Python status | Java status | Compatibility requirement | Notes |
|------|---------------|-------------|---------------------------|-------|
| Library scanning | Complete | Complete | Preserve behavior | Scan `.abc` under LOTRO Music roots; folder rules; primary vs set-copy; duplicates (Keep existing / Keep new / Separate) |
| Song metadata | Complete | Partial | Preserve Maestro metadata | Library table reads full indexed row; scan writes Maestro-compatible %%/header fields via `AbcMetadataParser`; inline song edits not started |
| Filters and search | Complete | Partial | Preserve behavior | Title/composer, status, in-set, rating, parts, duration, last-played (incl. Never), transcriber; `default_filters` in prefs; sorting + empty state |
| Setlists | Complete | Complete (builder) | Preserve stored data | Folders, order, timing, per-set band layout / part overrides; ABCP import/export deferred |
| ABCP support | Complete | Not started | Preserve interchange | XML playlist import/export compatible with ABC Player workflows |
| Songbook export | Complete | Not started | Preserve PluginData format | Manual write of `SongbookData.plugindata` (Lua) to account targets |
| Band management | Complete | Complete | Preserve stored data | Bands, members, layouts, snapped layout grid; Change Player cascades song/setlist assignments |
| Player management | Complete | Complete | Preserve stored data | Players + instruments; filters (name/level/class/instrument); searchable layout picker |
| Band layouts | Complete | Complete | Preserve stored data | Pan/re-center/context menu; MAX_CARDS; overlap warning on Save; band list drag-reorder; unsaved name/notes on leave |
| Part assignments | Complete | Complete | Preserve stored data | Setlist grid UI with song-layout baseline + overrides; dedicated song-layout library editor deferred |
| Playback | Complete (custom TinySoundFont path) | Partial (Maestro engine + bottom transport/queue) | Use Maestro Java engine | `LotroAbcPlaybackEngine`; library/setlist play+queue; parts mute/solo popup |
| Set Play | Complete | Not started | Preserve session semantics | NOW/NEXT/Played/Skip; advance song; play logging |
| Relay / group playback | Complete | Not started | Preserve protocol where practical | Cloudflare Worker relay; Band Assistant / browser follower |
| Settings | Complete | Complete (CRUD) | Preserve prefs where practical | Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD; Set Play relays still stubbed; LOTRO Documents auto-detect + Scan Account Targets |
| Themes | Complete | Partial | Visual identity TBD | Python: dark LOTRO-inspired Qt theme; Java: Maestro/ABC Player Flat Dark / Flat Light via FlatLaf, wired in Appearance |
| Packaging | Complete (PyInstaller) | Not started | Own installer later | Java must not package Python app or Maestro/ABC Player/ABC Tools launchers |
| Database compatibility | Complete (SQLite v12) | Complete (R/W) | Open existing DB where practical | Creates/migrates to v12; opens shared DB read-write; interchangeable with Python |

## Python areas inspected

- Entry: `main.py`, `ui/main_window.py` (Library → Setlists → Bands → Set Play → Band Assistant → Settings)
- Scanning / parsing: `scanning/`, `parsing/abc_parser.py`
- Playback: `playback/` (including ported Maestro ABC logic)
- DB: `db/schema.py` (migrations through version **12**), repositories
- Services: setlists, ABCP, set export, PluginData, Set Play sync/relay, preferences
- Docs: `README.md`, `PROJECT_BRIEF.md`, `REQUIREMENTS.md`, `DATA_MODEL.md`, `SCHEMA.md`, `DECISIONS.md`, `NOTICE.txt`

## Data-folder conventions (shared)

| Path | Role |
|------|------|
| `~/.abc_music_manager/abc_music_manager.sqlite` | Library index and app entities (schema v12) |
| `~/.abc_music_manager/preferences.json` | Jackson JSON; shared key names + `extras` passthrough for unknown/Python UI keys |
| `$ABC_MUSIC_MANAGER_DATA/` | Optional override for the data directory (portable-app hook) |

ABC source files remain on disk under the configured LOTRO Music tree; the DB is an index.

Java may add keys under `extras` (e.g. `java_nav_section`) without breaking Python; Python-only UI keys are preserved on save.

## Java milestone context

Completed bandleader library + management slice (playback still deferred):

1. Open or create SQLite v12 (writable; migrate older DBs)
2. List primary-library songs in the Library table with filters
3. Settings dialog: Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD
4. Shared `preferences.json` load/save (including Java `theme`)
5. Library scanning with progress + duplicate resolution
6. Navigation: Library | Setlists | Bands (top tabs; Players under Bands)
7. Player / Band / layout grid management
8. Setlist builder (folders, metadata, songs, timing, part overrides)

Later: Set Play / relays, ABCP, songbook export, Band Assistant, portable packaging.

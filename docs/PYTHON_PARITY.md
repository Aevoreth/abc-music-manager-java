# Python ↔ Java Parity Inventory

The **Python** edition ([Aevoreth/abc-music-manager](https://github.com/Aevoreth/abc-music-manager))
is the **current stable release** (documented here as of local inspection of v0.2.9b).

The **Java** edition in this repository is a prototype under development. This
matrix inventories capabilities; it is **not** a mandate to implement everything now.

| Area | Python status | Java status | Compatibility requirement | Notes |
|------|---------------|-------------|---------------------------|-------|
| Library scanning | Complete | Complete | Preserve behavior | Scan `.abc` under LOTRO Music roots; folder rules; primary vs set-copy; duplicates (Keep existing / Keep new / Separate) |
| Song metadata | Complete | Complete | Preserve Maestro metadata | Library table + Song detail (Basic Info / Parts / Notes & Lyrics / Raw ABC); Save writes title/composer/parts to ABC as needed; inline rating/status; Playback History / PlayLog; scan writes Maestro-compatible %%/header fields via `AbcMetadataParser`; dedicated song-layout library editor deferred (would change DB structure) |
| Filters and search | Complete | Complete | Preserve behavior | Title/composer, status, in-set, rating, parts, duration, last-played (incl. Never), transcriber; `default_filters` in prefs; sorting + empty state; Status/In-set/Rating stay in Filters pane (Java layout); `library_table_header_state` persisted; empty-state Open User Guide deferred until broader Java↔Python parity |
| Setlists | Complete | Complete (builder + set export + ABCP import) | Preserve stored data | Folders, tree drag reorder/move between folders, song order, timing, per-set band layout / part overrides; set package export (folder/zip + optional CSV/ABCP); standalone ABCP export/import; ABCP import shows a local-only notice (Java) |
| ABCP support | Complete | Complete | Preserve interchange | XML playlist read/write compatible with ABC Player; import matches exact `SongFile.file_path` values (Python parity); Java prompts that import is for locally created playlists, not shared setlists |
| Songbook export | Complete | Complete | Preserve PluginData format | Manual File → Write PluginData…; UTF-8 Lua `SongbookData.plugindata` to enabled AccountTargets (Python `plugindata_writer`; HTA is format reference only) |
| Band management | Complete | Complete | Preserve stored data | Bands, members, layouts, snapped layout grid; Change Player cascades song/setlist assignments |
| Player management | Complete | Complete | Preserve stored data | Players + instruments; filters (name/level/class/instrument); searchable layout picker |
| Band layouts | Complete | Complete | Preserve stored data | Pan/re-center/context menu; MAX_CARDS; overlap warning on Save; band list drag-reorder; unsaved name/notes on leave |
| Part assignments | Complete | Complete | Preserve stored data | Setlist grid UI with song-layout baseline + overrides; dedicated song-layout library editor deferred (would change DB structure) |
| ABC audition (audio engine + transport) | Complete (custom TinySoundFont path) | Complete | Use Maestro Java engine | Audible ABC sampling only (not Set Play). `LotroAbcPlaybackEngine`; library/setlist → queue; mute/solo; tempo/stereo/volume transport |
| Set Play (live set session) | Complete | Not started | Preserve session semantics | In-game bandleader set guidance — not audio. NOW/NEXT/Played/Skip; advance song; play logging |
| Relay / group playback | Complete | Not started | Preserve protocol where practical | Cloudflare Worker relay; Band Assistant / browser follower |
| Settings | Complete | Complete (CRUD) | Preserve prefs where practical | Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD; Set Play relays still stubbed; LOTRO Documents auto-detect + Scan Account Targets |
| Themes | Complete (LOTRO-inspired Qt) | Complete (Maestro FlatLaf) | Own visual identity | Java targets Maestro/ABC Player Flat Dark / Flat Light via FlatLaf (Appearance); not a port of Python’s LOTRO Qt theme. Future theme revisit possible |
| Packaging | Complete (PyInstaller) | Not started | Own installer later | Java must not package Python app or Maestro/ABC Player/ABC Tools launchers |
| Database compatibility | Complete (SQLite v12) | Complete (R/W) | Open existing DB where practical | Creates/migrates to v12; opens shared DB read-write; interchangeable with Python |

## Python areas inspected

- Entry: `main.py`, `ui/main_window.py` (Library → Setlists → Bands → Set Play → Band Assistant → Settings)
- Scanning / parsing: `scanning/`, `parsing/abc_parser.py`
- ABC audition / audio: `playback/` (including ported Maestro ABC logic); distinct from Set Play session UI
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

Completed bandleader library + management slice:

1. Open or create SQLite v12 (writable; migrate older DBs)
2. List primary-library songs in the Library table with filters
3. Settings dialog: Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD
4. Shared `preferences.json` load/save (including Java `theme`)
5. Library scanning with progress + duplicate resolution
6. Navigation: Library | Setlists | Bands (top tabs; Players under Bands)
7. Player / Band / layout grid management
8. Setlist builder (folders, metadata, songs, timing, part overrides)
9. Library song detail + inline metadata / play history / Raw ABC; Maestro ABC audition transport

Later: Set Play (live set session) / relays, Band Assistant, dedicated song-layout library editor (schema change), empty-state Open User Guide, portable packaging.

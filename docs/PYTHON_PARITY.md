# Python ↔ Java Parity Inventory

The **Python** edition ([Aevoreth/abc-music-manager](https://github.com/Aevoreth/abc-music-manager)
v0.2.9b) is the last Python release and the **behavior / schema reference**.

The **Java** edition in this repository is the **active product** (full-function
port from 0.3.0b). It shares `~/.abc_music_manager/` with Python. This matrix
inventories capabilities and remaining gaps; it is not a mandate to clone every
Python UI detail.

Inspected against local Python v0.2.9b and this Java tree (changelog through
0.3.1b plus unreleased User Guide).

| Area | Python status | Java status | Compatibility requirement | Notes |
|------|---------------|-------------|---------------------------|-------|
| Library scanning | Complete | Complete (redesigned duplicates) | Preserve behavior where practical | Scan `.abc` under LOTRO Music roots; folder rules; primary vs set-copy. Java uses inventory-first peer `DuplicateGroup`s (exact hash + logical identity), batch review, cleanup plan, Recycle Bin trash, folder-cluster exclude — not Python’s existing-vs-new / first-scanned asymmetry. Folder-cluster review is part of Scan Library (no separate **Analyze duplicate folders…** menu) |
| Song metadata | Complete | Complete | Preserve Maestro metadata | Library table + Song detail (Basic Info / Parts / Notes & Lyrics / Raw ABC); Save writes title/composer/parts to ABC as needed (ahead of Python’s user guide, which still marks ABC write-back as unimplemented); inline rating/status; Playback History / PlayLog; scan writes Maestro-compatible %%/header fields via `AbcMetadataParser` |
| Filters and search | Complete | Complete | Preserve behavior | Title/composer, status, in-set, rating, parts, duration, last-played (incl. Never), transcriber; `default_filters` in prefs; sorting + empty state; Status/In-set/Rating stay in Filters pane (Java layout); `library_table_header_state` persisted; empty-state **Open User Guide** + **Help → User Guide** |
| Setlists | Complete | Complete (builder + set export + ABCP import) | Preserve stored data | Folders, tree drag reorder/move between folders, song order, timing, per-set band layout / part overrides; set package export (folder/zip + optional CSV/ABCP); standalone ABCP export/import; ABCP import shows a local-only notice (Java) |
| ABCP support | Complete | Complete | Preserve interchange | XML playlist read/write compatible with ABC Player; import matches exact `SongFile.file_path` values (Python parity); Java prompts that import is for locally created playlists, not shared setlists |
| Songbook export | Complete | Complete | Preserve PluginData format | Manual File → Write PluginData…; UTF-8 Lua `SongbookData.plugindata` to enabled AccountTargets (Python `plugindata_writer`; HTA is format reference only) |
| Band management | Complete | Complete | Preserve stored data | Bands, members, layouts, snapped layout grid; Change Player cascades song/setlist assignments |
| Player management | Complete | Complete | Preserve stored data | Players + instruments; filters (name/level/class/instrument); searchable layout picker |
| Band layouts | Complete | Complete | Preserve stored data | Pan/re-center/context menu; MAX_CARDS; overlap warning on Save; band list drag-reorder; unsaved name/notes on leave |
| Part assignments | Complete (library editor buggy) | Complete on setlists; library editor deferred | Preserve stored data | Setlist grid UI with song-layout baseline + overrides. v12 `SongLayout` / `SongLayoutAssignment` already exist; Java creates them from the setlist editor. Dedicated library song-layout editor (Python **Actions → Layout**) is not in Java — UI gap, not a schema change |
| ABC audition (audio engine + transport) | Complete (custom TinySoundFont path) | Complete | Use Maestro Java engine | Audible ABC sampling only (not Set Play). `LotroAbcPlaybackEngine`; library/setlist → queue; mute/solo; tempo/stereo/volume transport. See [playback gaps](#playback-transport) |
| Set Play (live set session) | Complete | Complete | Preserve session semantics | In-game bandleader set guidance — not audio. NOW/NEXT/Played/Skip; advance song; play logging; up-next band grid (Java Maestro grid styling). Broadcast via Cloudflare relay |
| Relay / group playback | Complete | Complete | Preserve protocol where practical | Cloudflare Worker relay (`workers/set-play-relay`); Band Assistant tab / `--assistant`; browser follower `/playback` |
| Settings | Complete | Complete (CRUD) | Preserve prefs where practical | Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD; Set Play relays CRUD + Wrangler deploy/redeploy wizard; LOTRO Documents auto-detect + Scan Account Targets |
| Help / About | Complete | Partial | Own identity | **Help → User Guide** in both. Python **Help → About** (version, license, third-party) is not in Java yet; version appears in the User Guide viewer when the package manifest has `Implementation-Version` |
| Themes | Complete (LOTRO-inspired Qt) | Complete (Maestro FlatLaf) | Own visual identity | Java targets Maestro/ABC Player Flat Dark / Flat Light via FlatLaf (Appearance); not a port of Python’s LOTRO Qt theme. Future theme revisit possible |
| Packaging | Complete (PyInstaller, Win/macOS/Linux) | Complete (Windows zip + MSI) | Own installer | Tag-push GitHub Actions (`jpackage` + trimmed jlink runtime); `ABC-Music-Manager-<version>.zip` / `.msi`. Must not package Python app or Maestro/ABC Player/ABC Tools launchers. macOS/Linux installers and code signing are not in this edition |
| Database compatibility | Complete (SQLite v12) | Complete (R/W) | Open existing DB where practical | Creates/migrates to v12; opens shared DB read-write; interchangeable with Python |

## Intentional differences (not bugs)

- **Duplicates:** Java peer groups + folder clusters during scan, vs Python existing-vs-new / first-scanned plus a standalone Analyze duplicate folders command.
- **Navigation:** Java top tabs + Settings dialog vs Python left nav + Settings page + View menu.
- **Playback chrome:** Java bottom bar (ABC Player-like) vs Python top toolbar.
- **Stereo width:** Java **0 = mono, 100 = full stereo**; Python **0 = wide L/R, 100 = centered**.
- **Themes and packaging:** own visual identity and Windows jpackage, not a Qt/PyInstaller clone.
- **Song-layout editing:** Java assigns parts on Setlists (and persists `SongLayout` rows); Python’s library Layout editor exists but is documented as not fully working.

## Remaining gaps and suggested requirements

These are follow-ups after feature parity of the bandleader product. They are
suggestions, not a committed roadmap.

### Product / UI

1. **Library song-layout editor** — Create and edit `SongLayout` / assignments from Library (Python **Actions → Layout** / Song detail Layouts tab) without opening a setlist. Schema already supports this.
2. **Help → About** — Version, license summary, and third-party credits in-app (Python has this; Java User Guide has a Legal page but no About dialog).
3. **Playback transport extras** {#playback-transport} — Python still has: MIDI panic (double-click Stop), **Export playlist as set**, and a **Layout** picker on the transport for stereo. Java covers mute/solo, queue, tempo/stereo/volume.
4. **Library table extras from original requirements** — Notes/Lyrics indicators and a **Total Plays** column are in `REQUIREMENTS.md` and stored on `Song`, but neither edition shows them as table columns today.
5. **Frequency-of-play filter** — Original requirements asked for “plays in last N days”; both editions filter by last-played instead. Keep last-played unless testers want play-count windows.
6. **Instrument / made-for filter** — Marked future in Python requirements; parts tooltips already show made-for.
7. **Standalone Analyze duplicate folders…** — Only needed if users want folder-cluster review without a full library scan.

### Platform and operations

8. **macOS and Linux packages** — Python ships via PyInstaller on three platforms; Java release CI is Windows zip + MSI only. Source still runs anywhere JDK 21 + Maven work.
9. **Code signing** — Windows artifacts are unsigned (SmartScreen warnings). Signing would be a release-ops requirement, not a feature gap.
10. **Align Maven `${project.version}` with release tags** — Modules are still `0.1.0-SNAPSHOT` while CHANGELOG / GitHub Releases use 0.3.x. The in-app User Guide reads `Implementation-Version` from the package manifest.
11. **Headless CI playback tests** — Still deferred in [MAESTRO_INTEGRATION.md](MAESTRO_INTEGRATION.md) (no audio device in CI).

### Original requirements neither edition fully met

12. **Filesystem watching** — `REQUIREMENTS.md` §3: optional low-latency rescan. Neither edition watches the Music tree; users re-run **Scan Library**.
13. **Multiple extra library roots** — Requirements mention more than one Music root. Both editions scan LOTRO `Music\` plus folder-rule excludes / set-export skip, not an arbitrary extra-roots list.
14. **Compact library drag onto a setlist** — Requirements mentioned drag from a compact browser; both editions use a filterable song picker instead.

### Shared schema (coordinate with any remaining Python users)

See [SCHEMA_ISSUES.md](SCHEMA_ISSUES.md). Suggested in a future v13+ bump, not Java-only:

- Rename catalog `Traveler's Trusty Fiddle` → `Traveller's Trusty Fiddle`
- Drop unused `PlayerInstrument.has_proficiency`
- Align `BandLayoutSlot` DDL defaults with the 9×7 cards both apps write
- UNIQUE indexes on layout membership `(layout_id, player_id)` and assignment counterparts

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

Completed bandleader library + management slice (this edition):

1. Open or create SQLite v12 (writable; migrate older DBs)
2. List primary-library songs in the Library table with filters
3. Settings dialog: Appearance, Default filters, roots, Status/FolderRule/AccountTarget CRUD
4. Shared `preferences.json` load/save (including Java `theme`)
5. Library scanning with progress + duplicate resolution
6. Navigation: Library | Setlists | Bands | Set Play | Band Assistant (top tabs; Players under Bands)
7. Player / Band / layout grid management
8. Setlist builder (folders, metadata, songs, timing, part overrides)
9. Library song detail + inline metadata / play history / Raw ABC; Maestro ABC audition transport
10. Solo Set Play (local session: Load set, NOW/NEXT/Skip/Advance, play logging, Your players + up-next grid)
11. Set Play relays / Band Assistant (`set_play_state_v1`, Broadcast, share link, deploy wizard)
12. In-app User Guide (**Help → User Guide**)
13. Windows zip + MSI packaging (tag-push GitHub Actions)

Later (see [remaining gaps](#remaining-gaps-and-suggested-requirements)): library song-layout editor; Help → About; playback extras; non-Windows packages.

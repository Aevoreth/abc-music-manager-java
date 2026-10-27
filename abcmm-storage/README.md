# abcmm-storage

SQLite + preferences persistence for ABC Music Manager (Java).

## Compatibility goal

Open existing Python-edition user data where practical:

| Path | Purpose |
|------|---------|
| `~/.abc_music_manager/abc_music_manager.sqlite` | Library index and entities |
| `~/.abc_music_manager/preferences.json` | UI and app preferences |
| `$ABC_MUSIC_MANAGER_DATA/` | Optional data-directory override |

Helpers: `com.aevoreth.abcmm.storage.DataPaths`.

## Schema authority

Prefer the Java migration chain in `SchemaMigrator`
(`CURRENT_SCHEMA_VERSION = 17`). Opening a Python v12 database migrates to v17
and will not round-trip to Python. Known older quirks:
[docs/SCHEMA_ISSUES.md](../docs/SCHEMA_ISSUES.md).

## Current status

- JDBC (`sqlite-jdbc`) opens or creates the DB **read-write** and migrates to schema version 17
- Repositories cover songs, play log, settings (Status / FolderRule / AccountTarget), players, bands, setlists, song layouts, library scan, Set Play relays / published sessions, and per-band optimizer profiles
- `JsonPreferencesStore` reads/writes `preferences.json`; `set_play_relays` is copied into SQLite once and then no longer written

Java is the product going forward. Schema v13 does not round-trip to Python.

## Optimizer profiles

Schema v15 adds soft optimizer settings. Schema v16 adds `SetlistAssignmentPin`, one player per setlist item and part. A missing `BandOptimizationProfile` row reads as the code default (smooth weight 4, total 1, role 1, keep 1, target 2, Normal search budget) and is not inserted until saved. A saved document that omits `searchBudget` reads as Normal. `PlayerInstrument.has_proficiency` is not read.

- Duplicating a band copies the profile JSON and player switch / instrument-affinity rows. Player ids stay the same. Occurrence rows and pins are not copied, because setlists are not copied with the band.
- Duplicating a setlist copies part preferences, keep weights, and pins onto the new item ids. When the destination band layout differs, only preferences and pins for characters on that layout are copied; all keep weights are retained. A song-only merge does not.
- Replacing a player on a layout rewrites that band's switch and affinity rows. If the new player already has the same row, the destination is kept and the source row is dropped. Part preferences and pins change player only on setlists that use that layout.
- Deleting a player removes that player's switch weights, affinities, part preferences, and pins. Deleting a band removes the profile and player-scoped rows and leaves occurrence rows and pins on setlists. Removing an item, deleting a setlist, or dropping orphaned setlist items during a library scan removes occurrence rows and pins with the item.
- A locked setlist rejects preference and pin writes. Band profile saves are not blocked by a locked setlist.

## Optimization history

Schema v17 adds `SetlistOptimizationHistory`. Applying an optimization replaces that setlist's assignment override rows and writes the before and after snapshots in one transaction. Undo restores the before snapshot in one step, including after a restart, and refuses when the setlist is locked or the current song order, layouts, parts, pins, or overrides differ from the saved result. The journal keeps 10 history rows per setlist and drops unprotected rows older than 30 days. The newest applied row is kept so it can still be undone. A missing override row and an explicit null part stay distinct. Song layouts, song parts, item order, pins, and song files are not modified. Duplicating a setlist does not copy history. Deleting a setlist removes its history rows.

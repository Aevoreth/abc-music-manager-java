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
(`CURRENT_SCHEMA_VERSION = 15`). Opening a Python v12 database migrates to v15
and will not round-trip to Python. Known older quirks:
[docs/SCHEMA_ISSUES.md](../docs/SCHEMA_ISSUES.md).

## Current status

- JDBC (`sqlite-jdbc`) opens or creates the DB **read-write** and migrates to schema version 15
- Repositories cover songs, play log, settings (Status / FolderRule / AccountTarget), players, bands, setlists, song layouts, library scan, Set Play relays / published sessions, and per-band optimizer profiles
- `JsonPreferencesStore` reads/writes `preferences.json`; `set_play_relays` is copied into SQLite once and then no longer written

Java is the product going forward. Schema v13 does not round-trip to Python.

## Optimizer profiles

Schema v15 adds soft optimizer settings. A missing `BandOptimizationProfile` row reads as the code default (smooth weight 4, total 1, role 1, keep 1, target 2) and is not inserted until saved. Hard pins are not stored. `PlayerInstrument.has_proficiency` is not read.

- Duplicating a band copies the profile JSON and player switch / instrument-affinity rows. Player ids stay the same. Occurrence rows are not copied, because setlists are not copied with the band.
- Duplicating a setlist copies part preferences and keep weights onto the new item ids. A song-only merge does not.
- Replacing a player on a layout rewrites that band's switch and affinity rows. If the new player already has the same row, the destination is kept and the source row is dropped. Part preferences change player only on setlists that use that layout.
- Deleting a player removes that player's switch weights, affinities, and part preferences. Deleting a band removes the profile and player-scoped rows and leaves occurrence rows on setlists. Removing an item, deleting a setlist, or dropping orphaned setlist items during a library scan removes occurrence rows with the item.

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
(`CURRENT_SCHEMA_VERSION = 13`). Opening a Python v12 database migrates to v13
and will not round-trip to Python. Known older quirks:
[docs/SCHEMA_ISSUES.md](../docs/SCHEMA_ISSUES.md).

## Current status

- JDBC (`sqlite-jdbc`) opens or creates the DB **read-write** and migrates to schema version 13
- Repositories cover songs, play log, settings (Status / FolderRule / AccountTarget), players, bands, setlists, song layouts, library scan, and Set Play relays / published sessions
- `JsonPreferencesStore` reads/writes `preferences.json`; `set_play_relays` is copied into SQLite once and then no longer written

Java is the product going forward. Schema v13 does not round-trip to Python.

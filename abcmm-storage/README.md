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

Prefer the Python migration chain in `abc_music_manager/db/schema.py`
(`CURRENT_SCHEMA_VERSION = 12`). The Python `SCHEMA.md` header may lag the code.
Java `SchemaMigrator` implements the same version. Known quirks:
[docs/SCHEMA_ISSUES.md](../docs/SCHEMA_ISSUES.md).

## Current status

- JDBC (`sqlite-jdbc`) opens or creates the DB **read-write** and migrates to schema version 12
- Repositories cover songs, play log, settings (Status / FolderRule / AccountTarget), players, bands, setlists, song layouts, and library scan
- `JsonPreferencesStore` reads/writes `preferences.json` with Python-compatible keys

Do not invent an incompatible schema. Coordinate any v13+ bump with remaining Python users (see SCHEMA_ISSUES).

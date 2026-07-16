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

## Current milestone

- JDBC (`sqlite-jdbc`) opens the existing DB **read-only** and requires schema version 12
- `SqliteSongRepository` lists filtered library songs (primary, non-excluded)
- Read-only lookups for `Status`, `FolderRule`, `AccountTarget`
- `JsonPreferencesStore` reads/writes `preferences.json` with Python-compatible keys

Do not invent an incompatible schema. Java does not run migrations in this milestone.

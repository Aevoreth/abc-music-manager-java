# Data and backups

ABC Music Manager is **local-first**: your ABC files stay in your Music folders; the app maintains an index and preferences separately.

---

## Data directory

Default location (shared with the Python edition when both are installed):

`~/.abc_music_manager/`

On Windows that is typically:

`C:\Users\<you>\.abc_music_manager\`

Override with the environment variable **`ABC_MUSIC_MANAGER_DATA`** (portable-app hook).

Files in that directory:

- `abc_music_manager.sqlite` — library index, setlists, bands, play history
- `preferences.json` — settings, window layout, relays, filters, theme

---

## What is not stored in the data directory

- Original ABC files (remain under your LOTRO Music folder)
- Exported set folders (wherever you export them)
- Game PluginData (written directly under the LOTRO PluginData tree)

---

## Backup suggestions

1. Copy `~/.abc_music_manager/` periodically (database + preferences)
2. Keep your Music library backed up separately
3. If you use a portable data directory via `ABC_MUSIC_MANAGER_DATA`, back up that folder instead

---

## Reset preferences

**File → Settings… → Appearance → Reset all preferences** clears settings. This does **not** delete your SQLite library by itself, but paths and filters return to defaults. Theme and font changes apply when you save Settings.

---

## Warnings

1. Scanning your library removes songs from the database when their files are no longer found (irreversible for that index entry). Simply moving or renaming a song within the Music folder usually updates the stored path unless the song moves into an excluded folder.

---

[← User Guide home](index.md)

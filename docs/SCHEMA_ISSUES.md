# Schema issues (v12)

Known data / naming quirks in the shared SQLite schema (version **12**).
These are retained for Python ↔ Java parity; fix only when both editions can migrate together.

## Traveler's Trusty Fiddle spelling

| Field | Value |
|-------|--------|
| Stored / seeded name | `Traveler's Trusty Fiddle` (one L) |
| Correct LOTRO / Maestro spelling | `Traveller's Trusty Fiddle` (two L's) |

The v12 `Instrument` seed and Python `PLAYER_INSTRUMENTS` list use the single-L form.
ABC parts and Maestro often use the double-L form; library scanning maps that variant to the
seeded row so duplicates are not created.

**UI:** Java displays `Traveller's Trusty Fiddle` while keeping the stored name unchanged.

**Future correction:** Rename the catalog row (and any remaining references) to
`Traveller's Trusty Fiddle` in a coordinated schema bump after Python agrees.

## `PlayerInstrument.has_proficiency`

| Field | Value |
|-------|--------|
| Column | `PlayerInstrument.has_proficiency` (`INTEGER NOT NULL DEFAULT 0`) |
| Current use | Unused by Python (always written as `0` / false); Java UI no longer shows or edits it |

Possession is tracked only via `has_instrument`. The proficiency flag remains in the shared
schema so existing databases stay interchangeable.

**Future correction:** Drop `has_proficiency` in a coordinated schema bump after Python agrees.

## `BandLayoutSlot` size defaults vs app writes

| Field | Value |
|-------|--------|
| DDL defaults | `width_units DEFAULT 7`, `height_units DEFAULT 5` |
| App write size | Both editions insert **9×7** (`set_layout_slot` / `BandLayoutGridPanel`) |

The CREATE TABLE defaults are historical. Python UI treats cards as fixed 9×7 for hit-testing
and painting regardless of stored width/height; Java persists and paints the stored sizes but
defaults new cards to 9×7. Rows inserted without explicit dimensions (rare) would get 7×5.

**Future correction:** Align DDL defaults to `9` / `7` in a coordinated schema bump, or migrate
existing rows if any still use 7×5.

## Layout membership uniqueness is app-enforced

| Field | Value |
|-------|--------|
| Tables | `BandLayoutSlot`, `SongLayoutAssignment`, `SetlistBandAssignment` |
| Constraint | No UNIQUE on `(layout_id, player_id)` / `(song_layout_id, player_id)` / `(setlist_item_id, player_id)` |

Both editions enforce one row per player per layout (or assignment scope) in application code
(`set_layout_slot` delete-then-insert; `replace_player_in_band_layout` / Java
`replacePlayerInBandLayout` guards). The shared schema does not add UNIQUE indexes so older
databases remain loadable.

**Future correction:** Add UNIQUE indexes in a coordinated schema bump after Python agrees.

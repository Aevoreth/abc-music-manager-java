# abcmm-optimizer

Read-only proof-of-concept loader for setlist instrument assignment. It snapshots one setlist into an immutable problem and reports whether that snapshot and its current assignments are feasible. It does not score transitions, search, change the database, or modify the Swing application.

The module stays on the `setlist-optimizer` branch until a later benchmark review.

## What it checks

A performer may be assigned a part only when `PlayerInstrument.has_instrument` is true. `has_proficiency` is ignored. Two instrument rows are the same physical instrument when their ids match, their names match after trimming and lowercasing, or they differ only by the Traveler/Traveller spelling. Instrument family and class skill are not interchangeable.

Effective parts follow the setlist editor:

1. A `SetlistBandAssignment` row wins, including an explicit SQL NULL part (the player is idle).
2. Otherwise the item's song-layout assignment is used, including a null part.
3. Otherwise the player is idle.
4. Assignment rows for players who are not on the setlist's band layout are ignored and reported. They are not deleted.

Intentional doubling is not supported. Each required part must be covered by exactly one layout performer, and each performer plays at most one part in a song. Duplicate part numbers, two performers on one part, or two parts for one performer are infeasible. The loader does not drop or copy a part to hide that.

Pins can be supplied on an in-memory problem as `(setlist item id, part number, player id)`. The SQLite loader leaves them empty. There is no pin table in this stage.

## CLI

The process opens SQLite in read-only mode and does not migrate. Output uses ids only.

PowerShell splits an unquoted `-Dexec.args=...` on the dot and on `C:`, and Maven then reports `No plugin found for prefix '.args=...'`. Quote the whole `-D` argument:

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java "-Dexec.args=--database C:\path\to\abc_music_manager.sqlite --setlist 24"
```

Paths with spaces need the stop-parsing token so PowerShell does not split them:

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java --% -Dexec.args="--database C:\path\to\my database.sqlite --setlist 24"
```

Optional flags: `--seed <n>`, `--budget-seconds <n>`, `--players <n>`. Seed and budget are echoed for later search and do not run a solver. `--players` checks that the active layout roster has that many performers. It does not invent extra players.

Exit 0 means the snapshot and the baseline assignment are feasible. Exit 1 means the problem is not well-defined or the arguments are invalid. Exit 2 means the snapshot loaded, but the current assignment breaks a hard constraint.

Do not commit a private library database. Tests build a synthetic database in a temporary directory.

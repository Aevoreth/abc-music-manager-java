# abcmm-optimizer

Read-only proof-of-concept for setlist instrument assignment. It snapshots one setlist into an immutable problem, checks hard constraints, scores the baseline with adjacent-song rules, and can search for a better feasible assignment. It does not change the database or modify the Swing application.

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

## Scoring

A player adds one to a transition only when that player has a part in both adjacent songs and the physical instruments differ. Idle in either song adds nothing. The first song has no incoming cost. The scorer does not look back across an idle song: fiddle, then idle, then harp is two transitions of zero.

`AssignmentScorer` returns an accepted breakdown or a rejection. A rejection has diagnostics and no numeric score. Hard constraints stay hard: they are not rewritten as large finite penalties. The same assignment and the same rules always produce the same breakdown.

Displayed counts stay integers. The weighted score is the sum of four contributions, in this order:

- Smooth weight times overload. Overload is the sum of squared counts above the target. The default target is 2. One unit is one transition at target + 1.
- Total weight times switch burden. Each penalized switch adds that player's multiplier, or the band default of 1. The displayed change count stays unweighted.
- Role weight times role deviation. An assigned part pays at most one penalty. An occurrence part preference replaces instrument affinity for that part. With no part preference, a player who has affinities pays 0 on an equivalent preferred instrument and otherwise pays the highest miss penalty. Idle rows add no role cost.
- Keep weight times keep deviation. One unit per player whose part on an item differs from the baseline, times the part multiplier, else the item multiplier, else 1. The part used for that lookup is the baseline part, or the new part when the baseline was idle.

Each weight is finite and may be 0. A weight of 0 removes that term, including the soft preferences inside it. The defaults are all 1. These are raw units, not percentages: equal weights are not equal shares, because overload is squared. The worst transition and the histogram are reported and are not a fifth weight.

A per-player switch multiplier replaces the band default. A part keep multiplier replaces the item multiplier. Two part preferences for the same item and part, or two affinities for the same player and instrument, are invalid. Alias instruments on one player are the same affinity and are rejected when the rules are applied. Unknown ids are rejected the same way.

A pin is not a weight. A part preference that names a different player can still add role cost while the pin stays in place. Preferences and pins use the setlist item id, so two occurrences of one song do not share them.

The CLI scores the loaded baseline with those default rules. It prints the breakdown only when that baseline is accepted, using the breakdown's own fields. It does not take weight flags.

`--search` runs the pure-Java solver on that same default rule set. It requires `--budget-seconds`. An omitted `--seed` uses 0 for the search. Without `--search`, seed and budget are only echoed and no solver runs. A zero budget returns the repaired baseline. The proposal is printed after the baseline report as `proposal_stop`, `proposal_moves`, and the proposal breakdown, or `proposal: infeasible` when no feasible assignment exists. Search still opens the database read-only.

## Reference check

The private October 2026 reference setlist, id 24, is 34 songs and 33 transitions. The adjacent-song baseline is 148 penalized switches, worst transition 6, histogram `2=2,3=4,4=9,5=12,6=6`. The older lookback total of 156 is not the expected score.

That database is not in the repository. A test reads it only when `ABCMM_REFERENCE_DB` or the `abcmm.referenceDatabase` system property names a local file, and otherwise skips. Do not commit the database.

## CLI

The process opens SQLite in read-only mode and does not migrate. Output uses ids only.

PowerShell splits an unquoted `-Dexec.args=...` on the dot and on `C:`, and Maven then reports `No plugin found for prefix '.args=...'`. Quote the whole `-D` argument:

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java "-Dexec.args=--database C:\path\to\abc_music_manager.sqlite --setlist 24"
```

Add `--search` and `--budget-seconds` to run the solver. `--budget-seconds 0` scores the repaired baseline and does not search a neighborhood:

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java "-Dexec.args=--database C:\path\to\abc_music_manager.sqlite --setlist 24 --search --budget-seconds 10 --seed 42"
```

Paths with spaces need the stop-parsing token so PowerShell does not split them:

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java --% -Dexec.args="--database C:\path\to\my database.sqlite --setlist 24"
```

Optional flags: `--seed <n>`, `--budget-seconds <n>`, `--players <n>`, `--search`. `--players` checks that the active layout roster has that many performers. It does not invent extra players.

Without `--search`, exit 0 means the snapshot and the baseline assignment are feasible, and the report includes the default score. Exit 1 means the problem is not well-defined or the arguments are invalid. Exit 2 means the snapshot loaded, but the current assignment breaks a hard constraint. Exit 1 and exit 2 do not print a weighted score when the baseline itself is rejected. A roster-size mismatch can still print a score when the assignment is otherwise accepted.

With `--search`, exit 0 means a feasible proposal was found. Exit 2 means the snapshot loaded but no feasible assignment exists. Exit 1 still means bad arguments or an ill-defined problem, including a roster-size mismatch, and no search is run in that case.

Do not commit a private library database. Tests build a synthetic database in a temporary directory.

## Benchmark

`BenchmarkCli` compares the repaired baseline, a one-pass greedy assignment, and the heuristic. It does not change the database. Reports print counts, not names or player ids, and do not claim an optimum.

The reference matrix is setlist 24 with rosters 12, 16, and 18, seeds 0, 1, and 42, and a 60-second budget. Checkpoints are 1, 2, 5, 10, 30, and 60 seconds. The first 12-player balanced seed-0 run is cold. A discarded 2-second warmup follows, and the matrix is warm. Rosters above the layout use non-layout library players who own a required instrument, then synthetic players. Synthetic players start idle and own three required instruments at offsets `k`, `k+1`, and `k+3`, never the whole catalog when more than one instrument is required. A 16 or 18 player result is tagged `library-extras`, `synthetic-extras`, or `library-extras+synthetic-extras`.

```powershell
mvn -pl abcmm-optimizer -DskipTests exec:java "-Dexec.mainClass=com.aevoreth.abcmm.optimizer.cli.BenchmarkCli" "-Dexec.args=--database C:\path\to\abc_music_manager.sqlite --setlist 24 --budget-seconds 60"
```

`--fixture public` runs the committed 4-player fixture instead. Omit the budget and it stays at 0 seconds, which checks the harness without a long search. `--rosters`, `--seeds`, `--checkpoints`, `--cold-warm`, and `--warmup-seconds` override the defaults. `--out` writes the same text to another file.

Exit 0 means the report was written. Exit 1 means the arguments, database, or roster size is invalid. Exit 2 means the database file changed size or last-modified time. The 60-second matrix is not part of `mvn test`.

### Development regression checks

`--fixture synthetic` uses a public, seeded 34-song problem with 12 active parts per song and three owned instruments per character. The default layout has 12 characters; roster expansion adds idle characters with limited inventories. It does not load a private database. For a short comparison:

```bash
mvn -pl abcmm-optimizer exec:java \
  -Dexec.mainClass=com.aevoreth.abcmm.optimizer.cli.BenchmarkCli \
  '-Dexec.args=--fixture synthetic --rosters 12,18 --seeds 0,1,42 --budget-seconds 1 --cold-warm false'
```

Keep fixture and search seeds, weights, rosters, JVM, and budgets the same when comparing versions. Candidate counts include rejected moves and starting-assignment attempts; they are not equivalent amounts of CPU work across algorithms.

`SearchBudget.attempts(seed, count)` provides repeatable work limits for tests and experiments, with a one-minute safety deadline. `SolverQualityTest` compares the tiny public fixture against exhaustive enumeration, checks fixed-work repeatability, and compares full scoring against randomized moves, rollback, and independently generated starting assignments. These small exact results do not prove optimality for a real setlist.

Search now checks a feasible greedy starting assignment and tries seeded randomized matchings after local optima, including cases where no pair swap can escape. All preservation costs continue to refer to the original baseline. The original assignment wins score ties. See [implementation and validation notes](../docs/OPTIMIZER_IMPROVEMENTS.md).

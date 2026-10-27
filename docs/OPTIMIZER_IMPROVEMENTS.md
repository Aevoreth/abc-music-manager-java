# Solver and instrument-placement changes

This batch adds multiple feasible starting assignments, reduces temporary allocation in move evaluation, and introduces repeatable quality checks. It also adds soft instrument placement and an expanded user guide. It does not add a global exact solver or claim that a production setlist is optimal.

## Search behavior

- Keep the repaired baseline as the first incumbent. Preserve it on score ties and for a zero-duration budget.
- Evaluate a feasible greedy assignment against the same rules before using it. Never substitute it for the original baseline used by preservation costs.
- After local optima, periodically try independently randomized feasible matchings. Try alternative matchings when pair perturbations cannot move at all. A four-player-cycle regression fixture demonstrates an escape unavailable to pair swaps and three-player cycles.
- Preserve the best incumbent across restarts. Cancellation and deadline checks also run during legal-pair enumeration and between songs during randomized preparation.
- Use reservoir sampling for pair perturbations instead of allocating a list of every legal pair. Check cheap ownership/requirement failures before allocating claim-check arrays. Restore only the changed player slots for single-song moves.
- Rescore a single song and its neighboring transitions without allocating song-sized boolean arrays. Global floating-point totals still use the original canonical summation order. This preserves exact agreement with the independent scorer, including fractional weights.
- Keep full independent scoring on accepted improvements and on the returned incumbent. The randomized-repair tests separately validate pins, required instruments, and every score field over 100 starting matchings.

`SearchBudget.attempts(seed, count)` makes regression experiments independent of normal wall-clock variation. It includes starting-assignment attempts and retains a one-minute safety deadline. Production UI searches retain their time budgets. An attempt count is useful for repeatability; it is not a promise of equal CPU work between algorithms.

## Instrument placement

Band profiles now optionally store instrument-first preferred and avoided character lists. Assigning an instrument outside its preferred group or to an avoided character adds one placement penalty per affected part, multiplied by Role preservation. The two conditions do not double-charge a part. Song part preferences replace this band-level role penalty. Pins remain hard, and ownership remains mandatory.

Character preferences use mutually exclusive No preference, Prefer, Always when present, and Avoid choices. The instrument-first editor offers No preference, Prefer, and Avoid for each character. The placement penalty is shared by that instrument. The default editor value is 10; it can be adjusted or disabled through Role preservation.

The rules live in optional profile JSON, with no SQLite schema migration. Existing profiles read with no placements. Band copies preserve them; character replacement rewrites their references, retaining an existing destination choice on conflict; character deletion removes the references. Older builds that do not recognize the optional field reject it instead of silently discarding settings.

The [user guide](user/optimizer.md) includes the back-row example, the distinction between character-first and instrument-first preferences, song-specific exceptions, and numeric weight examples. The same guide opens from the relevant dialogs.

## Validation

The full reactor install reported 406 tests: 400 passed, one failed, and five skipped. The failure is the pre-existing Linux-relative-path fixture in `FolderDuplicateDetectorTest.detectsDuplicatedFoldersWithoutPreferringAlphabeticalFirst`. The skips are the optional private-reference-database test, two MIDI/audio tests, and two GUI tests disabled by their own headless setup. The diagnostic run used `maven.test.failure.ignore=true` so later modules could execute; its BUILD SUCCESS is not an all-tests-passed claim.

Seventeen added checks cover placement scoring and alias handling, soft ownership fallback, pins, zero role weights, occurrence overrides, persistence and lifecycle operations, malformed profile documents, fixed-work repeatability, exhaustive small-fixture optima, randomized score/rollback consistency, independent starting matchings, the synthetic benchmark CLI, and UI editing/preservation. The subsequently adjusted UI layout passed the nine selected UI/help checks again.

The packaged application's five navigation pages and schema-18 database initialization passed a separate desktop smoke check. The placement editor, character Avoid setting, and direct optimizer-help topic were also opened and inspected under Xvfb. Audio uses the existing fallback on this machine, which has no sound device.

## Synthetic smoke comparison

This is a small development comparison, not the historical private-library benchmark in [OPTIMIZER_BENCHMARK.md](OPTIMIZER_BENCHMARK.md).

The probe used `BenchmarkFixture.synthetic(34, roster, 2026)`: 34 songs, 12 active parts, 8 instruments, three owned instruments per character, and idle extras for the 18-character roster. Weights were smooth 4, total 1, role 1, keep 1, target 2; there were no placement rules. Search seeds were 0, 1, and 42, with one second per solve after three 200-ms warmups. Each implementation ran in a separate Java 21 JVM with `-Xmx512m` in the same Linux cloud environment.

The baseline search, repair, candidate state, moves, and budget classes came from `96afc20` and were compiled against the same current model/scorer with empty placement settings. Allocation was measured on the solver thread using `ThreadMXBean`. Values below are medians across three seeds; score ranges cover those same runs. Counts include rejected candidates, so they are not equivalent operations across implementations.

| Roster | Implementation | Attempts/second | Allocated per solve | Bytes/attempt | Weighted score range |
|---|---|---:|---:|---:|---:|
| 12 | Baseline | 4,658,006 | 465.0 MiB | 104.7 | 1646–1653 |
| 12 | This batch | 4,077,576 | 151.9 MiB | 39.0 | 1634–1672 |
| 18 | Baseline | 1,444,653 | 334.4 MiB | 242.8 | 257–261 |
| 18 | This batch | 1,410,272 | 128.7 MiB | 95.6 | 257–259 |

Allocation was lower in this workload. Throughput and quality did not improve uniformly: two 12-character seeds had worse scores, while another reduced the worst transition from 9 to 8. The 18-character median score stayed the same. These results support continued measurement and show the cost of exploring different starts; they do not justify a universal speed or quality claim. Fixed-work tests are deterministic, but time-budgeted results vary with scheduling and JVM warmup.

For ongoing comparisons, use the committed synthetic fixture through BenchmarkCli, preserve seeds and settings, and record distributions rather than selecting the best seed. Run the private reference matrix only with a locally supplied read-only database. Larger section-rebuild moves, adaptive stopping, and stronger Apply-time snapshot validation remain separate follow-up work.

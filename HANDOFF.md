# Cursor Handoff — Setlist Instrument Optimizer

> Status: **Planning / proof of concept not yet implemented**  
> Project: `Aevoreth/abc-music-manager-java` (Java 21, Maven, Swing, SQLite, MIT)  
> Work branch: **`setlist-optimizer`**, intentionally separate from `master`  
> Date: 2026-10-25  
> Main issue: [#1 — Setlist Optimizer epic](https://github.com/Aevoreth/abc-music-manager-java/issues/1)

## For Cursor: start here

1. **Checkout `setlist-optimizer`**. Keep `master` usable for unrelated ABC Music Manager development. Do not work on `master` or merge automatically.
2. Read this handoff and linked GitHub issues. Begin with **issue #2 (read-only POC loader)**; work through #3, #4, #5 before implementing UI/schema changes.
3. The first deliverable is a **read-only, standalone Java 21 optimizer/benchmark CLI** with deterministic scoring. **Do not implement UI, database migrations, undo, or edit user data during the POC.**
4. Prefer a **custom pure-Java solver** without external solver dependencies; keep the project MIT. Benchmark realistic performance/quality before a production commitment.
5. Do not copy the uploaded user SQLite database into this public repository or log/export sensitive library/player details by default. Use it only as a local read-only benchmark source, with synthetic/sanitized public fixtures.
6. Never silently relax a **hard** restriction to get an assignment. Report infeasibility and explain why.

## What we are optimizing

ABC Music Manager manages LOTRO music, setlists, band/performer layouts, and per-song ABC part-to-player assignments. The requested feature automatically assigns a setlist's required parts to the band's available performers to **make the transitions between songs easier**.

**The definition of a penalized instrument change is critical and settled:**

- A player changes instruments **between adjacent songs** only when they are assigned a part in **both** immediately consecutive songs and those assignments require **different actual physical instruments**.
- If the player is **idle in either adjacent song**, that boundary requires **no penalized switch**. Example: fiddle in song 1 → idle in song 2 → harp in song 3 = **zero** penalized switches. They can equip harp while song 2 plays.
- **Never look back to the last played song** to count switches; previous historical playing is useful for UI context, not for the optimization penalty.
- No incoming transition penalty for the first song. No special penalty for switching after the last song.

Pseudocode:

```java
boolean requiresBetweenSongSwitch(Instrument previous, Instrument current) {
    return previous != null
        && current != null
        && !samePhysicalInstrument(previous, current);
}
```

Instruments must be compared by stable identity or **verified physical equivalence**. Shared instrument family / proficiency / class ability **does not** make two different instruments free to swap. Existing spelling aliases (e.g. Traveler/Traveller) should be treated consistently with current application semantics, with tests.

**Instrument availability/proficiency assumption (settled):**

- `PlayerInstrument.has_instrument = true` means the performer **has and is proficient with** that instrument.
- `has_proficiency` is irrelevant for the optimizer even when stored as false. Do **not** reject a player or infer extra eligibility from character class proficiency.
- A player lacking the exact required actual instrument (or a vetted same-physical-instrument alias) is ineligible. Never assume shared instrument family is interchangeable.
- Instrument ownership is a hard constraint; any additional user exclusions/restrictions are also hard constraints.

**Other fixed behavior:**

- Preserve existing song order and duration; **do not** reorder songs.
- Preserve each ABC arrangement and required parts; **do not** rewrite ABC/MIDI or substitute instruments to improve score.
- Preserve band layout/stage slots; change **assignments of existing parts to performers**, not positions.
- Initially assume each required ABC part is covered exactly once, and a performer plays at most one part per song. Investigate/document whether deliberate doubling needs an explicit feature; never silently discard/duplicate a part.
- Song **occurrences** matter: a repeated song in one setlist can have different assignment preferences and pins.

## Objective: smooth every transition, not just minimize sum

The operator wants a set where most songs require only **1–2** people to switch instruments, even if that leads to a slightly higher total change count, rather than a set with many zero-change boundaries followed by spikes of eight or nine changes.

For transition `t` (songs `t-1` → `t`), let `C[t]` be the **integer count of concurrently-active players** who must change actual instruments.

Default user-adjustable target `T = 2` changes/transition. Suggested overload metric:

```text
overload = sum over t of max(0, C[t] - T)^2
totalChanges = sum over t of C[t]
```

Proposed **four independently adjustable nonnegative band-level weights**, including zero:

1. **Smooth transitions**: penalize overload / spikes above T (highest suggested default).
2. **Minimize total changes**: reduce total actual adjacent-song switches.
3. **Preserve established player roles**: player↔instrument/role/part affinities.
4. **Preserve existing assignments**: avoid changing tested song layouts or current setlist assignments unnecessarily (unless pinned, these remain *soft*).

Example concept (not final normalized implementation):

```text
weightedCost =
    W_smooth * normalizedOverload
  + W_total  * normalizedTotalChanges
  + W_roles  * normalizedRoleDeviation
  + W_keep   * normalizedDeviationFromBaseline
  + scopedPreferenceCosts
```

The formula and weighting *scale/normalization* must be explicit and stable, so moving one weight slider has a comprehensible impact. Users may set a weight to zero. Display **raw** changes separately from weighted penalties.

**Scoped preferences** (can override/default from band profile, but remain soft unless pinned):
- Per-player penalty multiplier for having to change instruments at transitions.
- Preferred player↔instrument/role assignment and weights (e.g. prefer a drummer to remain on drums).
- Preferred performer for a given part and/or part category.
- Per-setlist-item/per-part preferences and different baseline-preservation strengths.
- Optional fairness/balance or max-simultaneous-change refinements can follow after core benchmarks, not required for POC.

**Hard pins**:
- Pin a specific part of a specific **setlist item occurrence** to a specific player, e.g. (`setlist_item_id`, `part_number`, `player_id`).
- Pins are **absolute**, not weight 100/100. They must never be moved, even if conflicting with soft preferences.
- Conflicting pins, unowned required instruments, missing performers, or insufficient capacity must yield explicit infeasibility information.

Optimization should focus search on **worst transitions** while still tracking total changes and preferences. It should exploit idle performer slots as **free instrument-change opportunities** during the intervening song. With 16–18 performers serving 10–12 simultaneous parts, active↔idle rotations may offer major benefits.

## Reference database / truth-check fixture

A user-provided SQLite database was supplied to the conversation as `abc_music_manager.sqlite`. It is **not in the GitHub repository** and must **not be committed**, especially to this public project. The user can provide it locally to Cursor as necessary; if unavailable, build public synthetic fixtures and retain expected measurements below as reference.

Verified by read-only SQLite inspection of that upload:

| Field | Value |
|---|---|
| Setlist | `RAVE 26-10-16` |
| `Setlist.id` | `24` |
| `Setlist.band_layout_id` | `2` (`Rowanwoods RAVE`) |
| Locked | `true` (never mutate) |
| Songs / setlist items | `34` |
| Total required parts | `396` |
| Part distribution | `23 × 12`, `10 × 11`, `1 × 10` |
| Different required instrument IDs | `20` |
| Active layout performers | `12` |
| Adjacent song transitions | `33` |
| Existing penalized changes | **`148`** (correct rule: immediate adjacent songs only) |
| Existing maximum changes at one transition | **`6`** |
| Existing transition histogram | 2:2, 3:4, 4:9, 5:12, 6:6 |
| Ownership/proficiency rows in active band | `262` owned with proficiency flag 0; `14` not owned with proficiency flag 0 |

The earlier quoted count **156** used the superseded *last-played-across-idle* rule and **must not** be used for scoring or benchmarks. Likewise, earlier illustrative solver times and improved-solution scores were not production benchmarks and must not be presented as measurements.

To calculate baseline correctly, load effective player assignment per song:
1. `Song.parts` JSON for required part number and `instrument_id`.
2. `SetlistItem.song_layout_id` → `SongLayoutAssignment` baseline mapping (if present).
3. `SetlistBandAssignment` rows override matching players **even if their `part_number` is SQL NULL**.
4. Evaluate only players in `BandLayoutSlot` of the selected setlist's layout; ignore stale/historical assignments for others.
5. For each pair of adjacent items, count active-in-both players with different actual instruments.

Read-only baseline verification should reproduce the histogram above; reject hidden assumptions such as ownership/proficiency mismatch as blockers without diagnostics. Some historical assignments may be inconsistent; report them rather than silently rewriting input.

For 16/18-player benchmarks, add actual available performers and their recorded instrument inventories if user supplies them. Otherwise use **clearly marked synthetic scenarios** with realistic explicit inventories and publish assumptions; do not label them actual Rowanwoods outcomes.

## Relevant existing code (confirmed on master at handoff)

- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/band/PlayerRepository.java` — list players, instruments, and `PlayerInstrumentInfo` ownership.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/band/BandRepository.java` — layout membership and slots.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/band/SongLayoutRepository.java` — song layout assignments.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/setlist/SetlistRepository.java` — setlist items/overrides; has individual `upsertBandAssignment` but **not** a whole-setlist transactional optimization method.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/setlist/SetlistBandAssignmentInfo.java` — per-item player→part override, including nullable part.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/setlist/SetlistItemInfo.java` — ordered songs, `partsJson`, `songLayoutId`.
- `abcmm-domain/src/main/java/com/aevoreth/abcmm/domain/band/LotroInstrumentDefaults.java` — LOTRO class/skill defaults; **not** a source of extra solver eligibility beyond possession.
- `abcmm-app/src/main/java/com/aevoreth/abcmm/ui/SetlistBandAssignmentPanel.java` — editor displays last-played instrument using a backward search. Keep that for history if useful, but correct the **change warning** to immediate previous song; share scoring logic with domain.
- `abcmm-app/src/main/java/com/aevoreth/abcmm/ui/SetlistsPanel.java` — future UI entry point.
- `abcmm-storage/src/main/java/com/aevoreth/abcmm/storage/SqliteSetlistRepository.java` — setlist SQL and per-item overrides. Note `snapshotSongLayoutToItem` copies library defaults if overrides empty.
- `abcmm-storage/src/main/java/com/aevoreth/abcmm/storage/SqliteDatabase.java` — SQLite connection currently returns to autocommit after database initialization. Transactional **bulk** updates need explicit management.
- `abcmm-storage/src/main/java/com/aevoreth/abcmm/storage/SchemaMigrator.java` — production schema upgrade point.

The app is a multi-module Java 21 Maven project with MIT root `LICENSE`. Maestro v0.5.0b playback work is separate and not in scope.

## Planned architecture / data contracts

**POC module:** `abcmm-optimizer` or `abcmm-optimizer-poc` (select one and document in README), CLI first.

Suggested Java types (names flexible; responsibilities matter):
- `OptimizationProblem`: immutable ordered song occurrences; required part IDs + exact instrument IDs; band performers and possessions; existing effective/override mapping; optional pins/preferences.
- `OptimizationRules`: normalized weights, target, per-player/part preferences, ownership restrictions and absolute pins.
- `AssignmentValidator`: hard constraint feasibility; no under-assignment, double booking or unauthorized instruments.
- `AssignmentScorer`: canonical `C[t]`, total, overload, role and keep penalties, score breakdown and histogram.
- `AssignmentSolver`: accepts immutable problem/rules, time/effort limit, seed, progress/cancel; returns best valid assignment (or explicit no-feasible-result).
- `OptimizationResult`: full occurrence/player assignment proposal + validation, metrics, seed, elapsed time, not-proven-optimal status.
- `BenchmarkRunner`: read-only CLI harness, reproducible scenarios and reports.

**Do not allow the optimizer core to depend on Swing/JDBC**. A storage adapter creates the problem snapshot; solver uses domain value objects and compact indexed arrays. Shared scoring logic should be authoritative for later UI reporting.

Recommended custom **anytime heuristic**:
1. Validate input and build valid starting solution from current assignments, repairing missing/illegal assignments by feasible matching while honoring pins. Fail with diagnostics if impossible.
2. Fast swap and active↔idle rotation moves within one song.
3. Multi-part and multi-song/window moves to escape traps and deal with worst transitions.
4. Hill-climb plus optional annealing/perturb-and-restart, seeded for reproduction.
5. Track **best valid** result separate from exploratory state. Use incremental delta scores for the two adjacent transitions affected by changing one song, with full recheck on return.
6. Permit user-configurable effort/search budget; support prompt cancellation; no GUI thread blocking when integrated.

Runtime targets discussed (**not yet measured**): roughly 1–5s for 12 players, 1–8s for 16, 1–10s for 18 in an optimized Java implementation, with a default budget around 10s and alternative shorter/longer modes. Performance acceptance must come from benchmarks, not these estimates.

### POC CLI concept

```powershell
# Example only — finalize real syntax and jar name during issue #2
java -jar abcmm-optimizer-poc.jar `
  --database "C:\\path\\to\\abc_music_manager.sqlite" `
  --setlist 24 `
  --players 12 `
  --budget-seconds 10 `
  --seed 42
```

Must **open SQLite read-only**, with no migration, no locks implying writes, no changes to source files or song layouts. Optionally write an anonymized benchmark report elsewhere, **not** into user DB.

## Implementation plan / GitHub issues

**Milestone A — read-only proof of concept (no production migration):**

- [#2 POC 1/4](https://github.com/Aevoreth/abc-music-manager-java/issues/2): load real/synthetic datasets and validate feasibility; no DB writes.
- [#3 POC 2/4](https://github.com/Aevoreth/abc-music-manager-java/issues/3): canonical immediate-adjacent scoring, tunable weights, absolute pins, invariant tests.
- [#4 POC 3/4](https://github.com/Aevoreth/abc-music-manager-java/issues/4): custom Java heuristic, swaps/idle rotation/multi-song moves, cancellation, best-valid anytime results.
- [#5 POC 4/4](https://github.com/Aevoreth/abc-music-manager-java/issues/5): 12/16/18-player benchmark, smooth-vs-total tradeoffs and go/no-go report.

**Milestone B — production features (only after benchmark review):**

- [#6 Production 1/5](https://github.com/Aevoreth/abc-music-manager-java/issues/6): versioned persisted band-specific rule profiles and scoped preferences.
- [#7 Production 2/5](https://github.com/Aevoreth/abc-music-manager-java/issues/7): Swing band settings, per-player/part controls and hard per-setlist-item pins.
- [#8 Production 3/5](https://github.com/Aevoreth/abc-music-manager-java/issues/8): transactional apply and **persistent whole-operation undo**.
- [#9 Production 4/5](https://github.com/Aevoreth/abc-music-manager-java/issues/9): preview/progress/cancel, score comparison and explicit apply/undo controls.
- [#10 Production 5/5](https://github.com/Aevoreth/abc-music-manager-java/issues/10): correct existing warning semantics; tests/docs/accessibility and merge readiness.

**Execution order:** #2 → #3 → #4 → #5 → reassess solver quality → #6 → #7/#8 as dependencies allow → #9 → #10.

No automatic PR/merge until the user has evaluated POC performance and signaled readiness.

## Persistent undo design (production only)

**Decision:** prefer **SQLite persistent operation journal**, not memory-only undo; **one entire optimization is one undo action** and should survive application restarts.

Suggested `SetlistOptimizationHistory` stores operation ID, setlist ID, timestamp, before/after assignment **row-state** snapshots (including absence vs explicit `NULL` overrides), rules snapshot, related setlist/version/fingerprint and status.

On apply:
1. Verify not locked and expected setlist revision/contents still match optimization snapshot.
2. Begin a SQLite transaction on appropriate connection, capture exact before state, persist history + all optimized overrides, commit **together**. Roll back everything if any step fails.
3. Do not change `SongLayoutAssignment`, `Song.parts`, ABC files, song order, or stage coordinates.

On undo:
1. Verify current state exactly matches known after-state and relevant setlist structure (including song list/order, layout and pins as appropriate). Any manual changes after optimize should trigger **conflict, not silent data loss**.
2. Restore original override **rows** exactly, including whether the override row was absent or explicitly null, in a **single transaction**; mark history undone.
3. Locked sets remain protected. Optional redo later.

Starting retention suggestion: 30 days and/or last 10 optimization operations per setlist, subject to safe deletion policy. No UI undo machinery required in POC.

## Benchmark requirements and acceptance tests

- **Scoring truth:** 34 songs, 33 boundaries, 148 penalized adjacent-only changes, worst transition 6 on user reference setlist. Histogram: 2:2, 3:4, 4:9, 5:12, 6:6.
- **Hard constraints:** required part coverage, one part per player in a song, possession, all absolute pins, no unapproved substitutions/doubling.
- **Idle behavior:** fiddle → idle → harp is **free**; fiddle → harp across consecutive songs counts 1; same actual instrument across adjacent songs counts 0.
- **Scoped rules:** changing weight to 0 removes that preference; stronger player weight changes choice without falsifying raw switch counts; two occurrences of same song can have different pins.
- **Difficult cases:** nonexistent required instrument, missing player, insufficient active capacity, conflicting pins, duplicate part assignments, explicit null vs absent override, same-family/different instrument, repeated song.
- **Performance:** 12/16/18 realistic rosters; multiple deterministic seeds, warm and cold runs; quality checkpoints at 1/2/5/10/30/60 seconds; cost/quality and change-count histogram, including median and variance. Benchmarks should clearly state fake vs real roster inputs.
- **Output:** always validate full score and hard rules after search; report score components and infeasibility diagnostics; no claims of optimality without proof.
- **Production safety:** locked setlist can't be applied to, whole optimization is reversible exactly once even after restart, conflicts avoid overwriting later edits, SQL rollback tested, no GUI block or source file mutation.

## Engineering / branching conventions

- Work and commit exclusively to `setlist-optimizer`; do not modify `master`. Create small issue-scoped commits for review.
- Before touching production storage, read `docs/ARCHITECTURE.md`, `docs/PYTHON_PARITY.md`, repository architecture tests and current SQL schema evolution.
- Maintain module boundaries: pure optimizer/domain; storage adapter separate; Swing view separate.
- Java 21, Maven/JUnit, pure Java license under existing **MIT** root license. No Apache solver needed initially; a license-compatible dependency could be considered only if POC shows custom search unacceptable.
- Restrict all raw user database handling to local development. Never commit a complete private DB, personally identifying data, exports, or private song library to GitHub just to run CI.
- Existing upstream Maestro playback fixes are unrelated; avoid their modules unless needed for metadata reading.

## What is *not* decided yet

- Exact normalization and default numerical values for all four weights.
- Whether to expose a max-single-transition objective in addition to quadratic overload.
- How intentionally doubled parts should be formally represented.
- Whether actual 16/18-member inventories are configured in the user's working DB yet.
- Whether to add a full weighted fairness rule in v1.
- Search algorithm tuning and the final default runtime budget **must come from the benchmark**, not assumptions.
- Production undo retention controls beyond suggested default.

Treat these as design questions for tests/benchmark outcomes, not reasons to block the initial POC.

## Definition of success

A user can choose their band and fixed-order setlist, specify how much they value smooth transitions vs other preferences, pin important part assignments, and receive a valid optimized proposal quickly. They can inspect how many *actual between-song* instrument changes it requires, apply it safely, and undo the entire operation with one action even after a restart. Every change remains confined to setlist-level assignments; `master` and original ABC music stay available and unmodified.

**Next engineering step:** start [issue #2](https://github.com/Aevoreth/abc-music-manager-java/issues/2) on the `setlist-optimizer` branch.

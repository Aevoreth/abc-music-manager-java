# Setlist optimizer benchmark

Verdict: **go**, with a measured limit on the 12-player roster. The custom solver is fast enough to use, and on the reference setlist it improves the smooth-transition penalty within 10 seconds. It does not bring the worst transition below 6 when only the 12 layout players are available. This is not a proof of optimality.

## How this was measured

The run used `BenchmarkCli` on setlist 24 from the local application database, opened read-only. `ABCMM_REFERENCE_DB` was not set. The file's size and last-modified time were the same after the run.

The matrix is one JVM process:

- Cold cell: 12 layout players, balanced weights, seed 0, before any other solve.
- Discarded 2-second warmup on the public fixture.
- Warm matrix: rosters 12, 16, and 18; variants total-first, smooth-first, balanced, pinned, and restrictive; seeds 0, 1, and 42; 60-second budget.
- Checkpoints at 1, 2, 5, 10, 30, and 60 seconds record the best feasible score so far.
- An infeasible unowned pin is checked with a zero budget and is not searched.
- `mvn test` does not run this matrix. It uses the public fixture at budget 0.

Environment: Java 21.0.11, OpenJDK 64-Bit Server VM, Windows 11 amd64, 24 processors, AMD64 Family 26 Model 68 Stepping 0. Wall clock for the process was about 46 minutes. Search stops at the budget even when a better score might still exist. Reported rates are evaluated neighborhood attempts, including rejects, divided by elapsed time.

Weights all use target 2. Total-first is total weight 1 and the other weights 0. Smooth-first is smooth weight 1 and the other weights 0. Balanced and pinned use all four weights at 1. Restrictive uses smooth-first after each player keeps the instruments used by the baseline plus at most one other instrument. The greedy comparison is one forward pass: pins first, then the eligible player with the smallest incoming switch and the smallest player id. It ignores later songs. Idle on the previous song costs nothing.

A player adds to a transition only when that player is assigned in both adjacent songs on different physical instruments. Extra players start idle, so the baseline stays 148 changes.

## Reference check

Setlist 24 matched the reference shape: 34 songs, 396 parts, 20 instruments, 33 transitions, 12 layout players. The baseline is 148 penalized changes, worst transition 6, histogram `2=2, 3=4, 4=9, 5=12, 6=6`. Overload against target 2 is 244, and 31 transitions are above the target.

Roster 16 added 4 off-layout library players who already own a required instrument. Roster 18 added 6. No synthetic players were required. Those inventories are real recorded possessions, not a full-catalog assumption. Counts below are medians across the three warm seeds. Where the minimum and maximum differ, both are shown.

## 12 layout players

The repaired baseline is the start. Every warm 12-player search used the full 60 seconds, at about 630,000 to 680,000 candidate moves per second. The first strict improvement was within a few milliseconds. On smooth-first seed 0 the best score was in place by 565 ms, and the 1-second checkpoint already matched the 60-second result.

| Variant | Changes | Worst | Overload | Greedy changes | Greedy worst | Greedy overload |
|---|---:|---:|---:|---:|---:|---:|
| Baseline | 148 | 6 | 244 | 134 | 6 | 218 |
| Total-first | 111 | 6 | 120 | 134 | 6 | 218 |
| Smooth-first | 115 | 6 | 120 | 134 | 6 | 218 |
| Balanced | 115 (113–115) | 6 | 120 (120–122) | 134 | 6 | 218 |
| Pinned | 115 (115–116) | 6 | 120 (120–121) | 134 | 6 | 218 |
| Restrictive | 118 (118–119) | 6 | 138 (138–139) | no cover |  |  |

Smooth-first seed 0 ended at histogram `1=1, 2=7, 3=9, 4=8, 5=7, 6=1`, with 25 transitions still above target 2. The three seeds agreed on 115 changes, worst 6, and overload 120. The 10-second checkpoint is the same overload. That overload is below both the baseline (244) and the greedy pass (218), which is the go rule. The worst transition stayed 6 for every 12-player variant, including the cold balanced run (115 changes, overload 122).

The restrictive greedy pass could not cover every part. The heuristic still returned a feasible assignment. The infeasible pin was rejected with `pin_conflict` and no score.

## 16 players, library extras

Four real off-layout players. The idle baseline is still 148 changes and worst 6. Greedy falls to 16 changes, worst 4, overload 4.

| Variant | Changes | Worst | Overload |
|---|---:|---:|---:|
| Total-first | 9 | 2 | 0 |
| Smooth-first | 62 | 2 | 0 |
| Balanced | 41 (41–46) | 2 | 0 |
| Pinned | 42 (40–46) | 2 (2–3) | 0 (0–1) |
| Restrictive | 97 (96–97) | 5 | 69 |

Total-first beats the greedy pass on both total changes and the worst transition. Smooth-first drives overload to 0 and the worst transition to 2, and it uses more total changes than greedy because that weight does not charge them. The 10-second checkpoints match these medians. A 16-player total-first seed used about 560,000 candidates per second and had its best score by about 5 seconds.

## 18 players, library extras

Six real off-layout players. Greedy already reaches 0 changes, worst 0, and overload 0, because there is enough idle room to switch during songs where a player is unassigned.

| Variant | Changes | Worst | Overload |
|---|---:|---:|---:|
| Total-first | 0 | 0 | 0 |
| Smooth-first | 61 | 2 | 0 |
| Balanced | 43 (37–44) | 2 | 0 |
| Pinned | 42 (34–48) | 2 | 0 |
| Restrictive | 98 (97–98) | 5 | 70 (69–70) |

Total-first matches the greedy zero. Smooth-first stops at a flat overload of 0 with worst 2 and 61 changes. Once no transition is above the target, that objective has nothing left to say about the remaining switches, so it does not find the zero-switch assignment the greedy pass found. A total-first seed had its best score by about 1.5 seconds, at about 530,000 candidates per second, and then kept evaluating until the budget.

## Go rule

Go requires all of the following. Each one held.

- The 12-player baseline is 148 changes, worst 6, and the reference histogram.
- Returned search assignments validated. The planted infeasible pin did not receive a score.
- The database file was unchanged.
- The warm median smooth-first result on the 12-player roster improved overload versus both the baseline and the greedy pass by 10 seconds (120 versus 244 and 218). The worst transition did not improve. The rule accepts either one.

The limit to carry into the production decision: on the current 12-player layout, an hour of the same search would not be justified by this run. The useful score was in place in under a second, and a transition of 6 remained at 60 seconds. With 16 or 18 recorded players, worst transitions of 0–2 and overload 0 are already available, and a total-change weight matters once the smooth penalty has flattened. Restrictive ownership is feasible for the heuristic and is harder for the one-pass greedy cover. No solver retuning was done in this benchmark.

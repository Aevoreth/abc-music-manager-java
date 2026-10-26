package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AssignmentScorerTest {

    private static final Map<Long, String> NAMES = Map.of(
            1L, "Basic Lute",
            2L, "Basic Harp",
            3L, "Traveler's Trusty Fiddle",
            4L, "Traveller's Trusty Fiddle",
            5L, "Bass Lute");

    @Test
    void adjacentDifferentInstrumentsCountOneAndTheSameInstrumentCountsZero() {
        ScoreBreakdown different = accepted(twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 2L), idle(6, 11)));
        assertEquals(List.of(1), different.changesPerTransition());
        assertEquals(1, different.totalChanges());
        assertEquals(1, different.worstTransition());
        assertEquals(0, different.overload());
        assertEquals(1.0, different.weightedSwitchBurden(), 0.0);
        assertEquals(1.0, different.weightedScore(), 0.0);

        ScoreBreakdown same = accepted(twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 1L), idle(6, 11),
                1L, 1L));
        assertEquals(List.of(0), same.changesPerTransition());
        assertEquals(0, same.totalChanges());
        assertEquals(0.0, same.weightedScore(), 0.0);
    }

    @Test
    void samePhysicalInstrumentOnADifferentPartIsNotASwitch() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1L), player(11, 1L)),
                List.of(
                        song(5, 100, new RequiredPart(1, 1L)),
                        song(6, 101, new RequiredPart(1, 1L), new RequiredPart(2, 1L))),
                List.of(
                        play(5, 10, 1, 1L), idle(5, 11),
                        play(6, 10, 2, 1L), play(6, 11, 1, 1L)),
                List.of());
        ScoreBreakdown breakdown = accepted(problem);
        assertEquals(List.of(0), breakdown.changesPerTransition());
        assertEquals(0, breakdown.totalChanges());
    }

    @Test
    void idleBeforeBetweenAndAfterDoesNotLookBack() {
        ScoreBreakdown before = accepted(twoSongs(
                idle(5, 10), play(5, 11, 1, 3L),
                play(6, 10, 1, 3L), idle(6, 11),
                3L, 3L));
        assertEquals(List.of(0), before.changesPerTransition());

        ScoreBreakdown between = accepted(problem(
                List.of(player(10, 2L, 3L), player(11, 2L, 3L)),
                List.of(
                        song(5, 100, new RequiredPart(1, 3L)),
                        song(6, 101, new RequiredPart(1, 2L)),
                        song(7, 102, new RequiredPart(1, 2L))),
                List.of(
                        play(5, 10, 1, 3L), idle(5, 11),
                        idle(6, 10), play(6, 11, 1, 2L),
                        play(7, 10, 1, 2L), idle(7, 11)),
                List.of()));
        assertEquals(List.of(0, 0), between.changesPerTransition());
        assertEquals(0, between.totalChanges());
        assertEquals(0, between.worstTransition());
        assertEquals(Map.of(0, 2), between.histogram());

        ScoreBreakdown after = accepted(problem(
                List.of(player(10, 1L, 2L), player(11, 1L, 2L)),
                List.of(
                        song(5, 100, new RequiredPart(1, 1L)),
                        song(6, 101, new RequiredPart(1, 2L)),
                        song(7, 102, new RequiredPart(1, 2L))),
                List.of(
                        play(5, 10, 1, 1L), idle(5, 11),
                        play(6, 10, 1, 2L), idle(6, 11),
                        idle(7, 10), play(7, 11, 1, 2L)),
                List.of()));
        assertEquals(List.of(1, 0), after.changesPerTransition());
        assertEquals(1, after.totalChanges());
        assertEquals(1, after.worstTransition());
        assertTrue(after.format().contains("histogram: 0=1,1=1"));
    }

    @Test
    void aliasesAreFreeAndADifferentInstrumentInTheSameFamilyIsNot() {
        ScoreBreakdown alias = accepted(twoSongs(
                play(5, 10, 1, 3L), idle(5, 11),
                play(6, 10, 1, 4L), idle(6, 11),
                3L, 4L));
        assertEquals(0, alias.totalChanges());

        ScoreBreakdown family = accepted(twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 5L), idle(6, 11),
                1L, 5L));
        assertEquals(1, family.totalChanges());
    }

    @Test
    void repeatedSongOccurrencesDoNotSharePinsOrPartPreferences() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1L, 2L), player(11, 1L, 2L)),
                List.of(
                        song(5, 50, new RequiredPart(1, 1L)),
                        song(6, 50, new RequiredPart(1, 2L))),
                List.of(
                        play(5, 10, 1, 1L), idle(5, 11),
                        idle(6, 10), play(6, 11, 1, 2L)),
                List.of(new Pin(5, 1, 10), new Pin(6, 1, 11)));
        OptimizationRules rules = rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(
                        new InstrumentAffinity(10, 2L, 7),
                        new InstrumentAffinity(11, 1L, 9)),
                List.of(new PartPreference(5, 1, 11, 4)),
                Map.of(),
                Map.of());
        ScoreBreakdown breakdown = accepted(problem, Assignment.fromBaseline(problem), rules);
        assertEquals(0, breakdown.totalChanges());
        assertEquals(13.0, breakdown.roleDeviation(), 0.0);
        assertEquals(13.0, breakdown.weightedScore(), 0.0);

        ScoreBreakdown activeTwice = accepted(twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 2L), idle(6, 11)));
        assertEquals(1, activeTwice.totalChanges());

        Assignment swapped = new Assignment(List.of(
                slot(5, 11, 1, 1L), slot(5, 10, null, null),
                slot(6, 10, 1, 2L), slot(6, 11, null, null)));
        ScoreOutcome.Rejected conflict = rejected(problem, swapped, rules);
        assertTrue(has(conflict, "pin_conflict"));
    }

    @Test
    void partPreferenceOnOneOccurrenceDoesNotChargeTheOtherCopy() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1L), player(11, 1L)),
                List.of(
                        song(5, 50, new RequiredPart(1, 1L)),
                        song(6, 50, new RequiredPart(1, 1L))),
                List.of(
                        idle(5, 10), play(5, 11, 1, 1L),
                        idle(6, 10), play(6, 11, 1, 1L)),
                List.of());
        OptimizationRules rules = rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(new PartPreference(5, 1, 10, 5)),
                Map.of(),
                Map.of());
        ScoreBreakdown breakdown = accepted(problem, Assignment.fromBaseline(problem), rules);
        assertEquals(0, breakdown.totalChanges());
        assertEquals(5.0, breakdown.roleDeviation(), 0.0);
    }

    @Test
    void zeroWeightsRemoveContributionsWithoutChangingRawCounts() {
        OptimizationProblem problem = problem(
                List.of(player(10, 1L, 2L), player(11, 1L, 2L)),
                List.of(
                        song(5, 100, new RequiredPart(1, 1L)),
                        song(6, 101, new RequiredPart(1, 2L))),
                List.of(
                        play(5, 10, 1, 1L), idle(5, 11),
                        play(6, 10, 1, 2L), idle(6, 11)),
                List.of());
        Assignment candidate = new Assignment(List.of(
                slot(5, 11, 1, 1L), slot(5, 10, null, null),
                slot(6, 11, 1, 2L), slot(6, 10, null, null)));
        OptimizationRules rules = rules(
                0, 0, 0, 0, 0, 1,
                Map.of(11L, 8.0),
                List.of(),
                List.of(new PartPreference(5, 1, 10, 3)),
                Map.of(),
                Map.of());
        ScoreBreakdown breakdown = accepted(problem, candidate, rules);
        assertEquals(1, breakdown.totalChanges());
        assertEquals(1, breakdown.overload());
        assertEquals(8.0, breakdown.weightedSwitchBurden(), 0.0);
        assertEquals(3.0, breakdown.roleDeviation(), 0.0);
        assertEquals(4, breakdown.changedAssignments());
        assertEquals(4.0, breakdown.keepDeviation(), 0.0);
        assertEquals(0.0, breakdown.smoothContribution(), 0.0);
        assertEquals(0.0, breakdown.totalContribution(), 0.0);
        assertEquals(0.0, breakdown.roleContribution(), 0.0);
        assertEquals(0.0, breakdown.keepContribution(), 0.0);
        assertEquals(0.0, breakdown.weightedScore(), 0.0);
    }

    @Test
    void playerMultiplierChangesBurdenAndNotTheDisplayedCount() {
        OptimizationRules rules = rules(
                1, 1, 1, 1, 2, 2,
                Map.of(10L, 5.0),
                List.of(),
                List.of(),
                Map.of(),
                Map.of());
        OptimizationProblem bothSwitch = problem(
                List.of(player(10, 1L, 2L), player(11, 1L, 2L)),
                List.of(
                        song(5, 100, new RequiredPart(1, 1L), new RequiredPart(2, 2L)),
                        song(6, 101, new RequiredPart(1, 2L), new RequiredPart(2, 1L))),
                List.of(
                        play(5, 10, 1, 1L), play(5, 11, 2, 2L),
                        play(6, 10, 1, 2L), play(6, 11, 2, 1L)),
                List.of());
        ScoreBreakdown both = accepted(bothSwitch, Assignment.fromBaseline(bothSwitch), rules);
        assertEquals(2, both.totalChanges());
        assertEquals(7.0, both.weightedSwitchBurden(), 0.0);

        ScoreBreakdown silent = accepted(twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 2L), idle(6, 11)),
                rules(1, 1, 1, 1, 2, 1, Map.of(10L, 0.0), List.of(), List.of(), Map.of(), Map.of()));
        assertEquals(1, silent.totalChanges());
        assertEquals(0.0, silent.weightedSwitchBurden(), 0.0);
        assertEquals(0.0, silent.totalContribution(), 0.0);
    }

    @Test
    void roleWeightZeroKeepsTheDeviationAndDropsItsContribution() {
        OptimizationProblem problem = oneSong(idle(5, 10), play(5, 11, 1, 1L));
        OptimizationRules rules = rules(
                1, 1, 0, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(new PartPreference(5, 1, 10, 2)),
                Map.of(),
                Map.of());
        ScoreBreakdown breakdown = accepted(problem, Assignment.fromBaseline(problem), rules);
        assertEquals(2.0, breakdown.roleDeviation(), 0.0);
        assertEquals(0.0, breakdown.roleContribution(), 0.0);
        assertEquals(0.0, breakdown.weightedScore(), 0.0);
    }

    @Test
    void partPreferenceReplacesAffinityAndAMissDoesNotStack() {
        OptimizationProblem satisfied = problem(
                List.of(player(10, 1L, 2L, 3L), player(11, 1L, 2L, 3L)),
                List.of(song(5, 100, new RequiredPart(1, 3L))),
                List.of(play(5, 10, 1, 3L), idle(5, 11)),
                List.of());
        ScoreBreakdown replaced = accepted(
                satisfied,
                Assignment.fromBaseline(satisfied),
                rules(1, 1, 1, 1, 2, 1,
                        Map.of(),
                        List.of(new InstrumentAffinity(10, 1L, 9)),
                        List.of(new PartPreference(5, 1, 10, 4)),
                        Map.of(),
                        Map.of()));
        assertEquals(0.0, replaced.roleDeviation(), 0.0);

        OptimizationProblem missed = oneSong(idle(5, 10), play(5, 11, 1, 1L));
        ScoreBreakdown stacked = accepted(
                missed,
                Assignment.fromBaseline(missed),
                rules(1, 1, 1, 1, 2, 1,
                        Map.of(),
                        List.of(new InstrumentAffinity(11, 2L, 9)),
                        List.of(new PartPreference(5, 1, 10, 4)),
                        Map.of(),
                        Map.of()));
        assertEquals(4.0, stacked.roleDeviation(), 0.0);
    }

    @Test
    void affinityUsesTheHighestMissAndAnAliasCountsAsAMatch() {
        OptimizationProblem missed = problem(
                List.of(player(10, 1L, 2L, 3L), player(11, 1L, 2L, 3L)),
                List.of(song(5, 100, new RequiredPart(1, 3L))),
                List.of(play(5, 10, 1, 3L), idle(5, 11)),
                List.of());
        ScoreBreakdown highest = accepted(
                missed,
                Assignment.fromBaseline(missed),
                rules(1, 1, 1, 1, 2, 1,
                        Map.of(),
                        List.of(new InstrumentAffinity(10, 1L, 2), new InstrumentAffinity(10, 2L, 5)),
                        List.of(),
                        Map.of(),
                        Map.of()));
        assertEquals(5.0, highest.roleDeviation(), 0.0);
        assertEquals(5.0, highest.weightedScore(), 0.0);

        OptimizationProblem alias = problem(
                List.of(player(10, 3L, 4L), player(11, 3L, 4L)),
                List.of(song(5, 100, new RequiredPart(1, 4L))),
                List.of(play(5, 10, 1, 4L), idle(5, 11)),
                List.of());
        ScoreBreakdown matched = accepted(
                alias,
                Assignment.fromBaseline(alias),
                rules(1, 1, 1, 1, 2, 1,
                        Map.of(),
                        List.of(new InstrumentAffinity(10, 3L, 5)),
                        List.of(),
                        Map.of(),
                        Map.of()));
        assertEquals(0.0, matched.roleDeviation(), 0.0);
    }

    @Test
    void aPinStaysHardWhileADisagreeingPreferenceStillAddsRoleCost() {
        OptimizationProblem problem = oneSong(
                play(5, 10, 1, 1L), idle(5, 11),
                List.of(new Pin(5, 1, 10)));
        OptimizationRules rules = rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(new PartPreference(5, 1, 11, 2)),
                Map.of(),
                Map.of());
        ScoreBreakdown honored = accepted(problem, Assignment.fromBaseline(problem), rules);
        assertEquals(2.0, honored.roleDeviation(), 0.0);

        Assignment moved = new Assignment(List.of(slot(5, 11, 1, 1L), slot(5, 10, null, null)));
        ScoreOutcome.Rejected rejected = rejected(problem, moved, rules);
        assertTrue(has(rejected, "pin_conflict"));
        assertFalse(rejected.diagnostics().isEmpty());
    }

    @Test
    void keepUsesThePartMultiplierThenTheItemMultiplier() {
        OptimizationProblem problem = oneSong(play(5, 10, 1, 1L), idle(5, 11));
        Assignment moved = new Assignment(List.of(slot(5, 10, null, null), slot(5, 11, 1, 1L)));
        ScoreBreakdown partWins = accepted(problem, moved, rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(5L, 10.0),
                Map.of(new OccurrencePart(5, 1), 3.0)));
        assertEquals(2, partWins.changedAssignments());
        assertEquals(6.0, partWins.keepDeviation(), 0.0);
        assertEquals(6.0, partWins.keepContribution(), 0.0);

        ScoreBreakdown itemOnly = accepted(problem, moved, rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(5L, 2.0),
                Map.of()));
        assertEquals(4.0, itemOnly.keepDeviation(), 0.0);
    }

    @Test
    void overloadIsTheSquaredExcessAndASingleSongHasNoTransition() {
        List<Performer> performers = new ArrayList<>();
        List<RequiredPart> lutes = new ArrayList<>();
        List<RequiredPart> harps = new ArrayList<>();
        List<BaselineAssignment> baseline = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long playerId = 10 + i;
            performers.add(player(playerId, 1L, 2L));
            lutes.add(new RequiredPart(i + 1, 1L));
            harps.add(new RequiredPart(i + 1, 2L));
            baseline.add(play(5, playerId, i + 1, 1L));
            baseline.add(play(6, playerId, i + 1, 2L));
        }
        ScoreBreakdown spike = accepted(problem(
                performers,
                List.of(new SongOccurrence(5, 100, 0, lutes), new SongOccurrence(6, 101, 1, harps)),
                baseline,
                List.of()));
        assertEquals(List.of(4), spike.changesPerTransition());
        assertEquals(4, spike.totalChanges());
        assertEquals(4, spike.worstTransition());
        assertEquals(4, spike.overload());
        assertEquals(8.0, spike.weightedScore(), 0.0);
        assertEquals(Map.of(4, 1), spike.histogram());

        ScoreBreakdown only = accepted(oneSong(play(5, 10, 1, 1L), idle(5, 11)));
        assertEquals(List.of(), only.changesPerTransition());
        assertEquals(0, only.totalChanges());
        assertEquals(0, only.worstTransition());
        assertEquals(0, only.overload());
        assertTrue(only.format().contains("histogram: none"));
        assertFalse(only.format().contains("\ntransition: "));
    }

    @Test
    void identicalInputsProduceTheSameScoreBits() {
        OptimizationProblem problem = twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 2L), idle(6, 11));
        OptimizationRules firstRules = rules(
                1, 1, 1, 1, 2, 1,
                Map.of(10L, 1.5),
                List.of(),
                List.of(new PartPreference(6, 1, 11, 0.5)),
                Map.of(),
                Map.of());
        OptimizationRules secondRules = rules(
                1, 1, 1, 1, 2, 1,
                Map.of(10L, 1.5),
                List.of(),
                List.of(new PartPreference(6, 1, 11, 0.5)),
                Map.of(),
                Map.of());
        ScoreBreakdown first = accepted(problem, Assignment.fromBaseline(problem), firstRules);
        ScoreBreakdown second = accepted(problem, Assignment.fromBaseline(problem), secondRules);
        assertEquals(first, second);
        assertEquals(Double.doubleToLongBits(first.weightedScore()), Double.doubleToLongBits(second.weightedScore()));
        assertTrue(first.format().contains("weighted_score: " + Double.toString(first.weightedScore())));
        assertEquals(1, first.totalChanges());
        assertEquals(0.5, first.roleDeviation(), 0.0);
    }

    @Test
    void storedInstrumentIsNotASubstitute() {
        OptimizationProblem derived = twoSongs(
                new BaselineAssignment(5, 10, 1, null), idle(5, 11),
                new BaselineAssignment(6, 10, 1, null), idle(6, 11));
        assertEquals(1, accepted(derived).totalChanges());

        OptimizationProblem mismatch = oneSong(play(5, 10, 1, 2L), idle(5, 11));
        FeasibilityReport report = AssignmentFeasibility.check(mismatch, null);
        assertEquals(2, report.exitCode());
        assertTrue(report.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("assignment_instrument_mismatch")));
        ScoreOutcome.Rejected rejected = rejected(mismatch, Assignment.fromBaseline(mismatch), OptimizationRules.defaults());
        assertTrue(has(rejected, "assignment_instrument_mismatch"));
    }

    @Test
    void invalidAssignmentsAndRulesAreRejectedWithoutAScore() {
        assertTrue(has(rejected(oneSong(play(5, 10, 1, 1L), idle(5, 11), List.of(new Pin(5, 1, 11)))),
                "pin_conflict"));
        assertTrue(has(rejected(oneSong(
                play(5, 10, 1, 1L), idle(5, 11),
                List.of(new Pin(5, 1, 10), new Pin(5, 1, 11)))), "pin_conflict"));

        OptimizationProblem unowned = oneSong(play(5, 10, 1, 1L), idle(5, 11));
        OptimizationProblem ownedHarpOnly = problem(
                List.of(player(10, 2L), player(11, 2L)),
                unowned.occurrences(),
                unowned.baseline(),
                List.of());
        assertTrue(has(rejected(ownedHarpOnly), "unowned_instrument"));

        assertTrue(has(rejected(problem(
                List.of(player(10, 1L), player(11, 1L)),
                List.of(song(5, 100, new RequiredPart(1, null))),
                List.of(play(5, 10, 1, 1L), idle(5, 11)),
                List.of())), "unresolved_instrument"));
        assertTrue(has(rejected(problem(
                List.of(player(10, 1L), player(11, 1L)),
                List.of(song(5, 100, new RequiredPart(1, 999L))),
                List.of(new BaselineAssignment(5, 10, 1, 999L), idle(5, 11)),
                List.of())), "unknown_instrument"));
        assertTrue(has(rejected(problem(
                List.of(player(10, 1L), player(11, 1L)),
                List.of(song(5, 100, new RequiredPart(1, 1L))),
                List.of(play(5, 10, 1, 1L)),
                List.of())), "missing_baseline"));
        assertTrue(has(rejected(oneSong(idle(5, 10), idle(5, 11))), "uncovered_part"));
        assertTrue(has(rejected(oneSong(play(5, 10, 1, 1L), play(5, 11, 1, 1L))), "double_booked"));

        OptimizationProblem twoParts = problem(
                List.of(player(10, 1L, 2L), player(11, 1L, 2L)),
                List.of(song(5, 100, new RequiredPart(1, 1L), new RequiredPart(2, 2L))),
                List.of(play(5, 10, 1, 1L), play(5, 10, 2, 2L), idle(5, 11)),
                List.of());
        assertTrue(has(rejected(twoParts), "duplicate_player_assignment"));

        OptimizationProblem valid = oneSong(play(5, 10, 1, 1L), idle(5, 11));
        assertTrue(has(rejected(valid, Assignment.fromBaseline(valid), rules(
                1, 1, 1, 1, 2, 1,
                Map.of(99L, 1.0),
                List.of(),
                List.of(),
                Map.of(),
                Map.of())), "rule_unknown_player"));
        assertTrue(has(rejected(valid, Assignment.fromBaseline(valid), rules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(new InstrumentAffinity(10, 3L, 1), new InstrumentAffinity(10, 4L, 2)),
                List.of(),
                Map.of(),
                Map.of())), "rule_duplicate_affinity"));

        assertThrows(IllegalArgumentException.class, () -> rules(
                -1, 1, 1, 1, 2, 1, Map.of(), List.of(), List.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> rules(
                1, 1, 1, 1, -1, 1, Map.of(), List.of(), List.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new OptimizationRules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(new InstrumentAffinity(10, 1L, 1), new InstrumentAffinity(10, 1L, 2)),
                List.of(),
                Map.of(),
                Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new OptimizationRules(
                1, 1, 1, 1, 2, 1,
                Map.of(),
                List.of(),
                List.of(new PartPreference(5, 1, 10, 1), new PartPreference(5, 1, 11, 1)),
                Map.of(),
                Map.of()));
    }

    @Test
    void reportPrintsAnAcceptedBreakdownAndOmitsARejectedScore() {
        OptimizationProblem feasible = twoSongs(
                play(5, 10, 1, 1L), idle(5, 11),
                play(6, 10, 1, 2L), idle(6, 11));
        String accepted = ProblemReport.format(new LoadOutcome(
                1, null, null, null, feasible, AssignmentFeasibility.check(feasible, null)));
        assertTrue(accepted.contains("baseline: ok"));
        assertTrue(accepted.contains("changes: 1"));
        assertTrue(accepted.contains("weighted_score: 1.0"));
        assertTrue(accepted.contains("smooth_contribution: 0.0"));
        assertTrue(accepted.contains("total_contribution: 1.0"));

        OptimizationProblem roster = oneSong(play(5, 10, 1, 1L), idle(5, 11));
        String mismatch = ProblemReport.format(new LoadOutcome(
                1, null, null, 9, roster, AssignmentFeasibility.check(roster, 9)));
        assertTrue(mismatch.contains("players_mismatch"));
        assertTrue(mismatch.contains("weighted_score: 0.0"));

        OptimizationProblem infeasible = problem(
                List.of(player(10, 2L), player(11, 2L)),
                roster.occurrences(),
                roster.baseline(),
                List.of());
        String rejected = ProblemReport.format(new LoadOutcome(
                1, null, null, null, infeasible, AssignmentFeasibility.check(infeasible, null)));
        assertTrue(rejected.contains("unowned_instrument"));
        assertFalse(rejected.contains("weighted_score:"));
        assertFalse(rejected.contains("\nchanges: "));
    }

    private static ScoreBreakdown accepted(OptimizationProblem problem) {
        return accepted(problem, OptimizationRules.defaults());
    }

    private static ScoreBreakdown accepted(OptimizationProblem problem, OptimizationRules rules) {
        return accepted(problem, Assignment.fromBaseline(problem), rules);
    }

    private static ScoreBreakdown accepted(
            OptimizationProblem problem,
            Assignment assignment,
            OptimizationRules rules) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, assignment, rules);
        ScoreOutcome.Accepted accepted = assertInstanceOf(
                ScoreOutcome.Accepted.class,
                outcome,
                () -> String.valueOf(outcome));
        assertFaithful(accepted.breakdown());
        return accepted.breakdown();
    }

    private static ScoreOutcome.Rejected rejected(OptimizationProblem problem) {
        return rejected(problem, Assignment.fromBaseline(problem), OptimizationRules.defaults());
    }

    private static ScoreOutcome.Rejected rejected(
            OptimizationProblem problem,
            Assignment assignment,
            OptimizationRules rules) {
        ScoreOutcome outcome = AssignmentScorer.score(problem, assignment, rules);
        return assertInstanceOf(ScoreOutcome.Rejected.class, outcome);
    }

    private static void assertFaithful(ScoreBreakdown breakdown) {
        assertEquals(breakdown.smoothWeight() * breakdown.overload(), breakdown.smoothContribution(), 0.0);
        assertEquals(
                breakdown.totalWeight() * breakdown.weightedSwitchBurden(),
                breakdown.totalContribution(),
                0.0);
        assertEquals(breakdown.roleWeight() * breakdown.roleDeviation(), breakdown.roleContribution(), 0.0);
        assertEquals(breakdown.keepWeight() * breakdown.keepDeviation(), breakdown.keepContribution(), 0.0);
        assertEquals(
                breakdown.smoothContribution()
                        + breakdown.totalContribution()
                        + breakdown.roleContribution()
                        + breakdown.keepContribution(),
                breakdown.weightedScore(),
                0.0);
        int sum = 0;
        int worst = 0;
        for (int count : breakdown.changesPerTransition()) {
            sum += count;
            worst = Math.max(worst, count);
        }
        assertEquals(sum, breakdown.totalChanges());
        assertEquals(worst, breakdown.worstTransition());
        assertTrue(breakdown.format().contains("changes: " + breakdown.totalChanges()));
        assertTrue(breakdown.format().contains("weighted_score: " + Double.toString(breakdown.weightedScore())));
    }

    private static boolean has(ScoreOutcome.Rejected rejected, String code) {
        return rejected.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code));
    }

    private static OptimizationProblem twoSongs(
            BaselineAssignment firstPlayer,
            BaselineAssignment firstIdle,
            BaselineAssignment secondPlayer,
            BaselineAssignment secondIdle) {
        return twoSongs(firstPlayer, firstIdle, secondPlayer, secondIdle, 1L, 2L);
    }

    private static OptimizationProblem twoSongs(
            BaselineAssignment firstPlayer,
            BaselineAssignment firstIdle,
            BaselineAssignment secondPlayer,
            BaselineAssignment secondIdle,
            long firstInstrument,
            long secondInstrument) {
        return problem(
                List.of(player(10, 1L, 2L, 3L, 4L, 5L), player(11, 1L, 2L, 3L, 4L, 5L)),
                List.of(
                        song(5, 100, new RequiredPart(1, firstInstrument)),
                        song(6, 101, new RequiredPart(1, secondInstrument))),
                List.of(firstPlayer, firstIdle, secondPlayer, secondIdle),
                List.of());
    }

    private static OptimizationProblem oneSong(BaselineAssignment first, BaselineAssignment second) {
        return oneSong(first, second, List.of());
    }

    private static OptimizationProblem oneSong(
            BaselineAssignment first,
            BaselineAssignment second,
            List<Pin> pins) {
        return problem(
                List.of(player(10, 1L, 2L, 3L, 4L, 5L), player(11, 1L, 2L, 3L, 4L, 5L)),
                List.of(song(5, 100, new RequiredPart(1, 1L))),
                List.of(first, second),
                pins);
    }

    private static OptimizationProblem problem(
            List<Performer> performers,
            List<SongOccurrence> occurrences,
            List<BaselineAssignment> baseline,
            List<Pin> pins) {
        return new OptimizationProblem(
                1,
                false,
                1,
                performers,
                occurrences,
                baseline,
                pins,
                PhysicalInstrumentEquivalence.equivalentIds(NAMES));
    }

    private static OptimizationRules rules(
            double smooth,
            double total,
            double role,
            double keep,
            int target,
            double defaultSwitch,
            Map<Long, Double> switches,
            List<InstrumentAffinity> affinities,
            List<PartPreference> preferences,
            Map<Long, Double> itemKeep,
            Map<OccurrencePart, Double> partKeep) {
        return new OptimizationRules(
                smooth,
                total,
                role,
                keep,
                target,
                defaultSwitch,
                switches,
                affinities,
                preferences,
                itemKeep,
                partKeep);
    }

    private static SongOccurrence song(long item, long songId, RequiredPart... parts) {
        return new SongOccurrence(item, songId, 0, List.of(parts));
    }

    private static Performer player(long id, long... owned) {
        Set<Long> ids = new HashSet<>();
        for (long value : owned) {
            ids.add(value);
        }
        return new Performer(id, ids);
    }

    private static BaselineAssignment play(long item, long player, int part, long instrument) {
        return new BaselineAssignment(item, player, part, instrument);
    }

    private static BaselineAssignment idle(long item, long player) {
        return new BaselineAssignment(item, player, null, null);
    }

    private static AssignedSlot slot(long item, long player, Integer part, Long instrument) {
        return new AssignedSlot(item, player, part, instrument);
    }
}

package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AssignmentFeasibilityTest {

    private static final Map<Long, String> NAMES = Map.of(
            1L, "Basic Lute",
            2L, "Basic Harp",
            3L, "Traveler's Trusty Fiddle",
            4L, "Traveller's Trusty Fiddle");

    @Test
    void luteIsNotHarpAndTravelerCoversTraveller() {
        assertEquals(2, check(List.of(player(10, 2L)), part(1L), List.of(playing(10, 1, 1L)), List.of(), null)
                .exitCode());
        assertTrue(has(check(List.of(player(10, 2L)), part(1L), List.of(playing(10, 1, 1L)), List.of(), null),
                "unowned_instrument"));

        FeasibilityReport alias = check(
                List.of(player(10, 3L)), part(4L), List.of(playing(10, 1, 4L)), List.of(), null);
        assertEquals(0, alias.exitCode());
    }

    @Test
    void pinsConflictsAndRosterSizeAreHardFailures() {
        List<Performer> performers = List.of(player(10, 2L), player(11, 2L));
        List<BaselineAssignment> baseline = List.of(playing(10, 1, 2L), idle(11));

        FeasibilityReport wrongPlayer = check(
                performers, part(2L), baseline, List.of(new Pin(5, 1, 11)), null);
        assertEquals(2, wrongPlayer.exitCode());
        assertTrue(has(wrongPlayer, "pin_conflict"));

        FeasibilityReport bothPinned = check(
                performers,
                part(2L),
                baseline,
                List.of(new Pin(5, 1, 10), new Pin(5, 1, 11)),
                null);
        assertTrue(bothPinned.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("pin_conflict") && diagnostic.detail().contains("players=10,11")));

        FeasibilityReport missingPlayer = check(
                performers, part(2L), baseline, List.of(new Pin(5, 1, 99)), null);
        assertTrue(has(missingPlayer, "pin_unknown_player"));

        FeasibilityReport missingItem = check(
                performers, part(2L), baseline, List.of(new Pin(8, 1, 10)), null);
        assertTrue(has(missingItem, "pin_unknown_item"));

        FeasibilityReport roster = check(performers, part(2L), baseline, List.of(), 1);
        assertEquals(1, roster.exitCode());
        assertTrue(has(roster, "players_mismatch"));
    }

    @Test
    void duplicatePartsUnknownInstrumentsAndDoubleBookingAreReported() {
        OptimizationProblem duplicateParts = problem(
                List.of(player(10, 2L)),
                List.of(new SongOccurrence(5, 6, 0, List.of(new RequiredPart(1, 2L), new RequiredPart(1, 2L)))),
                List.of(playing(10, 1, 2L)),
                List.of());
        FeasibilityReport duplicate = AssignmentFeasibility.check(duplicateParts, null);
        assertEquals(1, duplicate.exitCode());
        assertTrue(has(duplicate, "duplicate_part_number"));
        assertTrue(duplicate.baselineFeasible());

        FeasibilityReport unknown = check(
                List.of(player(10, 2L)),
                new RequiredPart(1, 999L),
                List.of(playing(10, 1, 999L)),
                List.of(),
                null);
        assertEquals(1, unknown.exitCode());
        assertTrue(has(unknown, "unknown_instrument"));

        FeasibilityReport unresolved = check(
                List.of(player(10, 2L)),
                new RequiredPart(1, null),
                List.of(playing(10, 1, null)),
                List.of(),
                null);
        assertTrue(has(unresolved, "unresolved_instrument"));

        OptimizationProblem doubled = problem(
                List.of(player(10, 2L), player(11, 1L)),
                List.of(new SongOccurrence(
                        5, 6, 0, List.of(new RequiredPart(1, 2L), new RequiredPart(2, 1L)))),
                List.of(playing(10, 1, 2L), playing(11, 1, 2L)),
                List.of());
        FeasibilityReport doubleBooked = AssignmentFeasibility.check(doubled, null);
        assertEquals(2, doubleBooked.exitCode());
        assertTrue(has(doubleBooked, "double_booked"));
        assertTrue(has(doubleBooked, "uncovered_part"));
    }

    private static FeasibilityReport check(
            List<Performer> performers,
            RequiredPart required,
            List<BaselineAssignment> baseline,
            List<Pin> pins,
            Integer requestedPlayers) {
        return AssignmentFeasibility.check(
                problem(
                        performers,
                        List.of(new SongOccurrence(5, 6, 0, List.of(required))),
                        baseline,
                        pins),
                requestedPlayers);
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

    private static Performer player(long id, long... owned) {
        Set<Long> ids = new HashSet<>();
        for (long value : owned) {
            ids.add(value);
        }
        return new Performer(id, ids);
    }

    private static RequiredPart part(long instrumentId) {
        return new RequiredPart(1, instrumentId);
    }

    private static BaselineAssignment playing(long playerId, int partNumber, Long instrumentId) {
        return new BaselineAssignment(5, playerId, partNumber, instrumentId);
    }

    private static BaselineAssignment idle(long playerId) {
        return new BaselineAssignment(5, playerId, null, null);
    }

    private static boolean has(FeasibilityReport report, String code) {
        return report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code));
    }
}

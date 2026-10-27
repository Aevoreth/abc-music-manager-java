package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.optimizer.AssignmentOverride;
import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.optimizer.cli.OptimizerCli;
import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;
import com.aevoreth.abcmm.storage.SqliteOptimizationProfileRepository;

class SqliteOptimizationLoaderTest {

    @TempDir
    static Path tempDir;

    static Path database;
    static SyntheticLibrary library;

    @BeforeAll
    static void seed() throws Exception {
        database = tempDir.resolve("synthetic-setlist.sqlite");
        library = SyntheticLibrary.create(database);
        library.close();
    }

    @Test
    void repeatedSongKeepsDistinctOverridesAndIgnoresOutOfLayoutPlayers() {
        LoadOutcome outcome = load(library.feasibleId, null);
        OptimizationProblem problem = outcome.problem();
        assertNotNull(problem);
        assertTrue(problem.locked());
        assertEquals(2, problem.itemCount());
        assertEquals(4, problem.partCount());
        assertEquals(2, problem.instrumentCount());
        assertEquals(2, problem.playerCount());
        assertEquals(1, problem.transitionCount());
        assertEquals(0, outcome.report().exitCode());
        assertTrue(outcome.report().baselineFeasible());
        assertNotNull(outcome.revision());
        assertFalse(outcome.revision().overrides().isEmpty());

        SongOccurrence first = problem.occurrences().get(0);
        SongOccurrence second = problem.occurrences().get(1);
        assertEquals(first.songId(), second.songId());
        assertEquals(1, problem.baseline(first.setlistItemId(), library.alice).partNumber());
        assertEquals(library.fiddle, problem.effectiveInstrumentId(first.setlistItemId(), library.alice));
        assertEquals(2, problem.baseline(second.setlistItemId(), library.alice).partNumber());
        assertEquals(library.harp, problem.effectiveInstrumentId(second.setlistItemId(), library.alice));
        assertEquals(library.fiddle, problem.effectiveInstrumentId(second.setlistItemId(), library.bob));
        assertNull(problem.baseline(first.setlistItemId(), library.cara));
        assertTrue(has(outcome, DiagnosticSeverity.INFO, "out_of_layout_assignment"));
        assertTrue(owned(problem, library.alice).contains(library.fiddle));
        assertFalse(outcome.problem().toString().contains("Alice"));
    }

    @Test
    void explicitNullOverrideLeavesThePlayerIdle() {
        LoadOutcome outcome = load(library.nullOverrideId, null);
        OptimizationProblem problem = outcome.problem();
        long itemId = problem.occurrences().get(0).setlistItemId();
        assertNull(problem.baseline(itemId, library.alice).partNumber());
        assertNotNull(outcome.revision());
        assertTrue(outcome.revision().overrides().contains(
                new AssignmentOverride(itemId, library.alice, null)));
        assertNull(problem.effectiveInstrumentId(itemId, library.alice));
        assertEquals(2, problem.baseline(itemId, library.bob).partNumber());
        assertEquals(2, outcome.report().exitCode());
        assertTrue(has(outcome, DiagnosticSeverity.BASELINE, "uncovered_part"));
    }

    @Test
    void travelerOwnershipCoversTravellerDemand() {
        LoadOutcome outcome = load(library.aliasId, null);
        OptimizationProblem problem = outcome.problem();
        long itemId = problem.occurrences().get(0).setlistItemId();
        assertEquals(library.traveller, problem.effectiveInstrumentId(itemId, library.alice));
        assertTrue(owned(problem, library.alice).contains(library.traveler));
        assertFalse(owned(problem, library.alice).contains(library.traveller));
        assertEquals(0, outcome.report().exitCode());
    }

    @Test
    void proficiencyWithoutPossessionIsNotEligibility() {
        LoadOutcome outcome = load(library.unownedId, null);
        assertFalse(owned(outcome.problem(), library.bob).contains(library.lute));
        assertEquals(2, outcome.report().exitCode());
        assertTrue(has(outcome, DiagnosticSeverity.BASELINE, "unowned_instrument"));
    }

    @Test
    void doubleBookingAndUnknownOrDuplicatePartsFailOpen() {
        LoadOutcome doubled = load(library.doubleBookedId, null);
        assertEquals(2, doubled.report().exitCode());
        assertTrue(has(doubled, DiagnosticSeverity.BASELINE, "double_booked"));
        assertTrue(has(doubled, DiagnosticSeverity.BASELINE, "uncovered_part"));

        LoadOutcome unknown = load(library.unknownInstrumentId, null);
        assertNotNull(unknown.problem());
        assertEquals(1, unknown.problem().itemCount());
        assertEquals(1, unknown.report().exitCode());
        assertTrue(has(unknown, DiagnosticSeverity.INTEGRITY, "unknown_instrument"));

        LoadOutcome duplicate = load(library.duplicatePartId, null);
        assertEquals(2, duplicate.problem().partCount());
        assertEquals(1, duplicate.report().exitCode());
        assertTrue(has(duplicate, DiagnosticSeverity.INTEGRITY, "duplicate_part_number"));

        LoadOutcome mismatch = load(library.partCountMismatchId, null);
        assertEquals(1, mismatch.problem().partCount());
        assertTrue(has(mismatch, DiagnosticSeverity.INTEGRITY, "part_count_mismatch"));

        LoadOutcome rows = load(library.duplicateRowId, null);
        assertEquals(1, rows.report().exitCode());
        assertTrue(has(rows, DiagnosticSeverity.INTEGRITY, "duplicate_assignment_row"));
    }

    @Test
    void madeForResolvesAndBrokenInputKeepsTheItem() {
        LoadOutcome madeFor = load(library.madeForId, null);
        long itemId = madeFor.problem().occurrences().get(0).setlistItemId();
        assertEquals(library.lute, madeFor.problem().effectiveInstrumentId(itemId, library.alice));
        assertEquals(0, madeFor.report().exitCode());

        LoadOutcome unparsable = load(library.unparsableId, null);
        assertEquals(1, unparsable.problem().itemCount());
        assertEquals(0, unparsable.problem().partCount());
        assertEquals(1, unparsable.report().exitCode());
        assertTrue(has(unparsable, DiagnosticSeverity.INTEGRITY, "unparsable_parts"));
    }

    @Test
    void missingLayoutSetlistOrFileDoesNotInventAProblem() {
        LoadOutcome missingLayout = load(library.missingLayoutId, null);
        assertNull(missingLayout.problem());
        assertEquals(1, missingLayout.report().exitCode());
        assertTrue(has(missingLayout, DiagnosticSeverity.INTEGRITY, "missing_band_layout"));

        LoadOutcome missingSetlist = load(9_999_999L, null);
        assertNull(missingSetlist.problem());
        assertNull(missingSetlist.revision());
        assertTrue(has(missingSetlist, DiagnosticSeverity.INTEGRITY, "setlist_not_found"));

        LoadOutcome missingFile = new SqliteOptimizationLoader().load(new LoadRequest(
                tempDir.resolve("absent.sqlite"), 1, null, null, null));
        assertNull(missingFile.problem());
        assertNull(missingFile.revision());
        assertTrue(has(missingFile, DiagnosticSeverity.INTEGRITY, "missing_database"));

        LoadOutcome roster = load(library.feasibleId, 9);
        assertNotNull(roster.problem());
        assertEquals(1, roster.report().exitCode());
        assertTrue(has(roster, DiagnosticSeverity.INTEGRITY, "players_mismatch"));
    }

    @Test
    void sameSongCanCarryDifferentPinsAndSavedRules() throws Exception {
        Path pinned = tempDir.resolve("pinned-repeat.sqlite");
        long setlistId;
        long firstId;
        long secondId;
        long alice;
        long bob;
        try (SyntheticLibrary library = SyntheticLibrary.create(pinned)) {
            SqliteOptimizationProfileRepository profiles =
                    new SqliteOptimizationProfileRepository(library.database);
            long bandId = library.bands.listBands().get(0).id();
            profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                    1,
                    9.0d,
                    1.0d,
                    3.0d,
                    1.0d,
                    2,
                    1.0d,
                    List.of(new PlayerSwitchWeight(library.alice, 4.0d)),
                    List.of(new InstrumentAffinityWeight(library.alice, library.fiddle, 2.5d)),
                    SearchBudgetPreset.QUICK));
            setlistId = library.setlists.addSetlist("Repeat", null);
            library.setlists.updateSetlist(
                    setlistId, "Repeat", library.layoutId, null, 0, false,
                    null, null, null, null, null, null);
            firstId = library.setlists.addItem(setlistId, library.tuneId, 0, null, library.tuneLayoutId);
            secondId = library.setlists.addItem(setlistId, library.tuneId, 1, null, library.tuneLayoutId);
            profiles.replaceOccurrencePreferences(setlistId, new OccurrencePreferences(
                    List.of(new PartPreferenceWeight(firstId, 1, library.alice, 6.0d)),
                    List.of(),
                    List.of()));
            profiles.replacePins(setlistId, List.of(
                    new AssignmentPin(firstId, 1, library.alice),
                    new AssignmentPin(secondId, 1, library.bob)));
            alice = library.alice;
            bob = library.bob;
        }

        LoadOutcome outcome = new SqliteOptimizationLoader().load(
                new LoadRequest(pinned, setlistId, 1L, null, null));
        OptimizationProblem problem = outcome.problem();
        assertNotNull(problem);
        assertEquals(problem.occurrences().get(0).songId(), problem.occurrences().get(1).songId());
        assertEquals(List.of(new Pin(firstId, 1, alice), new Pin(secondId, 1, bob)), problem.pins());
        assertEquals(9.0d, outcome.rules().smoothWeight());
        assertEquals(3.0d, outcome.rules().roleWeight());
        assertEquals(4.0d, outcome.rules().playerSwitchMultiplier().get(alice));
        assertEquals(2.5d, outcome.rules().instrumentAffinities().get(0).missPenalty());
        assertEquals(6.0d, outcome.rules().partPreferences().get(0).penalty());
        assertEquals(SearchBudgetPreset.QUICK, outcome.searchBudget());
        assertNull(outcome.budgetSeconds());
        assertEquals(Duration.ofSeconds(2), OptimizerCli.searchBudget(outcome));
    }

    private static LoadOutcome load(long setlistId, Integer players) {
        return new SqliteOptimizationLoader().load(
                new LoadRequest(database, setlistId, 42L, 10L, players));
    }

    private static java.util.Set<Long> owned(OptimizationProblem problem, long playerId) {
        Optional<Performer> performer = problem.performers().stream()
                .filter(candidate -> candidate.playerId() == playerId)
                .findFirst();
        assertTrue(performer.isPresent());
        return performer.orElseThrow().ownedInstrumentIds();
    }

    private static boolean has(LoadOutcome outcome, DiagnosticSeverity severity, String code) {
        return outcome.report().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == severity && diagnostic.code().equals(code));
    }
}

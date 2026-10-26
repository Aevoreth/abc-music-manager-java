package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.optimizer.load.SqliteOptimizationLoader;

class ReferenceBaselineScoreTest {

    @Test
    void privateReferenceBaselineUsesAdjacentSongCounts() {
        String configured = System.getProperty("abcmm.referenceDatabase");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("ABCMM_REFERENCE_DB");
        }
        assumeTrue(configured != null && !configured.isBlank(), "reference database not configured");
        Path database = Path.of(configured);
        assertTrue(Files.isRegularFile(database), "reference database not found");

        LoadOutcome outcome = new SqliteOptimizationLoader().load(new LoadRequest(database, 24, null, null, null));
        assertEquals(0, outcome.report().exitCode());
        ScoreOutcome scored = AssignmentScorer.score(
                outcome.problem(),
                Assignment.fromBaseline(outcome.problem()),
                OptimizationRules.defaults());
        ScoreOutcome.Accepted accepted = assertInstanceOf(ScoreOutcome.Accepted.class, scored);
        ScoreBreakdown breakdown = accepted.breakdown();
        assertEquals(34, outcome.problem().itemCount());
        assertEquals(33, outcome.problem().transitionCount());
        assertEquals(33, breakdown.changesPerTransition().size());
        assertEquals(148, breakdown.totalChanges());
        assertEquals(6, breakdown.worstTransition());
        assertEquals(Map.of(2, 2, 3, 4, 4, 9, 5, 12, 6, 6), breakdown.histogram());
    }
}

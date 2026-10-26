package com.aevoreth.abcmm.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.ItemSources;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.PlayerPart;

class EffectiveAssignmentResolverTest {

    @Test
    void nullOverrideBeatsLayoutAndRepeatedItemsStayIndependent() {
        ItemSources first = new ItemSources(
                10,
                List.of(),
                List.of(new PlayerPart(1, 1), new PlayerPart(2, 2)));
        ItemSources second = new ItemSources(
                11,
                List.of(new PlayerPart(1, null), new PlayerPart(2, 1)),
                List.of(new PlayerPart(1, 1), new PlayerPart(2, 2)));
        Map<Long, Map<Integer, Long>> instruments = Map.of(
                10L, Map.of(1, 100L, 2, 200L),
                11L, Map.of(1, 100L, 2, 200L));

        EffectiveAssignmentResolver.Result result = EffectiveAssignmentResolver.resolve(
                List.of(1L, 2L), List.of(first, second), instruments);

        BaselineAssignment firstAlice = assignment(result, 10, 1);
        assertEquals(1, firstAlice.partNumber());
        assertEquals(100L, firstAlice.instrumentId());
        BaselineAssignment secondAlice = assignment(result, 11, 1);
        assertNull(secondAlice.partNumber());
        assertNull(secondAlice.instrumentId());
        assertEquals(1, assignment(result, 11, 2).partNumber());
        assertEquals(100L, assignment(result, 11, 2).instrumentId());
    }

    @Test
    void outOfLayoutRowsAreIgnoredAndDuplicateRowsAreIntegrityErrors() {
        ItemSources item = new ItemSources(
                10,
                List.of(new PlayerPart(1, 1), new PlayerPart(1, 2), new PlayerPart(9, 1)),
                List.of(new PlayerPart(8, 2)));
        EffectiveAssignmentResolver.Result result = EffectiveAssignmentResolver.resolve(
                List.of(1L),
                List.of(item),
                Map.of(10L, Map.of(1, 100L, 2, 200L)));

        assertEquals(1, result.assignments().size());
        assertEquals(1, result.assignments().get(0).partNumber());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == DiagnosticSeverity.INFO
                        && diagnostic.code().equals("out_of_layout_assignment")
                        && diagnostic.detail().contains("player=9")
                        && diagnostic.detail().contains("source=override")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("out_of_layout_assignment")
                        && diagnostic.detail().contains("player=8")
                        && diagnostic.detail().contains("source=layout")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == DiagnosticSeverity.INTEGRITY
                        && diagnostic.code().equals("duplicate_assignment_row")
                        && diagnostic.detail().contains("player=1")));
    }

    private static BaselineAssignment assignment(
            EffectiveAssignmentResolver.Result result,
            long itemId,
            long playerId) {
        return result.assignments().stream()
                .filter(row -> row.setlistItemId() == itemId && row.playerId() == playerId)
                .findFirst()
                .orElseThrow();
    }
}

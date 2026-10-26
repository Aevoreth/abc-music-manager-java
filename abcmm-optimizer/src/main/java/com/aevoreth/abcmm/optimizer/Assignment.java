package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidate part assignment for one setlist. Slots are copied and are not
 * tied to later edits of the source list.
 */
public record Assignment(List<AssignedSlot> slots) {

    public Assignment {
        slots = List.copyOf(slots);
    }

    public static Assignment fromBaseline(OptimizationProblem problem) {
        List<AssignedSlot> copied = new ArrayList<>();
        for (BaselineAssignment row : problem.baseline()) {
            copied.add(new AssignedSlot(
                    row.setlistItemId(), row.playerId(), row.partNumber(), row.instrumentId()));
        }
        return new Assignment(copied);
    }

    /** First slot for this item and player, or null when that row is absent. */
    public AssignedSlot slot(long setlistItemId, long playerId) {
        for (AssignedSlot slot : slots) {
            if (slot.setlistItemId() == setlistItemId && slot.playerId() == playerId) {
                return slot;
            }
        }
        return null;
    }
}

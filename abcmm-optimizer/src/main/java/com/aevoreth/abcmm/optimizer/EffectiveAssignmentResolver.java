package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the effective part for each layout player on each setlist item.
 * An override row wins even when its part number is null. Players who are not on the
 * layout are reported and ignored.
 */
public final class EffectiveAssignmentResolver {

    private EffectiveAssignmentResolver() {
    }

    public record PlayerPart(long playerId, Integer partNumber) {
    }

    public record ItemSources(
            long setlistItemId,
            List<PlayerPart> overrides,
            List<PlayerPart> layoutAssignments) {
    }

    public record Result(List<BaselineAssignment> assignments, List<Diagnostic> diagnostics) {
    }

    public static Result resolve(
            List<Long> layoutPlayerIds,
            List<ItemSources> items,
            Map<Long, Map<Integer, Long>> instrumentIdByItemAndPart) {
        Set<Long> layoutPlayers = new LinkedHashSet<>(layoutPlayerIds);
        List<BaselineAssignment> assignments = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ItemSources item : items) {
            Map<Long, Integer> overrides = indexRows(
                    item.setlistItemId(), "override", item.overrides(), layoutPlayers, diagnostics);
            Map<Long, Integer> layout = indexRows(
                    item.setlistItemId(), "layout", item.layoutAssignments(), layoutPlayers, diagnostics);
            Map<Integer, Long> instruments = instrumentIdByItemAndPart.getOrDefault(
                    item.setlistItemId(), Map.of());
            for (long playerId : layoutPlayerIds) {
                Integer part;
                if (overrides.containsKey(playerId)) {
                    part = overrides.get(playerId);
                } else if (layout.containsKey(playerId)) {
                    part = layout.get(playerId);
                } else {
                    part = null;
                }
                Long instrument = part == null ? null : instruments.get(part);
                assignments.add(new BaselineAssignment(item.setlistItemId(), playerId, part, instrument));
            }
        }
        return new Result(List.copyOf(assignments), List.copyOf(diagnostics));
    }

    /**
     * @return player to part for players on the layout. Null part values are preserved.
     *         The map's {@code containsKey} distinguishes an explicit null from a missing row.
     */
    private static Map<Long, Integer> indexRows(
            long setlistItemId,
            String source,
            List<PlayerPart> rows,
            Set<Long> layoutPlayers,
            List<Diagnostic> diagnostics) {
        Map<Long, Integer> indexed = new LinkedHashMap<>();
        Set<Long> ignored = new LinkedHashSet<>();
        if (rows == null) {
            return indexed;
        }
        for (PlayerPart row : rows) {
            if (!layoutPlayers.contains(row.playerId())) {
                if (ignored.add(row.playerId())) {
                    diagnostics.add(Diagnostic.info(
                            "out_of_layout_assignment",
                            Diagnostics.detail(
                                    "item", setlistItemId,
                                    "player", row.playerId(),
                                    "source", source)));
                } else {
                    diagnostics.add(duplicateRow(setlistItemId, row.playerId(), source));
                }
                continue;
            }
            if (indexed.containsKey(row.playerId())) {
                diagnostics.add(duplicateRow(setlistItemId, row.playerId(), source));
                continue;
            }
            indexed.put(row.playerId(), row.partNumber());
        }
        return indexed;
    }

    private static Diagnostic duplicateRow(long setlistItemId, long playerId, String source) {
        return Diagnostic.integrity(
                "duplicate_assignment_row",
                Diagnostics.detail(
                        "item", setlistItemId,
                        "player", playerId,
                        "source", source));
    }
}

package com.aevoreth.abcmm.domain.export;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;

/**
 * Resolve band-layout player order for CSV export columns.
 */
public final class LayoutExportOrder {

    private LayoutExportOrder() {
    }

    /**
     * Order slots by saved {@code export_column_order} player ids when present;
     * drop missing players; append remaining slots in their existing (row-major) order.
     */
    public static List<BandLayoutSlotInfo> listSlotsForExport(
            List<BandLayoutSlotInfo> slots,
            List<Long> savedPlayerOrder) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        Map<Long, BandLayoutSlotInfo> byPlayer = new LinkedHashMap<>();
        for (BandLayoutSlotInfo slot : slots) {
            byPlayer.put(slot.playerId(), slot);
        }
        List<BandLayoutSlotInfo> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (savedPlayerOrder != null) {
            for (Long pid : savedPlayerOrder) {
                if (pid == null) {
                    continue;
                }
                BandLayoutSlotInfo slot = byPlayer.get(pid);
                if (slot != null && seen.add(pid)) {
                    result.add(slot);
                }
            }
        }
        for (BandLayoutSlotInfo slot : slots) {
            if (seen.add(slot.playerId())) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Parse {@code BandLayout.export_column_order} JSON array of player ids.
     */
    public static List<Long> parseExportColumnOrderJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        String inner = trimmed.substring(1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
        for (String token : inner.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return List.copyOf(ids);
    }

    public static String toExportColumnOrderJson(List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < playerIds.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(playerIds.get(i));
        }
        sb.append(']');
        return sb.toString();
    }
}

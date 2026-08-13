package com.aevoreth.abcmm.domain.setplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CSV-style parts matrix for Set Play tab 3 / {@code set_play_state_v2}.
 */
public record SetPlayPartsSheet(
        List<Column> columns,
        List<Row> rows,
        List<InstrumentsNeeded> instrumentsNeeded) {

    public record Column(String key, String title, Long playerId) {
    }

    public record Row(long itemId, List<String> cells) {
    }

    public record InstrumentsNeeded(long playerId, String playerName, List<String> instruments) {
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> cols = new ArrayList<>();
        for (Column c : columns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.key());
            m.put("title", c.title());
            m.put("player_id", c.playerId());
            cols.add(m);
        }
        out.put("columns", cols);
        List<Map<String, Object>> rowPayloads = new ArrayList<>();
        for (Row r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item_id", r.itemId());
            m.put("cells", new ArrayList<>(r.cells()));
            rowPayloads.add(m);
        }
        out.put("rows", rowPayloads);
        List<Map<String, Object>> needed = new ArrayList<>();
        for (InstrumentsNeeded n : instrumentsNeeded) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player_id", n.playerId());
            m.put("player_name", n.playerName());
            m.put("instruments", new ArrayList<>(n.instruments()));
            needed.add(m);
        }
        out.put("instruments_needed", needed);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static SetPlayPartsSheet fromPayload(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return empty();
        }
        List<Column> columns = new ArrayList<>();
        if (map.get("columns") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    columns.add(new Column(
                            str(m.get("key"), ""),
                            str(m.get("title"), ""),
                            toLongOrNull(m.get("player_id"))));
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        if (map.get("rows") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    long itemId = toLong(m.get("item_id"), 0L);
                    List<String> cells = new ArrayList<>();
                    if (m.get("cells") instanceof List<?> cellList) {
                        for (Object c : cellList) {
                            cells.add(c == null ? "" : String.valueOf(c));
                        }
                    }
                    rows.add(new Row(itemId, cells));
                }
            }
        }
        List<InstrumentsNeeded> needed = new ArrayList<>();
        if (map.get("instruments_needed") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    List<String> inst = new ArrayList<>();
                    if (m.get("instruments") instanceof List<?> names) {
                        for (Object n : names) {
                            inst.add(n == null ? "" : String.valueOf(n));
                        }
                    }
                    needed.add(new InstrumentsNeeded(
                            toLong(m.get("player_id"), 0L),
                            str(m.get("player_name"), ""),
                            inst));
                }
            }
        }
        return new SetPlayPartsSheet(List.copyOf(columns), List.copyOf(rows), List.copyOf(needed));
    }

    public static SetPlayPartsSheet empty() {
        return new SetPlayPartsSheet(List.of(), List.of(), List.of());
    }

    public Set<String> uniqueInstruments(long playerId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (InstrumentsNeeded n : instrumentsNeeded) {
            if (n.playerId() == playerId) {
                out.addAll(n.instruments());
            }
        }
        return out;
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long toLong(Object value, long fallback) {
        Long n = toLongOrNull(value);
        return n == null ? fallback : n;
    }

    public static SetPlayPartsSheet of(
            List<Column> columns, List<Row> rows, List<InstrumentsNeeded> needed) {
        return new SetPlayPartsSheet(
                List.copyOf(Objects.requireNonNull(columns)),
                List.copyOf(Objects.requireNonNull(rows)),
                List.copyOf(Objects.requireNonNull(needed)));
    }
}

package com.aevoreth.abcmm.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.AssignmentOverride;
import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.OptimizationApplyResult;
import com.aevoreth.abcmm.domain.optimizer.OptimizationJournal;
import com.aevoreth.abcmm.domain.optimizer.OptimizationRefusal;
import com.aevoreth.abcmm.domain.optimizer.OptimizationRevision;
import com.aevoreth.abcmm.domain.optimizer.OptimizationStructureItem;
import com.aevoreth.abcmm.domain.optimizer.OptimizationUndoResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * SQLite {@link OptimizationJournal}. Apply and undo each run in one transaction
 * on the shared connection. Does not close the database.
 */
public final class SqliteOptimizationJournal implements OptimizationJournal {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String APPLIED = "APPLIED";
    private static final String UNDONE = "UNDONE";

    private final SqliteDatabase database;

    public SqliteOptimizationJournal(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public OptimizationRevision revision(long setlistId) throws LibraryException {
        try {
            OptimizationRevision revision = readRevision(setlistId);
            if (revision == null) {
                throw new LibraryException("Setlist not found");
            }
            return revision;
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read optimization revision", ex);
        }
    }

    @Override
    public OptimizationApplyResult apply(
            long setlistId,
            OptimizationRevision expected,
            List<AssignmentOverride> desiredOverrides,
            String rulesJson) throws LibraryException {
        if (database.isReadOnly()) {
            return OptimizationApplyResult.refused(
                    List.of(OptimizationRefusal.READ_ONLY), explain(List.of(OptimizationRefusal.READ_ONLY)));
        }
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(desiredOverrides, "desiredOverrides");
        Objects.requireNonNull(rulesJson, "rulesJson");
        OptimizationApplyResult[] result = new OptimizationApplyResult[1];
        inTransaction(() -> result[0] = applyInTransaction(setlistId, expected, desiredOverrides, rulesJson));
        return result[0];
    }

    @Override
    public OptimizationUndoResult undoLatest(long setlistId) throws LibraryException {
        if (database.isReadOnly()) {
            return OptimizationUndoResult.refused(
                    List.of(OptimizationRefusal.READ_ONLY), explain(List.of(OptimizationRefusal.READ_ONLY)));
        }
        OptimizationUndoResult[] result = new OptimizationUndoResult[1];
        inTransaction(() -> result[0] = undoInTransaction(setlistId));
        return result[0];
    }

    /**
     * Drops expired history for one setlist. Used by tests to check retention
     * without applying another optimization. Apply already prunes in its transaction.
     */
    void pruneHistory(long setlistId) throws LibraryException {
        inTransaction(() -> pruneRows(setlistId));
    }

    private OptimizationApplyResult applyInTransaction(
            long setlistId,
            OptimizationRevision expected,
            List<AssignmentOverride> desiredOverrides,
            String rulesJson) throws SQLException, LibraryException {
        OptimizationRevision current = readRevision(setlistId);
        if (current == null) {
            return OptimizationApplyResult.refused(
                    List.of(OptimizationRefusal.NOT_FOUND), explain(List.of(OptimizationRefusal.NOT_FOUND)));
        }
        if (isLocked(setlistId)) {
            return OptimizationApplyResult.refused(
                    List.of(OptimizationRefusal.LOCKED), explain(List.of(OptimizationRefusal.LOCKED)));
        }
        if (!current.equals(expected)) {
            List<OptimizationRefusal> reasons = new ArrayList<>();
            reasons.add(OptimizationRefusal.STALE_REVISION);
            reasons.addAll(differences(expected, current));
            return OptimizationApplyResult.refused(reasons, explain(reasons));
        }
        List<AssignmentOverride> desired = new OptimizationRevision(
                current.bandLayoutId(), current.items(), current.pins(), desiredOverrides).overrides();
        Set<Long> itemIds = new HashSet<>();
        for (OptimizationStructureItem item : current.items()) {
            itemIds.add(item.id());
        }
        for (AssignmentOverride row : desired) {
            if (!itemIds.contains(row.setlistItemId())) {
                throw new LibraryException("Override row is not on this setlist");
            }
        }
        replaceOverrides(setlistId, desired);
        long operationId = insertHistory(
                setlistId,
                rulesJson,
                writeStructure(current),
                writeOverrides(current.overrides()),
                writeOverrides(desired));
        pruneRows(setlistId);
        return OptimizationApplyResult.applied(operationId);
    }

    private OptimizationUndoResult undoInTransaction(long setlistId) throws SQLException, LibraryException {
        if (readRevision(setlistId) == null) {
            return OptimizationUndoResult.refused(
                    List.of(OptimizationRefusal.NOT_FOUND), explain(List.of(OptimizationRefusal.NOT_FOUND)));
        }
        if (isLocked(setlistId)) {
            return OptimizationUndoResult.refused(
                    List.of(OptimizationRefusal.LOCKED), explain(List.of(OptimizationRefusal.LOCKED)));
        }
        HistoryRow history = newestApplied(setlistId);
        if (history == null) {
            return OptimizationUndoResult.refused(
                    List.of(OptimizationRefusal.NOTHING_TO_UNDO),
                    explain(List.of(OptimizationRefusal.NOTHING_TO_UNDO)));
        }
        OptimizationRevision current = readRevision(setlistId);
        OptimizationRevision saved = revisionFromJson(history.structureJson(), history.afterOverridesJson());
        if (!current.equals(saved)) {
            List<OptimizationRefusal> reasons = differences(saved, current);
            if (reasons.isEmpty()) {
                reasons = List.of(OptimizationRefusal.CONFLICT_ASSIGNMENTS);
            }
            return OptimizationUndoResult.refused(reasons, explain(reasons));
        }
        replaceOverrides(setlistId, readOverrides(history.beforeOverridesJson()));
        markUndone(history.id());
        return OptimizationUndoResult.undone(history.id());
    }

    private OptimizationRevision readRevision(long setlistId) throws SQLException, LibraryException {
        SetlistHead head = readHead(setlistId);
        if (head == null) {
            return null;
        }
        return new OptimizationRevision(head.bandLayoutId(), readItems(setlistId), readPins(setlistId), readOverrides(setlistId));
    }

    private boolean isLocked(long setlistId) throws SQLException {
        SetlistHead head = readHead(setlistId);
        return head != null && head.locked();
    }

    private SetlistHead readHead(long setlistId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT locked, band_layout_id FROM Setlist WHERE id = ?")) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Long bandLayoutId = rs.getObject("band_layout_id") == null
                        ? null
                        : rs.getLong("band_layout_id");
                return new SetlistHead(rs.getInt("locked") != 0, bandLayoutId);
            }
        }
    }

    private List<OptimizationStructureItem> readItems(long setlistId) throws SQLException {
        String sql = """
                SELECT si.id, si.position, si.song_id, si.song_layout_id, s.parts
                FROM SetlistItem si
                JOIN Song s ON s.id = si.song_id
                WHERE si.setlist_id = ?
                ORDER BY si.position, si.id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<OptimizationStructureItem> items = new ArrayList<>();
                while (rs.next()) {
                    Long songLayoutId = rs.getObject("song_layout_id") == null
                            ? null
                            : rs.getLong("song_layout_id");
                    items.add(new OptimizationStructureItem(
                            rs.getLong("id"),
                            rs.getInt("position"),
                            rs.getLong("song_id"),
                            songLayoutId,
                            rs.getString("parts")));
                }
                return items;
            }
        }
    }

    private List<AssignmentPin> readPins(long setlistId) throws SQLException {
        String sql = """
                SELECT p.setlist_item_id, p.part_number, p.player_id
                FROM SetlistAssignmentPin p
                JOIN SetlistItem si ON si.id = p.setlist_item_id
                WHERE si.setlist_id = ?
                ORDER BY p.setlist_item_id, p.part_number, p.player_id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<AssignmentPin> pins = new ArrayList<>();
                while (rs.next()) {
                    pins.add(new AssignmentPin(
                            rs.getLong("setlist_item_id"),
                            rs.getInt("part_number"),
                            rs.getLong("player_id")));
                }
                return pins;
            }
        }
    }

    private List<AssignmentOverride> readOverrides(long setlistId) throws SQLException, LibraryException {
        String sql = """
                SELECT a.setlist_item_id, a.player_id, a.part_number
                FROM SetlistBandAssignment a
                JOIN SetlistItem si ON si.id = a.setlist_item_id
                WHERE si.setlist_id = ?
                ORDER BY a.setlist_item_id, a.player_id, a.id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<AssignmentOverride> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(overrideFrom(rs.getLong("setlist_item_id"), rs.getLong("player_id"), rs.getObject("part_number")));
                }
                return rows;
            }
        }
    }

    private static AssignmentOverride overrideFrom(long setlistItemId, long playerId, Object partNumber)
            throws LibraryException {
        Integer part = null;
        if (partNumber != null) {
            int value = partNumber instanceof Number number ? number.intValue() : Integer.parseInt(partNumber.toString());
            if (value <= 0) {
                throw new LibraryException("Stored assignment part is invalid");
            }
            part = value;
        }
        return new AssignmentOverride(setlistItemId, playerId, part);
    }

    private void replaceOverrides(long setlistId, List<AssignmentOverride> rows) throws SQLException {
        try (PreparedStatement delete = database.connection().prepareStatement(
                """
                DELETE FROM SetlistBandAssignment
                WHERE setlist_item_id IN (SELECT id FROM SetlistItem WHERE setlist_id = ?)
                """)) {
            delete.setLong(1, setlistId);
            delete.executeUpdate();
        }
        String now = SqliteTimestamps.now();
        try (PreparedStatement insert = database.connection().prepareStatement(
                """
                INSERT INTO SetlistBandAssignment
                    (setlist_item_id, player_id, part_number, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (AssignmentOverride row : rows) {
                insert.setLong(1, row.setlistItemId());
                insert.setLong(2, row.playerId());
                if (row.partNumber() == null) {
                    insert.setNull(3, Types.INTEGER);
                } else {
                    insert.setInt(3, row.partNumber());
                }
                insert.setString(4, now);
                insert.setString(5, now);
                insert.executeUpdate();
            }
        }
    }

    private long insertHistory(
            long setlistId,
            String rulesJson,
            String structureJson,
            String beforeJson,
            String afterJson) throws SQLException {
        String sql = """
                INSERT INTO SetlistOptimizationHistory
                    (setlist_id, created_at, status, rules_json, structure_json,
                     before_overrides_json, after_overrides_json, undone_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                """;
        try (PreparedStatement insert = database.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, setlistId);
            insert.setString(2, SqliteTimestamps.now());
            insert.setString(3, APPLIED);
            insert.setString(4, rulesJson);
            insert.setString(5, structureJson);
            insert.setString(6, beforeJson);
            insert.setString(7, afterJson);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned");
                }
                return keys.getLong(1);
            }
        }
    }

    private HistoryRow newestApplied(long setlistId) throws SQLException {
        String sql = """
                SELECT id, structure_json, before_overrides_json, after_overrides_json
                FROM SetlistOptimizationHistory
                WHERE setlist_id = ? AND status = ?
                ORDER BY id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            statement.setString(2, APPLIED);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new HistoryRow(
                        rs.getLong("id"),
                        rs.getString("structure_json"),
                        rs.getString("before_overrides_json"),
                        rs.getString("after_overrides_json"));
            }
        }
    }

    private void markUndone(long operationId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                UPDATE SetlistOptimizationHistory
                SET status = ?, undone_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, UNDONE);
            statement.setString(2, SqliteTimestamps.now());
            statement.setLong(3, operationId);
            statement.executeUpdate();
        }
    }

    private void pruneRows(long setlistId) throws SQLException {
        String sql = """
                SELECT id, status, created_at
                FROM SetlistOptimizationHistory
                WHERE setlist_id = ?
                ORDER BY id DESC
                """;
        List<HistoryMeta> rows = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HistoryMeta(
                            rs.getLong("id"),
                            rs.getString("status"),
                            rs.getString("created_at")));
                }
            }
        }
        Long protectedId = null;
        for (HistoryMeta row : rows) {
            if (APPLIED.equals(row.status())) {
                protectedId = row.id();
                break;
            }
        }
        Instant cutoff = Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS);
        List<Long> drop = new ArrayList<>();
        int kept = 0;
        for (HistoryMeta row : rows) {
            boolean protect = protectedId != null && row.id() == protectedId;
            boolean tooOld = !protect && isOlderThan(row.createdAt(), cutoff);
            if (protect || (!tooOld && kept < MAX_ENTRIES_PER_SETLIST)) {
                kept++;
                continue;
            }
            drop.add(row.id());
        }
        try (PreparedStatement delete = database.connection().prepareStatement(
                "DELETE FROM SetlistOptimizationHistory WHERE id = ?")) {
            for (Long id : drop) {
                delete.setLong(1, id);
                delete.executeUpdate();
            }
        }
    }

    private static boolean isOlderThan(String createdAt, Instant cutoff) {
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(createdAt).isBefore(cutoff);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static List<OptimizationRefusal> differences(OptimizationRevision expected, OptimizationRevision current) {
        List<OptimizationRefusal> reasons = new ArrayList<>();
        if (!orderKeys(expected).equals(orderKeys(current))) {
            reasons.add(OptimizationRefusal.CONFLICT_ORDER);
        }
        if (layoutDiffers(expected, current)) {
            reasons.add(OptimizationRefusal.CONFLICT_LAYOUT);
        }
        if (partsDiffer(expected, current)) {
            reasons.add(OptimizationRefusal.CONFLICT_PARTS);
        }
        if (!expected.pins().equals(current.pins())) {
            reasons.add(OptimizationRefusal.CONFLICT_PINS);
        }
        if (!expected.overrides().equals(current.overrides())) {
            reasons.add(OptimizationRefusal.CONFLICT_ASSIGNMENTS);
        }
        return reasons;
    }

    private static List<OrderKey> orderKeys(OptimizationRevision revision) {
        List<OrderKey> keys = new ArrayList<>();
        for (OptimizationStructureItem item : revision.items()) {
            keys.add(new OrderKey(item.id(), item.position(), item.songId()));
        }
        return keys;
    }

    private static boolean layoutDiffers(OptimizationRevision expected, OptimizationRevision current) {
        if (!Objects.equals(expected.bandLayoutId(), current.bandLayoutId())) {
            return true;
        }
        Map<Long, Long> expectedLayouts = layoutsByItem(expected);
        Map<Long, Long> currentLayouts = layoutsByItem(current);
        for (Map.Entry<Long, Long> entry : expectedLayouts.entrySet()) {
            if (!currentLayouts.containsKey(entry.getKey())) {
                continue;
            }
            if (!Objects.equals(entry.getValue(), currentLayouts.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean partsDiffer(OptimizationRevision expected, OptimizationRevision current) {
        Map<Long, String> expectedParts = partsByItem(expected);
        Map<Long, String> currentParts = partsByItem(current);
        for (Map.Entry<Long, String> entry : expectedParts.entrySet()) {
            if (!currentParts.containsKey(entry.getKey())) {
                continue;
            }
            if (!entry.getValue().equals(currentParts.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, Long> layoutsByItem(OptimizationRevision revision) {
        Map<Long, Long> layouts = new HashMap<>();
        for (OptimizationStructureItem item : revision.items()) {
            layouts.put(item.id(), item.songLayoutId());
        }
        return layouts;
    }

    private static Map<Long, String> partsByItem(OptimizationRevision revision) {
        Map<Long, String> parts = new HashMap<>();
        for (OptimizationStructureItem item : revision.items()) {
            parts.put(item.id(), item.partsText());
        }
        return parts;
    }

    private static String writeStructure(OptimizationRevision revision) throws LibraryException {
        ObjectNode root = JSON.createObjectNode();
        if (revision.bandLayoutId() == null) {
            root.putNull("bandLayoutId");
        } else {
            root.put("bandLayoutId", revision.bandLayoutId());
        }
        ArrayNode items = root.putArray("items");
        for (OptimizationStructureItem item : revision.items()) {
            ObjectNode node = items.addObject();
            node.put("id", item.id());
            node.put("position", item.position());
            node.put("songId", item.songId());
            if (item.songLayoutId() == null) {
                node.putNull("songLayoutId");
            } else {
                node.put("songLayoutId", item.songLayoutId());
            }
            node.put("parts", item.partsText());
        }
        ArrayNode pins = root.putArray("pins");
        for (AssignmentPin pin : revision.pins()) {
            ObjectNode node = pins.addObject();
            node.put("setlistItemId", pin.setlistItemId());
            node.put("partNumber", pin.partNumber());
            node.put("playerId", pin.playerId());
        }
        return write(root);
    }

    private static String writeOverrides(List<AssignmentOverride> rows) throws LibraryException {
        ArrayNode array = JSON.createArrayNode();
        for (AssignmentOverride row : rows) {
            ObjectNode node = array.addObject();
            node.put("setlistItemId", row.setlistItemId());
            node.put("playerId", row.playerId());
            if (row.partNumber() == null) {
                node.putNull("partNumber");
            } else {
                node.put("partNumber", row.partNumber());
            }
        }
        return write(array);
    }

    private static String write(JsonNode node) throws LibraryException {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new LibraryException("Failed to write optimization history", ex);
        }
    }

    private static OptimizationRevision revisionFromJson(String structureJson, String overridesJson)
            throws LibraryException {
        try {
            JsonNode structure = JSON.readTree(structureJson);
            Long bandLayoutId = longOrNull(structure, "bandLayoutId");
            List<OptimizationStructureItem> items = new ArrayList<>();
            for (JsonNode item : structure.get("items")) {
                items.add(new OptimizationStructureItem(
                        item.get("id").longValue(),
                        item.get("position").intValue(),
                        item.get("songId").longValue(),
                        longOrNull(item, "songLayoutId"),
                        textOrEmpty(item, "parts")));
            }
            List<AssignmentPin> pins = new ArrayList<>();
            for (JsonNode pin : structure.get("pins")) {
                pins.add(new AssignmentPin(
                        pin.get("setlistItemId").longValue(),
                        pin.get("partNumber").intValue(),
                        pin.get("playerId").longValue()));
            }
            return new OptimizationRevision(bandLayoutId, items, pins, readOverrides(overridesJson));
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new LibraryException("Optimization history is unreadable", ex);
        }
    }

    private static List<AssignmentOverride> readOverrides(String overridesJson) throws LibraryException {
        try {
            List<AssignmentOverride> rows = new ArrayList<>();
            for (JsonNode row : JSON.readTree(overridesJson)) {
                rows.add(new AssignmentOverride(
                        row.get("setlistItemId").longValue(),
                        row.get("playerId").longValue(),
                        intOrNull(row, "partNumber")));
            }
            return rows;
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new LibraryException("Optimization history is unreadable", ex);
        }
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.longValue();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.intValue();
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    private static String explain(List<OptimizationRefusal> reasons) {
        StringBuilder text = new StringBuilder();
        for (OptimizationRefusal reason : reasons) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(switch (reason) {
                case LOCKED -> "This setlist is locked. Unlock it before applying or undoing an optimization.";
                case STALE_REVISION -> "The setlist changed after this optimization was prepared.";
                case READ_ONLY -> "The database is read-only.";
                case CONFLICT_ASSIGNMENTS -> "Assignment overrides changed after the optimization.";
                case CONFLICT_ORDER -> "Song order or the setlist items changed.";
                case CONFLICT_LAYOUT -> "The band layout or a song layout changed.";
                case CONFLICT_PARTS -> "Song parts changed.";
                case CONFLICT_PINS -> "Assignment pins changed.";
                case NOTHING_TO_UNDO -> "There is no optimization to undo.";
                case NOT_FOUND -> "The setlist was not found.";
            });
        }
        return text.toString();
    }

    private void inTransaction(SqlWork work) throws LibraryException {
        Connection connection = database.connection();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            if (!previousAutoCommit) {
                work.run();
                return;
            }
            connection.setAutoCommit(false);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update optimization history", ex);
        }
        try {
            work.run();
            connection.commit();
        } catch (LibraryException ex) {
            rollback(connection);
            throw ex;
        } catch (SQLException ex) {
            rollback(connection);
            throw new LibraryException("Failed to update optimization history", ex);
        } catch (RuntimeException ex) {
            rollback(connection);
            throw ex;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // best-effort
            }
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best-effort
        }
    }

    private interface SqlWork {
        void run() throws SQLException, LibraryException;
    }

    private record SetlistHead(boolean locked, Long bandLayoutId) {
    }

    private record HistoryRow(
            long id,
            String structureJson,
            String beforeOverridesJson,
            String afterOverridesJson) {
    }

    private record HistoryMeta(long id, String status, String createdAt) {
    }

    private record OrderKey(long id, int position, long songId) {
    }
}

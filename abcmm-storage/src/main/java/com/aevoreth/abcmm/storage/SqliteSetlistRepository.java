package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistFolderInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * JDBC implementation of {@link SetlistRepository}. Does not close the shared database.
 */
public final class SqliteSetlistRepository implements SetlistRepository {

    private final SqliteDatabase database;

    public SqliteSetlistRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<SetlistFolderInfo> listFolders() throws LibraryException {
        String sql = """
                SELECT id, name, sort_order
                FROM SetlistFolder
                ORDER BY sort_order, name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<SetlistFolderInfo> folders = new ArrayList<>();
            while (rs.next()) {
                folders.add(new SetlistFolderInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("name")),
                        rs.getInt("sort_order")));
            }
            return List.copyOf(folders);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlist folders", ex);
        }
    }

    @Override
    public long addFolder(String name) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        try {
            int sortOrder;
            try (PreparedStatement max = database.connection().prepareStatement(
                    "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM SetlistFolder");
                 ResultSet rs = max.executeQuery()) {
                rs.next();
                sortOrder = rs.getInt(1);
            }
            String sql = """
                    INSERT INTO SetlistFolder (name, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name.strip());
                statement.setInt(2, sortOrder);
                statement.setString(3, now);
                statement.setString(4, now);
                statement.executeUpdate();
                return generatedId(statement);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add setlist folder", ex);
        }
    }

    @Override
    public void updateFolder(long id, String name) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = "UPDATE SetlistFolder SET name = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            statement.setString(2, SqliteTimestamps.now());
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update setlist folder", ex);
        }
    }

    @Override
    public void deleteFolder(long id) throws LibraryException {
        try {
            try (PreparedStatement count = database.connection().prepareStatement(
                    "SELECT COUNT(*) FROM Setlist WHERE folder_id = ?")) {
                count.setLong(1, id);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new LibraryException("Cannot delete folder: it contains setlists");
                    }
                }
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM SetlistFolder WHERE id = ?")) {
                delete.setLong(1, id);
                delete.executeUpdate();
            }
        } catch (LibraryException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete setlist folder", ex);
        }
    }

    @Override
    public void reorderFolders(List<Long> idOrder) throws LibraryException {
        Objects.requireNonNull(idOrder, "idOrder");
        String now = SqliteTimestamps.now();
        String sql = "UPDATE SetlistFolder SET sort_order = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (int i = 0; i < idOrder.size(); i++) {
                statement.setInt(1, i);
                statement.setString(2, now);
                statement.setLong(3, idOrder.get(i));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to reorder setlist folders", ex);
        }
    }

    @Override
    public List<SetlistInfo> listSetlists() throws LibraryException {
        String sql = """
                SELECT s.id, s.name, s.band_layout_id, s.folder_id, COALESCE(s.sort_order, 0) AS sort_order,
                       s.locked, s.default_change_duration_seconds, s.notes, s.set_date, s.set_time,
                       s.target_duration_seconds
                FROM Setlist s
                LEFT JOIN SetlistFolder f ON s.folder_id = f.id
                ORDER BY
                  CASE WHEN s.folder_id IS NULL THEN 1 ELSE 0 END,
                  COALESCE(f.sort_order, 999999),
                  COALESCE(s.sort_order, 999999),
                  s.name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<SetlistInfo> setlists = new ArrayList<>();
            while (rs.next()) {
                setlists.add(mapSetlist(rs));
            }
            return List.copyOf(setlists);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlists", ex);
        }
    }

    @Override
    public long addSetlist(String name, Long folderId) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        String today = LocalDate.now().toString();
        String defaultTime = "19:00";
        try {
            try (PreparedStatement bump = database.connection().prepareStatement(
                    "UPDATE Setlist SET sort_order = sort_order + 1, updated_at = ? WHERE folder_id IS ?")) {
                bump.setString(1, now);
                if (folderId == null) {
                    bump.setObject(2, null);
                } else {
                    bump.setLong(2, folderId);
                }
                bump.executeUpdate();
            }
            String sql = """
                    INSERT INTO Setlist
                    (name, band_layout_id, folder_id, sort_order, locked, default_change_duration_seconds,
                     notes, set_date, set_time, target_duration_seconds, created_at, updated_at)
                    VALUES (?, NULL, ?, 0, 0, NULL, NULL, ?, ?, NULL, ?, ?)
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name.strip());
                if (folderId == null) {
                    statement.setObject(2, null);
                } else {
                    statement.setLong(2, folderId);
                }
                statement.setString(3, today);
                statement.setString(4, defaultTime);
                statement.setString(5, now);
                statement.setString(6, now);
                statement.executeUpdate();
                return generatedId(statement);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add setlist", ex);
        }
    }

    @Override
    public void updateSetlist(
            long id,
            String name,
            Long bandLayoutId,
            Long folderId,
            Integer sortOrder,
            boolean locked,
            Integer defaultChangeDurationSeconds,
            String notes,
            String setDate,
            String setTime,
            Integer targetDurationSeconds) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = """
                UPDATE Setlist SET
                  name = ?,
                  band_layout_id = ?,
                  folder_id = ?,
                  sort_order = ?,
                  locked = ?,
                  default_change_duration_seconds = ?,
                  notes = ?,
                  set_date = ?,
                  set_time = ?,
                  target_duration_seconds = ?,
                  updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            setNullableLong(statement, 2, bandLayoutId);
            setNullableLong(statement, 3, folderId);
            statement.setInt(4, sortOrder == null ? 0 : sortOrder);
            statement.setInt(5, locked ? 1 : 0);
            setNullableInt(statement, 6, defaultChangeDurationSeconds);
            statement.setString(7, blankToNull(notes));
            statement.setString(8, blankToNull(setDate));
            statement.setString(9, blankToNull(setTime));
            setNullableInt(statement, 10, targetDurationSeconds);
            statement.setString(11, SqliteTimestamps.now());
            statement.setLong(12, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update setlist", ex);
        }
    }

    @Override
    public void deleteSetlist(long id) throws LibraryException {
        try {
            removeSetlistItemsAndOrphanLayouts(id);
            try (PreparedStatement clearPlayLog = database.connection().prepareStatement(
                    "UPDATE PlayLog SET context_setlist_id = NULL WHERE context_setlist_id = ?")) {
                clearPlayLog.setLong(1, id);
                clearPlayLog.executeUpdate();
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM Setlist WHERE id = ?")) {
                delete.setLong(1, id);
                delete.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete setlist", ex);
        }
    }

    @Override
    public List<SetlistItemInfo> listItems(long setlistId) throws LibraryException {
        String sql = """
                SELECT si.id, si.setlist_id, si.song_id, s.title, s.composers, s.duration_seconds,
                       json_array_length(COALESCE(s.parts, '[]')) AS part_count,
                       si.position, si.override_change_duration_seconds, si.song_layout_id
                FROM SetlistItem si
                JOIN Song s ON s.id = si.song_id
                WHERE si.setlist_id = ?
                ORDER BY si.position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SetlistItemInfo> items = new ArrayList<>();
                while (rs.next()) {
                    Integer duration = rs.getObject("duration_seconds") == null
                            ? null
                            : rs.getInt("duration_seconds");
                    Integer override = rs.getObject("override_change_duration_seconds") == null
                            ? null
                            : rs.getInt("override_change_duration_seconds");
                    Long songLayoutId = rs.getObject("song_layout_id") == null
                            ? null
                            : rs.getLong("song_layout_id");
                    items.add(new SetlistItemInfo(
                            rs.getLong("id"),
                            rs.getLong("setlist_id"),
                            rs.getLong("song_id"),
                            nullToEmpty(rs.getString("title")),
                            nullToEmpty(rs.getString("composers")),
                            duration,
                            rs.getInt("part_count"),
                            rs.getInt("position"),
                            override,
                            songLayoutId));
                }
                return List.copyOf(items);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlist items", ex);
        }
    }

    @Override
    public long addItem(
            long setlistId,
            long songId,
            int position,
            Integer overrideChangeDurationSeconds,
            Long songLayoutId) throws LibraryException {
        String now = SqliteTimestamps.now();
        String sql = """
                INSERT INTO SetlistItem
                (setlist_id, song_id, position, override_change_duration_seconds, song_layout_id,
                 created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, setlistId);
            statement.setLong(2, songId);
            statement.setInt(3, position);
            setNullableInt(statement, 4, overrideChangeDurationSeconds);
            setNullableLong(statement, 5, songLayoutId);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add setlist item", ex);
        }
    }

    @Override
    public void updateItem(
            long itemId,
            Integer overrideChangeDurationSeconds,
            Long songLayoutId) throws LibraryException {
        String sql = """
                UPDATE SetlistItem
                SET override_change_duration_seconds = ?, song_layout_id = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            setNullableInt(statement, 1, overrideChangeDurationSeconds);
            setNullableLong(statement, 2, songLayoutId);
            statement.setString(3, SqliteTimestamps.now());
            statement.setLong(4, itemId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update setlist item", ex);
        }
    }

    @Override
    public void removeItem(long itemId) throws LibraryException {
        try {
            try (PreparedStatement clearSong = database.connection().prepareStatement(
                    "UPDATE Song SET last_setlist_item_id = NULL WHERE last_setlist_item_id = ?")) {
                clearSong.setLong(1, itemId);
                clearSong.executeUpdate();
            } catch (SQLException ex) {
                // Column may be absent on very old DBs; ignore if missing — v12 has it.
                if (!isMissingColumn(ex)) {
                    throw ex;
                }
            }
            try (PreparedStatement clearAssignments = database.connection().prepareStatement(
                    "DELETE FROM SetlistBandAssignment WHERE setlist_item_id = ?")) {
                clearAssignments.setLong(1, itemId);
                clearAssignments.executeUpdate();
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM SetlistItem WHERE id = ?")) {
                delete.setLong(1, itemId);
                delete.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to remove setlist item", ex);
        }
    }

    @Override
    public void reorderItems(long setlistId, List<Long> itemIdOrder) throws LibraryException {
        Objects.requireNonNull(itemIdOrder, "itemIdOrder");
        String now = SqliteTimestamps.now();
        String sql = """
                UPDATE SetlistItem SET position = ?, updated_at = ?
                WHERE id = ? AND setlist_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (int i = 0; i < itemIdOrder.size(); i++) {
                statement.setInt(1, i);
                statement.setString(2, now);
                statement.setLong(3, itemIdOrder.get(i));
                statement.setLong(4, setlistId);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to reorder setlist items", ex);
        }
    }

    @Override
    public List<SetlistBandAssignmentInfo> listBandAssignments(long setlistItemId)
            throws LibraryException {
        String sql = """
                SELECT id, setlist_item_id, player_id, part_number
                FROM SetlistBandAssignment
                WHERE setlist_item_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistItemId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SetlistBandAssignmentInfo> rows = new ArrayList<>();
                while (rs.next()) {
                    Integer partNumber = rs.getObject("part_number") == null
                            ? null
                            : rs.getInt("part_number");
                    rows.add(new SetlistBandAssignmentInfo(
                            rs.getLong("id"),
                            rs.getLong("setlist_item_id"),
                            rs.getLong("player_id"),
                            partNumber));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlist band assignments", ex);
        }
    }

    @Override
    public void upsertBandAssignment(long setlistItemId, long playerId, Integer partNumber)
            throws LibraryException {
        String now = SqliteTimestamps.now();
        try {
            Long existingId = null;
            try (PreparedStatement find = database.connection().prepareStatement(
                    """
                    SELECT id FROM SetlistBandAssignment
                    WHERE setlist_item_id = ? AND player_id = ?
                    """)) {
                find.setLong(1, setlistItemId);
                find.setLong(2, playerId);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getLong(1);
                    }
                }
            }
            if (existingId != null) {
                try (PreparedStatement update = database.connection().prepareStatement(
                        """
                        UPDATE SetlistBandAssignment
                        SET part_number = ?, updated_at = ?
                        WHERE id = ?
                        """)) {
                    setNullableInt(update, 1, partNumber);
                    update.setString(2, now);
                    update.setLong(3, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = database.connection().prepareStatement(
                        """
                        INSERT INTO SetlistBandAssignment
                        (setlist_item_id, player_id, part_number, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, setlistItemId);
                    insert.setLong(2, playerId);
                    setNullableInt(insert, 3, partNumber);
                    insert.setString(4, now);
                    insert.setString(5, now);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to upsert setlist band assignment", ex);
        }
    }

    @Override
    public void deleteBandAssignment(long setlistItemId, long playerId) throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM SetlistBandAssignment WHERE setlist_item_id = ? AND player_id = ?")) {
            statement.setLong(1, setlistItemId);
            statement.setLong(2, playerId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete setlist band assignment", ex);
        }
    }

    private void removeSetlistItemsAndOrphanLayouts(long setlistId) throws SQLException {
        List<Long> songLayoutIds = new ArrayList<>();
        try (PreparedStatement select = database.connection().prepareStatement(
                "SELECT song_layout_id FROM SetlistItem WHERE setlist_id = ? AND song_layout_id IS NOT NULL")) {
            select.setLong(1, setlistId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    songLayoutIds.add(rs.getLong(1));
                }
            }
        }

        try (PreparedStatement clearSong = database.connection().prepareStatement(
                """
                UPDATE Song SET last_setlist_item_id = NULL
                WHERE last_setlist_item_id IN (SELECT id FROM SetlistItem WHERE setlist_id = ?)
                """)) {
            clearSong.setLong(1, setlistId);
            clearSong.executeUpdate();
        } catch (SQLException ex) {
            if (!isMissingColumn(ex)) {
                throw ex;
            }
        }

        try (PreparedStatement clearAssignments = database.connection().prepareStatement(
                """
                DELETE FROM SetlistBandAssignment
                WHERE setlist_item_id IN (SELECT id FROM SetlistItem WHERE setlist_id = ?)
                """)) {
            clearAssignments.setLong(1, setlistId);
            clearAssignments.executeUpdate();
        }

        try (PreparedStatement deleteItems = database.connection().prepareStatement(
                "DELETE FROM SetlistItem WHERE setlist_id = ?")) {
            deleteItems.setLong(1, setlistId);
            deleteItems.executeUpdate();
        }

        for (Long songLayoutId : songLayoutIds) {
            boolean stillUsed;
            try (PreparedStatement check = database.connection().prepareStatement(
                    "SELECT 1 FROM SetlistItem WHERE song_layout_id = ? LIMIT 1")) {
                check.setLong(1, songLayoutId);
                try (ResultSet rs = check.executeQuery()) {
                    stillUsed = rs.next();
                }
            }
            if (!stillUsed) {
                try (PreparedStatement delAssign = database.connection().prepareStatement(
                        "DELETE FROM SongLayoutAssignment WHERE song_layout_id = ?")) {
                    delAssign.setLong(1, songLayoutId);
                    delAssign.executeUpdate();
                }
                try (PreparedStatement delLayout = database.connection().prepareStatement(
                        "DELETE FROM SongLayout WHERE id = ?")) {
                    delLayout.setLong(1, songLayoutId);
                    delLayout.executeUpdate();
                }
            }
        }
    }

    private static boolean isMissingColumn(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("no such column");
    }

    private static SetlistInfo mapSetlist(ResultSet rs) throws SQLException {
        Long bandLayoutId = rs.getObject("band_layout_id") == null
                ? null
                : rs.getLong("band_layout_id");
        Long folderId = rs.getObject("folder_id") == null ? null : rs.getLong("folder_id");
        Integer defaultChange = rs.getObject("default_change_duration_seconds") == null
                ? null
                : rs.getInt("default_change_duration_seconds");
        Integer targetDuration = rs.getObject("target_duration_seconds") == null
                ? null
                : rs.getInt("target_duration_seconds");
        return new SetlistInfo(
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                bandLayoutId,
                folderId,
                rs.getInt("sort_order"),
                rs.getInt("locked") != 0,
                defaultChange,
                blankToNull(rs.getString("notes")),
                blankToNull(rs.getString("set_date")),
                blankToNull(rs.getString("set_time")),
                targetDuration);
    }

    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated key returned");
            }
            return keys.getLong(1);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

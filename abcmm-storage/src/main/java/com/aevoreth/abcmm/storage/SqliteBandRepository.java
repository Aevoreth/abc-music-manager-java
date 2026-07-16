package com.aevoreth.abcmm.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * JDBC implementation of {@link BandRepository}. Does not close the shared database.
 */
public final class SqliteBandRepository implements BandRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SqliteDatabase database;

    public SqliteBandRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<BandInfo> listBands() throws LibraryException {
        String sql = """
                SELECT id, name, notes, COALESCE(sort_order, 0) AS sort_order
                FROM Band
                ORDER BY sort_order, name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<BandInfo> bands = new ArrayList<>();
            while (rs.next()) {
                bands.add(new BandInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("name")),
                        blankToNull(rs.getString("notes")),
                        rs.getInt("sort_order")));
            }
            return List.copyOf(bands);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list bands", ex);
        }
    }

    @Override
    public long addBand(String name, String notes) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        try {
            int sortOrder;
            try (PreparedStatement max = database.connection().prepareStatement(
                    "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM Band");
                 ResultSet rs = max.executeQuery()) {
                rs.next();
                sortOrder = rs.getInt(1);
            }
            String sql = """
                    INSERT INTO Band (name, notes, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            long bandId;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name.strip());
                statement.setString(2, blankToNull(notes));
                statement.setInt(3, sortOrder);
                statement.setString(4, now);
                statement.setString(5, now);
                statement.executeUpdate();
                bandId = generatedId(statement);
            }
            // A band is one layout; create it immediately for setlist / grid use.
            addLayout(bandId, name.strip());
            return bandId;
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add band", ex);
        }
    }

    @Override
    public void updateBand(long id, String name, String notes) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = "UPDATE Band SET name = ?, notes = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            statement.setString(2, blankToNull(notes));
            statement.setString(3, SqliteTimestamps.now());
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update band", ex);
        }
        BandLayoutInfo layout = getOrCreatePrimaryLayout(id);
        updateLayout(layout.id(), name.strip(), layout.exportColumnOrderJson());
    }

    @Override
    public void deleteBand(long id) throws LibraryException {
        try {
            try (PreparedStatement slots = database.connection().prepareStatement(
                    """
                    DELETE FROM BandLayoutSlot
                    WHERE band_layout_id IN (SELECT id FROM BandLayout WHERE band_id = ?)
                    """)) {
                slots.setLong(1, id);
                slots.executeUpdate();
            }
            executeDelete("DELETE FROM BandLayout WHERE band_id = ?", id);
            executeDelete("DELETE FROM BandMember WHERE band_id = ?", id);
            executeDelete("DELETE FROM Band WHERE id = ?", id);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete band", ex);
        }
    }

    @Override
    public void reorderBands(List<Long> idOrder) throws LibraryException {
        Objects.requireNonNull(idOrder, "idOrder");
        String now = SqliteTimestamps.now();
        String sql = "UPDATE Band SET sort_order = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (int i = 0; i < idOrder.size(); i++) {
                statement.setInt(1, i);
                statement.setString(2, now);
                statement.setLong(3, idOrder.get(i));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to reorder bands", ex);
        }
    }

    @Override
    public long duplicateBand(long bandId) throws LibraryException {
        try {
            String name;
            String notes;
            try (PreparedStatement find = database.connection().prepareStatement(
                    "SELECT name, notes FROM Band WHERE id = ?")) {
                find.setLong(1, bandId);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) {
                        throw new LibraryException("Band not found: " + bandId);
                    }
                    name = rs.getString("name");
                    notes = rs.getString("notes");
                }
            }
            // addBand already creates one primary layout; copy slots onto it (and drop extras).
            long newBandId = addBand(name + " - Copy", notes);
            BandLayoutInfo newPrimary = getOrCreatePrimaryLayout(newBandId);
            BandLayoutInfo sourcePrimary = getOrCreatePrimaryLayout(bandId);
            if (sourcePrimary.exportColumnOrderJson() != null) {
                updateLayout(
                        newPrimary.id(),
                        newPrimary.name(),
                        sourcePrimary.exportColumnOrderJson());
            }
            for (BandLayoutSlotInfo slot : listSlots(sourcePrimary.id())) {
                setSlot(
                        newPrimary.id(),
                        slot.playerId(),
                        slot.x(),
                        slot.y(),
                        slot.widthUnits(),
                        slot.heightUnits());
            }
            syncMembersFromPrimaryLayout(newBandId);
            return newBandId;
        } catch (SQLException ex) {
            throw new LibraryException("Failed to duplicate band", ex);
        }
    }

    @Override
    public List<PlayerInfo> listMembers(long bandId) throws LibraryException {
        String sql = """
                SELECT p.id, p.name, p.level, p."class"
                FROM BandMember bm
                JOIN Player p ON p.id = bm.player_id
                WHERE bm.band_id = ?
                ORDER BY p.name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<PlayerInfo> members = new ArrayList<>();
                while (rs.next()) {
                    Integer level = rs.getObject("level") == null ? null : rs.getInt("level");
                    members.add(new PlayerInfo(
                            rs.getLong("id"),
                            nullToEmpty(rs.getString("name")),
                            level,
                            blankToNull(rs.getString("class"))));
                }
                return List.copyOf(members);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list band members", ex);
        }
    }

    @Override
    public void setMembers(long bandId, List<Long> playerIds) throws LibraryException {
        Objects.requireNonNull(playerIds, "playerIds");
        try {
            executeDelete("DELETE FROM BandMember WHERE band_id = ?", bandId);
            try (PreparedStatement insert = database.connection().prepareStatement(
                    "INSERT OR IGNORE INTO BandMember (band_id, player_id) VALUES (?, ?)")) {
                for (Long playerId : playerIds) {
                    insert.setLong(1, bandId);
                    insert.setLong(2, playerId);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to set band members", ex);
        }
    }

    @Override
    public BandLayoutInfo getOrCreatePrimaryLayout(long bandId) throws LibraryException {
        List<BandLayoutInfo> layouts = listLayouts(bandId);
        if (!layouts.isEmpty()) {
            return layouts.get(0);
        }
        String bandName = "Layout";
        try (PreparedStatement find = database.connection().prepareStatement(
                "SELECT name FROM Band WHERE id = ?")) {
            find.setLong(1, bandId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && !name.isBlank()) {
                        bandName = name.strip();
                    }
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to load band for layout", ex);
        }
        long layoutId = addLayout(bandId, bandName);
        layouts = listLayouts(bandId);
        for (BandLayoutInfo layout : layouts) {
            if (layout.id() == layoutId) {
                return layout;
            }
        }
        throw new LibraryException("Failed to create primary layout for band " + bandId);
    }

    @Override
    public void syncMembersFromPrimaryLayout(long bandId) throws LibraryException {
        BandLayoutInfo layout = getOrCreatePrimaryLayout(bandId);
        List<Long> playerIds = new ArrayList<>();
        for (BandLayoutSlotInfo slot : listSlots(layout.id())) {
            playerIds.add(slot.playerId());
        }
        setMembers(bandId, playerIds);
    }

    @Override
    public List<BandLayoutInfo> listLayouts(long bandId) throws LibraryException {
        String sql = """
                SELECT id, band_id, name, export_column_order, COALESCE(sort_order, 0) AS sort_order
                FROM BandLayout
                WHERE band_id = ?
                ORDER BY sort_order, id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<BandLayoutInfo> layouts = new ArrayList<>();
                while (rs.next()) {
                    layouts.add(new BandLayoutInfo(
                            rs.getLong("id"),
                            rs.getLong("band_id"),
                            nullToEmpty(rs.getString("name")),
                            blankToNull(rs.getString("export_column_order")),
                            rs.getInt("sort_order")));
                }
                return List.copyOf(layouts);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list band layouts", ex);
        }
    }

    @Override
    public long addLayout(long bandId, String name) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        try {
            int sortOrder;
            try (PreparedStatement max = database.connection().prepareStatement(
                    "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM BandLayout WHERE band_id = ?")) {
                max.setLong(1, bandId);
                try (ResultSet rs = max.executeQuery()) {
                    rs.next();
                    sortOrder = rs.getInt(1);
                }
            }
            String sql = """
                    INSERT INTO BandLayout (band_id, name, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, bandId);
                statement.setString(2, name.strip());
                statement.setInt(3, sortOrder);
                statement.setString(4, now);
                statement.setString(5, now);
                statement.executeUpdate();
                return generatedId(statement);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add band layout", ex);
        }
    }

    @Override
    public void updateLayout(long layoutId, String name, String exportColumnOrderJson)
            throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = """
                UPDATE BandLayout
                SET name = ?, export_column_order = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            statement.setString(2, blankToNull(exportColumnOrderJson));
            statement.setString(3, SqliteTimestamps.now());
            statement.setLong(4, layoutId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update band layout", ex);
        }
    }

    @Override
    public void deleteLayout(long layoutId) throws LibraryException {
        try {
            executeDelete("DELETE FROM BandLayoutSlot WHERE band_layout_id = ?", layoutId);
            executeDelete("DELETE FROM BandLayout WHERE id = ?", layoutId);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete band layout", ex);
        }
    }

    @Override
    public List<BandLayoutSlotInfo> listSlots(long bandLayoutId) throws LibraryException {
        String sql = """
                SELECT s.id, s.band_layout_id, s.player_id, p.name AS player_name,
                       s.x, s.y, s.width_units, s.height_units
                FROM BandLayoutSlot s
                JOIN Player p ON p.id = s.player_id
                WHERE s.band_layout_id = ?
                ORDER BY s.y, s.x
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandLayoutId);
            try (ResultSet rs = statement.executeQuery()) {
                List<BandLayoutSlotInfo> slots = new ArrayList<>();
                while (rs.next()) {
                    slots.add(new BandLayoutSlotInfo(
                            rs.getLong("id"),
                            rs.getLong("band_layout_id"),
                            rs.getLong("player_id"),
                            nullToEmpty(rs.getString("player_name")),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("width_units"),
                            rs.getInt("height_units")));
                }
                return List.copyOf(slots);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list layout slots", ex);
        }
    }

    @Override
    public void setSlot(
            long bandLayoutId,
            long playerId,
            int x,
            int y,
            int widthUnits,
            int heightUnits) throws LibraryException {
        String now = SqliteTimestamps.now();
        try {
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM BandLayoutSlot WHERE band_layout_id = ? AND player_id = ?")) {
                delete.setLong(1, bandLayoutId);
                delete.setLong(2, playerId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = database.connection().prepareStatement(
                    """
                    INSERT INTO BandLayoutSlot
                    (band_layout_id, player_id, x, y, width_units, height_units, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setLong(1, bandLayoutId);
                insert.setLong(2, playerId);
                insert.setInt(3, x);
                insert.setInt(4, y);
                insert.setInt(5, widthUnits);
                insert.setInt(6, heightUnits);
                insert.setString(7, now);
                insert.setString(8, now);
                insert.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to set layout slot", ex);
        }
    }

    @Override
    public void deleteSlot(long bandLayoutId, long playerId) throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM BandLayoutSlot WHERE band_layout_id = ? AND player_id = ?")) {
            statement.setLong(1, bandLayoutId);
            statement.setLong(2, playerId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete layout slot", ex);
        }
    }

    @Override
    public void replaceSlots(long bandLayoutId, List<BandLayoutSlotInfo> slots)
            throws LibraryException {
        Objects.requireNonNull(slots, "slots");
        try {
            executeDelete("DELETE FROM BandLayoutSlot WHERE band_layout_id = ?", bandLayoutId);
            String now = SqliteTimestamps.now();
            try (PreparedStatement insert = database.connection().prepareStatement(
                    """
                    INSERT INTO BandLayoutSlot
                    (band_layout_id, player_id, x, y, width_units, height_units, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (BandLayoutSlotInfo slot : slots) {
                    insert.setLong(1, bandLayoutId);
                    insert.setLong(2, slot.playerId());
                    insert.setInt(3, slot.x());
                    insert.setInt(4, slot.y());
                    insert.setInt(5, slot.widthUnits());
                    insert.setInt(6, slot.heightUnits());
                    insert.setString(7, now);
                    insert.setString(8, now);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to replace layout slots", ex);
        }
    }

    @Override
    public void replacePlayerInBandLayout(
            long bandLayoutId,
            long bandId,
            long oldPlayerId,
            long newPlayerId) throws LibraryException {
        if (oldPlayerId == newPlayerId) {
            throw new LibraryException("old and new player must differ");
        }
        List<BandLayoutSlotInfo> slots = listSlots(bandLayoutId);
        BandLayoutSlotInfo oldSlot = null;
        for (BandLayoutSlotInfo slot : slots) {
            if (slot.playerId() == oldPlayerId) {
                oldSlot = slot;
            }
            if (slot.playerId() == newPlayerId) {
                throw new LibraryException("Player " + newPlayerId + " already on layout " + bandLayoutId);
            }
        }
        if (oldSlot == null) {
            throw new LibraryException("Player " + oldPlayerId + " not on layout " + bandLayoutId);
        }

        Connection connection = database.connection();
        String now = SqliteTimestamps.now();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to begin replace-player transaction", ex);
        }
        try {
            transferSongLayoutAssignments(connection, bandLayoutId, oldPlayerId, newPlayerId, now);
            transferSetlistBandAssignments(connection, bandLayoutId, oldPlayerId, newPlayerId, now);
            rewriteExportColumnOrder(connection, bandLayoutId, oldPlayerId, newPlayerId, now);

            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM BandLayoutSlot WHERE band_layout_id = ? AND player_id = ?")) {
                delete.setLong(1, bandLayoutId);
                delete.setLong(2, oldPlayerId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                    INSERT INTO BandLayoutSlot
                    (band_layout_id, player_id, x, y, width_units, height_units, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setLong(1, bandLayoutId);
                insert.setLong(2, newPlayerId);
                insert.setInt(3, oldSlot.x());
                insert.setInt(4, oldSlot.y());
                insert.setInt(5, oldSlot.widthUnits());
                insert.setInt(6, oldSlot.heightUnits());
                insert.setString(7, now);
                insert.setString(8, now);
                insert.executeUpdate();
            }
            try (PreparedStatement member = connection.prepareStatement(
                    "INSERT OR IGNORE INTO BandMember (band_id, player_id) VALUES (?, ?)")) {
                member.setLong(1, bandId);
                member.setLong(2, newPlayerId);
                member.executeUpdate();
            }

            connection.commit();
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // best-effort
            }
            if (ex instanceof LibraryException libraryEx) {
                throw libraryEx;
            }
            throw new LibraryException("Failed to replace player on band layout", ex);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // best-effort
            }
        }
    }

    private static void transferSongLayoutAssignments(
            Connection connection,
            long bandLayoutId,
            long oldPlayerId,
            long newPlayerId,
            String now) throws SQLException {
        List<long[]> rows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
                SELECT sla.song_layout_id, sla.part_number
                FROM SongLayoutAssignment sla
                JOIN SongLayout sl ON sl.id = sla.song_layout_id
                WHERE sl.band_layout_id = ? AND sla.player_id = ?
                """)) {
            select.setLong(1, bandLayoutId);
            select.setLong(2, oldPlayerId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long songLayoutId = rs.getLong(1);
                    Integer partNumber = rs.getObject(2) == null ? null : rs.getInt(2);
                    rows.add(new long[] {
                            songLayoutId,
                            partNumber == null ? Long.MIN_VALUE : partNumber.longValue()
                    });
                }
            }
        }
        for (long[] row : rows) {
            long songLayoutId = row[0];
            Integer partNumber = row[1] == Long.MIN_VALUE ? null : (int) row[1];
            upsertSongLayoutAssignment(connection, songLayoutId, newPlayerId, partNumber, now);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM SongLayoutAssignment WHERE song_layout_id = ? AND player_id = ?")) {
                delete.setLong(1, songLayoutId);
                delete.setLong(2, oldPlayerId);
                delete.executeUpdate();
            }
        }
    }

    private static void transferSetlistBandAssignments(
            Connection connection,
            long bandLayoutId,
            long oldPlayerId,
            long newPlayerId,
            String now) throws SQLException {
        List<long[]> rows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
                SELECT sba.setlist_item_id, sba.part_number
                FROM SetlistBandAssignment sba
                JOIN SetlistItem si ON si.id = sba.setlist_item_id
                JOIN SongLayout sl ON sl.id = si.song_layout_id
                WHERE sl.band_layout_id = ? AND sba.player_id = ?
                """)) {
            select.setLong(1, bandLayoutId);
            select.setLong(2, oldPlayerId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long setlistItemId = rs.getLong(1);
                    Integer partNumber = rs.getObject(2) == null ? null : rs.getInt(2);
                    rows.add(new long[] {
                            setlistItemId,
                            partNumber == null ? Long.MIN_VALUE : partNumber.longValue()
                    });
                }
            }
        }
        for (long[] row : rows) {
            long setlistItemId = row[0];
            Integer partNumber = row[1] == Long.MIN_VALUE ? null : (int) row[1];
            upsertSetlistBandAssignment(connection, setlistItemId, newPlayerId, partNumber, now);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM SetlistBandAssignment WHERE setlist_item_id = ? AND player_id = ?")) {
                delete.setLong(1, setlistItemId);
                delete.setLong(2, oldPlayerId);
                delete.executeUpdate();
            }
        }
    }

    private static void upsertSongLayoutAssignment(
            Connection connection,
            long songLayoutId,
            long playerId,
            Integer partNumber,
            String now) throws SQLException {
        Long existingId = null;
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id FROM SongLayoutAssignment WHERE song_layout_id = ? AND player_id = ?")) {
            find.setLong(1, songLayoutId);
            find.setLong(2, playerId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    existingId = rs.getLong(1);
                }
            }
        }
        if (existingId != null) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE SongLayoutAssignment SET part_number = ?, updated_at = ? WHERE id = ?")) {
                setNullableInt(update, 1, partNumber);
                update.setString(2, now);
                update.setLong(3, existingId);
                update.executeUpdate();
            }
        } else {
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                    INSERT INTO SongLayoutAssignment
                    (song_layout_id, player_id, part_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setLong(1, songLayoutId);
                insert.setLong(2, playerId);
                setNullableInt(insert, 3, partNumber);
                insert.setString(4, now);
                insert.setString(5, now);
                insert.executeUpdate();
            }
        }
    }

    private static void upsertSetlistBandAssignment(
            Connection connection,
            long setlistItemId,
            long playerId,
            Integer partNumber,
            String now) throws SQLException {
        Long existingId = null;
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id FROM SetlistBandAssignment WHERE setlist_item_id = ? AND player_id = ?")) {
            find.setLong(1, setlistItemId);
            find.setLong(2, playerId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    existingId = rs.getLong(1);
                }
            }
        }
        if (existingId != null) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE SetlistBandAssignment SET part_number = ?, updated_at = ? WHERE id = ?")) {
                setNullableInt(update, 1, partNumber);
                update.setString(2, now);
                update.setLong(3, existingId);
                update.executeUpdate();
            }
        } else {
            try (PreparedStatement insert = connection.prepareStatement(
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
    }

    private static void rewriteExportColumnOrder(
            Connection connection,
            long bandLayoutId,
            long oldPlayerId,
            long newPlayerId,
            String now) throws SQLException, LibraryException {
        String exportJson;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT export_column_order FROM BandLayout WHERE id = ?")) {
            select.setLong(1, bandLayoutId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                exportJson = rs.getString(1);
            }
        }
        if (exportJson == null || exportJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(exportJson);
            if (!root.isArray()) {
                return;
            }
            boolean changed = false;
            ArrayNode rewritten = JSON.createArrayNode();
            for (JsonNode node : root) {
                long playerId = node.asLong();
                if (playerId == oldPlayerId) {
                    rewritten.add(newPlayerId);
                    changed = true;
                } else {
                    rewritten.add(playerId);
                }
            }
            if (!changed) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE BandLayout SET export_column_order = ?, updated_at = ? WHERE id = ?")) {
                update.setString(1, JSON.writeValueAsString(rewritten));
                update.setString(2, now);
                update.setLong(3, bandLayoutId);
                update.executeUpdate();
            }
        } catch (SQLException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LibraryException("Failed to rewrite export_column_order", ex);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void executeDelete(String sql, long id) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated key returned");
            }
            return keys.getLong(1);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * JDBC implementation of {@link BandRepository}. Does not close the shared database.
 */
public final class SqliteBandRepository implements BandRepository {

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

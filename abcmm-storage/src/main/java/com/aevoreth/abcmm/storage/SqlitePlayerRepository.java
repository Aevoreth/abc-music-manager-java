package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * JDBC implementation of {@link PlayerRepository}. Does not close the shared database.
 */
public final class SqlitePlayerRepository implements PlayerRepository {

    private final SqliteDatabase database;

    public SqlitePlayerRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<PlayerInfo> listPlayers() throws LibraryException {
        String sql = """
                SELECT id, name, level, "class"
                FROM Player
                ORDER BY name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PlayerInfo> players = new ArrayList<>();
            while (rs.next()) {
                players.add(mapPlayer(rs));
            }
            return List.copyOf(players);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list players", ex);
        }
    }

    @Override
    public PlayerInfo getPlayer(long id) throws LibraryException {
        String sql = """
                SELECT id, name, level, "class"
                FROM Player
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapPlayer(rs);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to get player", ex);
        }
    }

    @Override
    public long addPlayer(String name, Integer level, String characterClass)
            throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        String sql = """
                INSERT INTO Player (name, level, "class", created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name.strip());
            setNullableInt(statement, 2, level);
            statement.setString(3, blankToNull(characterClass));
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add player", ex);
        }
    }

    @Override
    public void updatePlayer(long id, String name, Integer level, String characterClass)
            throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = """
                UPDATE Player SET name = ?, level = ?, "class" = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            setNullableInt(statement, 2, level);
            statement.setString(3, blankToNull(characterClass));
            statement.setString(4, SqliteTimestamps.now());
            statement.setLong(5, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update player", ex);
        }
    }

    @Override
    public void deletePlayer(long id) throws LibraryException {
        try {
            executeDelete("DELETE FROM PlayerInstrument WHERE player_id = ?", id);
            executeDelete("DELETE FROM BandLayoutSlot WHERE player_id = ?", id);
            executeDelete("DELETE FROM BandMember WHERE player_id = ?", id);
            executeDelete("DELETE FROM SongLayoutAssignment WHERE player_id = ?", id);
            executeDelete("DELETE FROM SetlistBandAssignment WHERE player_id = ?", id);
            executeDelete("DELETE FROM Player WHERE id = ?", id);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete player", ex);
        }
    }

    @Override
    public List<InstrumentInfo> listInstruments() throws LibraryException {
        String sql = "SELECT id, name FROM Instrument ORDER BY name";
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<InstrumentInfo> instruments = new ArrayList<>();
            while (rs.next()) {
                instruments.add(new InstrumentInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("name"))));
            }
            return List.copyOf(instruments);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list instruments", ex);
        }
    }

    @Override
    public List<PlayerInstrumentInfo> listPlayerInstruments(long playerId)
            throws LibraryException {
        String sql = """
                SELECT pi.instrument_id, i.name, pi.has_instrument, pi.has_proficiency, pi.notes
                FROM PlayerInstrument pi
                JOIN Instrument i ON i.id = pi.instrument_id
                WHERE pi.player_id = ?
                ORDER BY i.name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, playerId);
            try (ResultSet rs = statement.executeQuery()) {
                List<PlayerInstrumentInfo> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PlayerInstrumentInfo(
                            rs.getLong("instrument_id"),
                            nullToEmpty(rs.getString("name")),
                            rs.getInt("has_instrument") != 0,
                            rs.getInt("has_proficiency") != 0,
                            blankToNull(rs.getString("notes"))));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list player instruments", ex);
        }
    }

    @Override
    public void setPlayerInstrument(
            long playerId,
            long instrumentId,
            boolean hasInstrument,
            boolean hasProficiency,
            String notes) throws LibraryException {
        String now = SqliteTimestamps.now();
        try {
            Long existingId = null;
            try (PreparedStatement find = database.connection().prepareStatement(
                    "SELECT id FROM PlayerInstrument WHERE player_id = ? AND instrument_id = ?")) {
                find.setLong(1, playerId);
                find.setLong(2, instrumentId);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getLong(1);
                    }
                }
            }
            if (existingId != null) {
                try (PreparedStatement update = database.connection().prepareStatement(
                        """
                        UPDATE PlayerInstrument
                        SET has_instrument = ?, has_proficiency = ?, notes = ?, updated_at = ?
                        WHERE id = ?
                        """)) {
                    update.setInt(1, hasInstrument ? 1 : 0);
                    update.setInt(2, hasProficiency ? 1 : 0);
                    update.setString(3, blankToNull(notes));
                    update.setString(4, now);
                    update.setLong(5, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = database.connection().prepareStatement(
                        """
                        INSERT INTO PlayerInstrument
                        (player_id, instrument_id, has_instrument, has_proficiency, notes, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, playerId);
                    insert.setLong(2, instrumentId);
                    insert.setInt(3, hasInstrument ? 1 : 0);
                    insert.setInt(4, hasProficiency ? 1 : 0);
                    insert.setString(5, blankToNull(notes));
                    insert.setString(6, now);
                    insert.setString(7, now);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to set player instrument", ex);
        }
    }

    private void executeDelete(String sql, long id) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static PlayerInfo mapPlayer(ResultSet rs) throws SQLException {
        Integer level = rs.getObject("level") == null ? null : rs.getInt("level");
        return new PlayerInfo(
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                level,
                blankToNull(rs.getString("class")));
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

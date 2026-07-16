package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * JDBC implementation of {@link SongLayoutRepository}. Does not close the shared database.
 */
public final class SqliteSongLayoutRepository implements SongLayoutRepository {

    private final SqliteDatabase database;

    public SqliteSongLayoutRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public SongLayoutInfo getOrCreateSongLayout(long songId, long bandLayoutId, String name)
            throws LibraryException {
        String findSql = """
                SELECT id, song_id, band_layout_id, name
                FROM SongLayout
                WHERE song_id = ? AND band_layout_id = ?
                ORDER BY name
                LIMIT 1
                """;
        try (PreparedStatement find = database.connection().prepareStatement(findSql)) {
            find.setLong(1, songId);
            find.setLong(2, bandLayoutId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return mapLayout(rs);
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to get song layout", ex);
        }

        String now = SqliteTimestamps.now();
        String insertSql = """
                INSERT INTO SongLayout (song_id, band_layout_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement insert = database.connection().prepareStatement(
                insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, songId);
            insert.setLong(2, bandLayoutId);
            insert.setString(3, blankToNull(name));
            insert.setString(4, now);
            insert.setString(5, now);
            insert.executeUpdate();
            long id = generatedId(insert);
            return new SongLayoutInfo(id, songId, bandLayoutId, blankToNull(name));
        } catch (SQLException ex) {
            throw new LibraryException("Failed to create song layout", ex);
        }
    }

    @Override
    public List<SongLayoutAssignmentInfo> listAssignments(long songLayoutId)
            throws LibraryException {
        String sql = """
                SELECT id, song_layout_id, player_id, part_number
                FROM SongLayoutAssignment
                WHERE song_layout_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songLayoutId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SongLayoutAssignmentInfo> rows = new ArrayList<>();
                while (rs.next()) {
                    Integer partNumber = rs.getObject("part_number") == null
                            ? null
                            : rs.getInt("part_number");
                    rows.add(new SongLayoutAssignmentInfo(
                            rs.getLong("id"),
                            rs.getLong("song_layout_id"),
                            rs.getLong("player_id"),
                            partNumber));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list song layout assignments", ex);
        }
    }

    @Override
    public void setAssignment(long songLayoutId, long playerId, Integer partNumberOrNull)
            throws LibraryException {
        String now = SqliteTimestamps.now();
        try {
            Long existingId = null;
            try (PreparedStatement find = database.connection().prepareStatement(
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
                try (PreparedStatement update = database.connection().prepareStatement(
                        """
                        UPDATE SongLayoutAssignment
                        SET part_number = ?, updated_at = ?
                        WHERE id = ?
                        """)) {
                    setNullableInt(update, 1, partNumberOrNull);
                    update.setString(2, now);
                    update.setLong(3, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = database.connection().prepareStatement(
                        """
                        INSERT INTO SongLayoutAssignment
                        (song_layout_id, player_id, part_number, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, songLayoutId);
                    insert.setLong(2, playerId);
                    setNullableInt(insert, 3, partNumberOrNull);
                    insert.setString(4, now);
                    insert.setString(5, now);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to set song layout assignment", ex);
        }
    }

    private static SongLayoutInfo mapLayout(ResultSet rs) throws SQLException {
        return new SongLayoutInfo(
                rs.getLong("id"),
                rs.getLong("song_id"),
                rs.getLong("band_layout_id"),
                blankToNull(rs.getString("name")));
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

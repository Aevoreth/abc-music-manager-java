package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.PlayLogEntry;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;

/**
 * JDBC PlayLog access matching Python {@code play_log.py}.
 */
public final class SqlitePlayLogRepository implements PlayLogRepository {

    private final SqliteDatabase database;

    public SqlitePlayLogRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void logPlay(long songId, Long contextSetlistId, String contextNote) throws LibraryException {
        String now = SqliteTimestamps.now();
        insertAndRefresh(songId, now, contextSetlistId, contextNote, now);
    }

    @Override
    public void logPlayAt(long songId, String playedAtIso, Long contextSetlistId, String contextNote)
            throws LibraryException {
        Objects.requireNonNull(playedAtIso, "playedAtIso");
        insertAndRefresh(songId, playedAtIso, contextSetlistId, contextNote, playedAtIso);
    }

    @Override
    public List<PlayLogEntry> getPlayHistory(long songId, int limit) throws LibraryException {
        int safeLimit = Math.max(1, limit);
        String sql = """
                SELECT pl.id, pl.played_at, sl.name, pl.context_note
                FROM PlayLog pl
                LEFT JOIN Setlist sl ON sl.id = pl.context_setlist_id
                WHERE pl.song_id = ?
                ORDER BY pl.played_at DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songId);
            statement.setInt(2, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                List<PlayLogEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(new PlayLogEntry(
                            rs.getLong(1),
                            rs.getString(2),
                            blankToNull(rs.getString(3)),
                            blankToNull(rs.getString(4))));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to load play history for song " + songId, ex);
        }
    }

    @Override
    public Optional<Long> updatePlay(long playLogId, String playedAtIso, String contextNote)
            throws LibraryException {
        Objects.requireNonNull(playedAtIso, "playedAtIso");
        try {
            Long songId = songIdForPlayLog(playLogId);
            if (songId == null) {
                return Optional.empty();
            }
            try (PreparedStatement update = database.connection().prepareStatement(
                    "UPDATE PlayLog SET played_at = ?, context_note = ? WHERE id = ?")) {
                update.setString(1, playedAtIso);
                update.setString(2, blankToNull(contextNote));
                update.setLong(3, playLogId);
                update.executeUpdate();
            }
            refreshSongPlayAggregates(songId);
            return Optional.of(songId);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update play log " + playLogId, ex);
        }
    }

    @Override
    public Optional<Long> deletePlay(long playLogId) throws LibraryException {
        try {
            Long songId = songIdForPlayLog(playLogId);
            if (songId == null) {
                return Optional.empty();
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM PlayLog WHERE id = ?")) {
                delete.setLong(1, playLogId);
                delete.executeUpdate();
            }
            refreshSongPlayAggregates(songId);
            return Optional.of(songId);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete play log " + playLogId, ex);
        }
    }

    private void insertAndRefresh(
            long songId,
            String playedAt,
            Long contextSetlistId,
            String contextNote,
            String createdAt) throws LibraryException {
        try {
            try (PreparedStatement insert = database.connection().prepareStatement(
                    """
                            INSERT INTO PlayLog (song_id, played_at, context_setlist_id, context_note, created_at)
                            VALUES (?, ?, ?, ?, ?)
                            """)) {
                insert.setLong(1, songId);
                insert.setString(2, playedAt);
                if (contextSetlistId == null) {
                    insert.setObject(3, null);
                } else {
                    insert.setLong(3, contextSetlistId);
                }
                insert.setString(4, blankToNull(contextNote));
                insert.setString(5, createdAt);
                insert.executeUpdate();
            }
            refreshSongPlayAggregates(songId);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to log play for song " + songId, ex);
        }
    }

    private Long songIdForPlayLog(long playLogId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT song_id FROM PlayLog WHERE id = ?")) {
            statement.setLong(1, playLogId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getLong(1);
            }
        }
    }

    private void refreshSongPlayAggregates(long songId) throws SQLException {
        String now = SqliteTimestamps.now();
        try (PreparedStatement update = database.connection().prepareStatement(
                """
                        UPDATE Song SET last_played_at = (SELECT MAX(played_at) FROM PlayLog WHERE song_id = Song.id),
                           total_plays = (SELECT COUNT(*) FROM PlayLog WHERE song_id = Song.id),
                           updated_at = ? WHERE id = ?
                        """)) {
            update.setString(1, now);
            update.setLong(2, songId);
            update.executeUpdate();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

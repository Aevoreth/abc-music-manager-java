package com.aevoreth.abcmm.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.SetlistRef;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;
import com.aevoreth.abcmm.domain.library.SongDetailInfo;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Song/library queries and metadata writes against the Python SQLite schema.
 * When {@code ownsDatabase} is false (shared session), {@link #close()} does not close the DB.
 */
public final class SqliteSongRepository implements SongRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SqliteDatabase database;
    private final boolean ownsDatabase;

    /**
     * Owns the database and closes it on {@link #close()} (convenient for single-repo tests).
     */
    public SqliteSongRepository(SqliteDatabase database) {
        this(database, true);
    }

    /**
     * @param ownsDatabase if true, {@link #close()} closes {@code database}; if false, the
     *                     caller (e.g. MainFrame) retains ownership of the shared connection
     */
    public SqliteSongRepository(SqliteDatabase database, boolean ownsDatabase) {
        this.database = Objects.requireNonNull(database, "database");
        this.ownsDatabase = ownsDatabase;
    }

    @Override
    public List<LibrarySong> listLibrarySongs(LibraryFilter filter) throws LibraryException {
        Objects.requireNonNull(filter, "filter");
        LibraryFilterQuery query = LibraryFilterQuery.build(filter);
        try (PreparedStatement statement = database.connection().prepareStatement(query.sql())) {
            query.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                List<LibrarySong> songs = new ArrayList<>();
                while (rs.next()) {
                    Integer rating = rs.getObject("rating") == null ? null : rs.getInt("rating");
                    Integer duration = rs.getObject("duration_seconds") == null
                            ? null
                            : rs.getInt("duration_seconds");
                    Long statusId = rs.getObject("status_id") == null ? null : rs.getLong("status_id");
                    songs.add(new LibrarySong(
                            rs.getLong("id"),
                            nullToEmpty(rs.getString("title")),
                            nullToEmpty(rs.getString("composers")),
                            blankToNull(rs.getString("transcriber")),
                            duration,
                            rs.getInt("part_count"),
                            rs.getString("parts"),
                            blankToNull(rs.getString("last_played_at")),
                            rs.getInt("total_plays"),
                            rating,
                            statusId,
                            rs.getString("status_name"),
                            rs.getString("status_color"),
                            blankToNull(rs.getString("notes")),
                            blankToNull(rs.getString("lyrics")),
                            rs.getInt("in_upcoming_set") != 0));
                }
                return List.copyOf(songs);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list library songs", ex);
        }
    }

    @Override
    public List<String> listUniqueTranscribers() throws LibraryException {
        String sql = """
                SELECT DISTINCT s.transcriber FROM Song s
                WHERE s.id IN (
                    SELECT DISTINCT song_id FROM SongFile
                    WHERE is_primary_library = 1 AND scan_excluded = 0
                )
                  AND s.transcriber IS NOT NULL AND s.transcriber != ''
                ORDER BY s.transcriber
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<String> transcribers = new ArrayList<>();
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.isBlank()) {
                    transcribers.add(value);
                }
            }
            return List.copyOf(transcribers);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list transcribers", ex);
        }
    }

    @Override
    public Optional<Path> resolvePrimaryAbcPath(long songId) throws LibraryException {
        String sql = """
                SELECT file_path FROM SongFile
                WHERE song_id = ?
                ORDER BY is_primary_library DESC, is_set_copy ASC, id ASC
                LIMIT 1
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String path = rs.getString(1);
                if (path == null || path.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(Path.of(path));
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to resolve ABC path for song " + songId, ex);
        }
    }

    @Override
    public Optional<LibrarySong> findSongById(long songId) throws LibraryException {
        // Prefer a direct query so set-only songs still resolve for playback.
        String sql = """
                SELECT s.id, s.title, s.composers, s.transcriber, s.duration_seconds,
                       json_array_length(COALESCE(s.parts, '[]')) AS part_count,
                       s.parts,
                       s.last_played_at, s.total_plays, s.rating, s.status_id,
                       st.name AS status_name, st.color AS status_color,
                       s.notes, s.lyrics,
                       EXISTS (
                           SELECT 1 FROM SetlistItem si JOIN Setlist sl ON sl.id = si.setlist_id
                           WHERE si.song_id = s.id AND sl.locked = 0
                       ) AS in_upcoming_set
                FROM Song s
                LEFT JOIN Status st ON st.id = s.status_id
                WHERE s.id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Integer rating = rs.getObject("rating") == null ? null : rs.getInt("rating");
                Integer duration = rs.getObject("duration_seconds") == null
                        ? null
                        : rs.getInt("duration_seconds");
                Long statusId = rs.getObject("status_id") == null ? null : rs.getLong("status_id");
                return Optional.of(new LibrarySong(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("title")),
                        nullToEmpty(rs.getString("composers")),
                        blankToNull(rs.getString("transcriber")),
                        duration,
                        rs.getInt("part_count"),
                        rs.getString("parts"),
                        blankToNull(rs.getString("last_played_at")),
                        rs.getInt("total_plays"),
                        rating,
                        statusId,
                        rs.getString("status_name"),
                        rs.getString("status_color"),
                        blankToNull(rs.getString("notes")),
                        blankToNull(rs.getString("lyrics")),
                        rs.getInt("in_upcoming_set") != 0));
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to find song " + songId, ex);
        }
    }

    @Override
    public Optional<SongDetailInfo> getSongForDetail(long songId) throws LibraryException {
        String sql = """
                SELECT s.id, s.title, s.composers, s.transcriber, s.duration_seconds, s.parts,
                       s.rating, s.status_id, s.notes, s.lyrics, st.name AS status_name,
                       (SELECT export_timestamp FROM SongFile WHERE song_id = s.id LIMIT 1) AS export_timestamp
                FROM Song s
                LEFT JOIN Status st ON st.id = s.status_id
                WHERE s.id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String partsJson = rs.getString("parts");
                int partCount = countParts(partsJson);
                Integer rating = rs.getObject("rating") == null ? null : rs.getInt("rating");
                Integer duration = rs.getObject("duration_seconds") == null
                        ? null
                        : rs.getInt("duration_seconds");
                Long statusId = rs.getObject("status_id") == null ? null : rs.getLong("status_id");
                return Optional.of(new SongDetailInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("title")),
                        nullToEmpty(rs.getString("composers")),
                        blankToNull(rs.getString("transcriber")),
                        duration,
                        partCount,
                        rating,
                        statusId,
                        rs.getString("status_name"),
                        blankToNull(rs.getString("notes")),
                        blankToNull(rs.getString("lyrics")),
                        blankToNull(rs.getString("export_timestamp"))));
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to load song detail " + songId, ex);
        }
    }

    @Override
    public void updateSongAppMetadata(long songId, SongAppMetadataUpdate update) throws LibraryException {
        Objects.requireNonNull(update, "update");
        if (update.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("UPDATE Song SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (update.updateRating()) {
            sql.append("rating = ?");
            args.add(update.rating());
            first = false;
        }
        if (update.updateStatusId()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("status_id = ?");
            args.add(update.statusId());
            first = false;
        }
        if (update.updateNotes()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("notes = ?");
            args.add(update.notes());
            first = false;
        }
        if (update.updateLyrics()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("lyrics = ?");
            args.add(update.lyrics());
        }
        sql.append(", updated_at = ? WHERE id = ?");
        args.add(SqliteTimestamps.now());
        args.add(songId);
        try (PreparedStatement statement = database.connection().prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                Object value = args.get(i);
                int index = i + 1;
                if (value == null) {
                    statement.setObject(index, null);
                } else if (value instanceof Integer integer) {
                    statement.setInt(index, integer);
                } else if (value instanceof Long longValue) {
                    statement.setLong(index, longValue);
                } else {
                    statement.setString(index, String.valueOf(value));
                }
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update song metadata " + songId, ex);
        }
    }

    @Override
    public void updateSongFromParsedFile(
            long songId,
            Path filePath,
            AbcFileMetadata metadata,
            String fileMtime,
            String fileHash) throws LibraryException {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(metadata, "metadata");
        String now = SqliteTimestamps.now();
        String partsJson = partsToJson(metadata);
        // Keep the path string as provided (usually the SongFile.file_path value).
        String pathString = filePath.toString();
        try {
            try (PreparedStatement updateSong = database.connection().prepareStatement(
                    """
                            UPDATE Song SET title = ?, composers = ?, duration_seconds = ?, transcriber = ?,
                               parts = ?, updated_at = ? WHERE id = ?
                            """)) {
                updateSong.setString(1, metadata.title());
                updateSong.setString(2, metadata.composers());
                if (metadata.durationSeconds() == null) {
                    updateSong.setNull(3, Types.INTEGER);
                } else {
                    updateSong.setInt(3, metadata.durationSeconds());
                }
                updateSong.setString(4, metadata.transcriber());
                updateSong.setString(5, partsJson);
                updateSong.setString(6, now);
                updateSong.setLong(7, songId);
                updateSong.executeUpdate();
            }
            Long fileId = null;
            try (PreparedStatement find = database.connection().prepareStatement(
                    "SELECT id FROM SongFile WHERE song_id = ? AND file_path = ?")) {
                find.setLong(1, songId);
                find.setString(2, pathString);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        fileId = rs.getLong(1);
                    }
                }
            }
            if (fileId == null) {
                try (PreparedStatement find = database.connection().prepareStatement(
                        """
                                SELECT id FROM SongFile
                                WHERE song_id = ?
                                ORDER BY is_primary_library DESC, is_set_copy ASC, id ASC
                                LIMIT 1
                                """)) {
                    find.setLong(1, songId);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) {
                            fileId = rs.getLong(1);
                        }
                    }
                }
            }
            if (fileId != null) {
                try (PreparedStatement updateFile = database.connection().prepareStatement(
                        """
                                UPDATE SongFile SET file_mtime = ?, file_hash = ?,
                                   export_timestamp = ?, updated_at = ? WHERE id = ?
                                """)) {
                    updateFile.setString(1, fileMtime);
                    updateFile.setString(2, fileHash);
                    updateFile.setString(3, metadata.exportTimestamp());
                    updateFile.setString(4, now);
                    updateFile.setLong(5, fileId);
                    updateFile.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to refresh song metadata from file for song " + songId, ex);
        }
    }

    @Override
    public List<SetlistRef> listUnlockedSetlistsContainingSong(long songId) throws LibraryException {
        String sql = """
                SELECT DISTINCT sl.id, sl.name
                FROM Setlist sl
                JOIN SetlistItem si ON si.setlist_id = sl.id
                WHERE si.song_id = ? AND sl.locked = 0
                ORDER BY sl.name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SetlistRef> setlists = new ArrayList<>();
                while (rs.next()) {
                    setlists.add(new SetlistRef(rs.getLong(1), nullToEmpty(rs.getString(2))));
                }
                return List.copyOf(setlists);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlists for song " + songId, ex);
        }
    }

    @Override
    public List<StatusInfo> listStatuses() throws LibraryException {
        String sql = "SELECT id, name, color, sort_order FROM Status ORDER BY sort_order, name";
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<StatusInfo> statuses = new ArrayList<>();
            while (rs.next()) {
                statuses.add(new StatusInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("name")),
                        nullToEmpty(rs.getString("color")),
                        rs.getInt("sort_order")));
            }
            return List.copyOf(statuses);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list statuses", ex);
        }
    }

    @Override
    public List<FolderRuleInfo> listFolderRules() throws LibraryException {
        String sql = """
                SELECT id, path, enabled, include_in_export
                FROM FolderRule
                WHERE rule_type = 'exclude'
                ORDER BY path
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<FolderRuleInfo> rules = new ArrayList<>();
            while (rs.next()) {
                rules.add(new FolderRuleInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("path")),
                        rs.getInt("enabled") != 0,
                        rs.getInt("include_in_export") != 0));
            }
            return List.copyOf(rules);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list folder rules", ex);
        }
    }

    @Override
    public List<AccountTargetInfo> listAccountTargets() throws LibraryException {
        String sql = """
                SELECT id, account_name, plugin_data_path, enabled
                FROM AccountTarget
                ORDER BY account_name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<AccountTargetInfo> targets = new ArrayList<>();
            while (rs.next()) {
                targets.add(new AccountTargetInfo(
                        rs.getLong("id"),
                        nullToEmpty(rs.getString("account_name")),
                        nullToEmpty(rs.getString("plugin_data_path")),
                        rs.getInt("enabled") != 0));
            }
            return List.copyOf(targets);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list account targets", ex);
        }
    }

    @Override
    public void close() {
        if (ownsDatabase) {
            database.close();
        }
    }

    Connection connection() {
        return database.connection();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int countParts(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return 0;
        }
        try {
            return JSON.readTree(partsJson).size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String partsToJson(AbcFileMetadata metadata) {
        ArrayNode array = JSON.createArrayNode();
        for (AbcPartMetadata part : metadata.parts()) {
            ObjectNode node = array.addObject();
            node.put("part_number", part.partNumber());
            if (part.partName() == null) {
                node.putNull("part_name");
            } else {
                node.put("part_name", part.partName());
            }
            if (part.instrumentId() == null) {
                node.putNull("instrument_id");
            } else {
                node.put("instrument_id", part.instrumentId());
            }
            if (part.titleFromT() == null) {
                node.putNull("title_from_t");
            } else {
                node.put("title_from_t", part.titleFromT());
            }
        }
        return array.toString();
    }
}

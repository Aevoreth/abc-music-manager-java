package com.aevoreth.abcmm.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;

/**
 * Read-only song/library queries against the Python SQLite schema.
 * When {@code ownsDatabase} is false (shared session), {@link #close()} does not close the DB.
 */
public final class SqliteSongRepository implements SongRepository {

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
}

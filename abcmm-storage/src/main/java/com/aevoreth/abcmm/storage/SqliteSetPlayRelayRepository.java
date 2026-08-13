package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setplay.SetPlayPublishedSessionInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayRelayInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayRelayRepository;

/**
 * JDBC implementation of {@link SetPlayRelayRepository}. Does not close the shared database.
 */
public final class SqliteSetPlayRelayRepository implements SetPlayRelayRepository {

    private final SqliteDatabase database;

    public SqliteSetPlayRelayRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<SetPlayRelayInfo> listRelays() throws LibraryException {
        String sql = """
                SELECT id, name, url, token, retention_days, sort_order
                FROM SetPlayRelay
                ORDER BY sort_order, name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<SetPlayRelayInfo> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRelay(rs));
            }
            return List.copyOf(out);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list Set Play relays", ex);
        }
    }

    @Override
    public Optional<SetPlayRelayInfo> findRelay(long id) throws LibraryException {
        String sql = """
                SELECT id, name, url, token, retention_days, sort_order
                FROM SetPlayRelay WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRelay(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to load Set Play relay", ex);
        }
    }

    @Override
    public long addRelay(String name, String url, String token, int retentionDays) throws LibraryException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(url, "url");
        String now = SqliteTimestamps.now();
        int sort = nextSortOrder();
        String sql = """
                INSERT INTO SetPlayRelay (name, url, token, retention_days, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name.strip());
            statement.setString(2, url.strip().replaceAll("/+$", ""));
            statement.setString(3, blankToNull(token));
            statement.setInt(4, clampRetention(retentionDays));
            statement.setInt(5, sort);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add Set Play relay", ex);
        }
    }

    @Override
    public void updateRelay(long id, String name, String url, String token, int retentionDays)
            throws LibraryException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(url, "url");
        String sql = """
                UPDATE SetPlayRelay
                SET name = ?, url = ?, token = ?, retention_days = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            statement.setString(2, url.strip().replaceAll("/+$", ""));
            statement.setString(3, blankToNull(token));
            statement.setInt(4, clampRetention(retentionDays));
            statement.setString(5, SqliteTimestamps.now());
            statement.setLong(6, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update Set Play relay", ex);
        }
    }

    @Override
    public void deleteRelay(long id) throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM SetPlayRelay WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete Set Play relay", ex);
        }
    }

    @Override
    public boolean copyRelaysFromPreferencesIfEmpty(Preferences preferences) throws LibraryException {
        Objects.requireNonNull(preferences, "preferences");
        if (!listRelays().isEmpty()) {
            preferences.setSetPlayRelays(List.of());
            return false;
        }
        List<Map<String, Object>> pending = preferences.setPlayRelays();
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        String selected = preferences.setPlaySelectedRelayId();
        String newSelected = null;
        for (Map<String, Object> raw : pending) {
            if (raw == null) {
                continue;
            }
            String name = String.valueOf(raw.getOrDefault("name", "")).strip();
            String url = String.valueOf(raw.getOrDefault("url", "")).strip();
            if (name.isEmpty() || url.isEmpty()) {
                continue;
            }
            Object tokenObj = raw.get("token");
            String token = tokenObj == null ? null : String.valueOf(tokenObj);
            long id = addRelay(name, url, token, SetPlayRelayInfo.DEFAULT_RETENTION_DAYS);
            Object oldId = raw.get("id");
            if (selected != null && oldId != null && selected.equals(String.valueOf(oldId))) {
                newSelected = String.valueOf(id);
            }
            if (newSelected == null) {
                newSelected = String.valueOf(id);
            }
        }
        preferences.setSetPlayRelays(List.of());
        if (newSelected != null) {
            preferences.setSetPlaySelectedRelayId(newSelected);
        }
        return true;
    }

    @Override
    public List<SetPlayPublishedSessionInfo> listPublishedSessions(long relayId) throws LibraryException {
        String sql = """
                SELECT id, relay_id, code, name, passphrase, setlist_id
                FROM SetPlayPublishedSession
                WHERE relay_id = ?
                ORDER BY updated_at DESC, name
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, relayId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SetPlayPublishedSessionInfo> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapSession(rs));
                }
                return List.copyOf(out);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list published sessions", ex);
        }
    }

    @Override
    public Optional<SetPlayPublishedSessionInfo> findPublishedSession(long relayId, String code)
            throws LibraryException {
        String sql = """
                SELECT id, relay_id, code, name, passphrase, setlist_id
                FROM SetPlayPublishedSession
                WHERE relay_id = ? AND UPPER(code) = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, relayId);
            statement.setString(2, code == null ? "" : code.strip().toUpperCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to load published session", ex);
        }
    }

    @Override
    public long upsertPublishedSession(
            long relayId, String code, String name, String passphrase, Long setlistId)
            throws LibraryException {
        Optional<SetPlayPublishedSessionInfo> existing = findPublishedSession(relayId, code);
        String now = SqliteTimestamps.now();
        String normalized = code == null ? "" : code.strip().toUpperCase();
        if (existing.isPresent()) {
            String sql = """
                    UPDATE SetPlayPublishedSession
                    SET name = ?, passphrase = COALESCE(?, passphrase), setlist_id = ?, updated_at = ?
                    WHERE id = ?
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
                statement.setString(1, name == null ? "" : name.strip());
                statement.setString(2, blankToNull(passphrase));
                if (setlistId == null) {
                    statement.setObject(3, null);
                } else {
                    statement.setLong(3, setlistId);
                }
                statement.setString(4, now);
                statement.setLong(5, existing.get().id());
                statement.executeUpdate();
                return existing.get().id();
            } catch (SQLException ex) {
                throw new LibraryException("Failed to update published session", ex);
            }
        }
        String sql = """
                INSERT INTO SetPlayPublishedSession
                    (relay_id, code, name, passphrase, setlist_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, relayId);
            statement.setString(2, normalized);
            statement.setString(3, name == null ? "" : name.strip());
            statement.setString(4, blankToNull(passphrase));
            if (setlistId == null) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, setlistId);
            }
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to save published session", ex);
        }
    }

    @Override
    public void updatePublishedSessionName(long relayId, String code, String name) throws LibraryException {
        String sql = """
                UPDATE SetPlayPublishedSession
                SET name = ?, updated_at = ?
                WHERE relay_id = ? AND UPPER(code) = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name == null ? "" : name.strip());
            statement.setString(2, SqliteTimestamps.now());
            statement.setLong(3, relayId);
            statement.setString(4, code == null ? "" : code.strip().toUpperCase());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to rename published session", ex);
        }
    }

    @Override
    public void deletePublishedSession(long relayId, String code) throws LibraryException {
        String sql = "DELETE FROM SetPlayPublishedSession WHERE relay_id = ? AND UPPER(code) = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, relayId);
            statement.setString(2, code == null ? "" : code.strip().toUpperCase());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete published session", ex);
        }
    }

    private int nextSortOrder() throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM SetPlayRelay");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw new LibraryException("Failed to assign relay sort order", ex);
        }
    }

    private static SetPlayRelayInfo mapRelay(ResultSet rs) throws SQLException {
        return new SetPlayRelayInfo(
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                nullToEmpty(rs.getString("url")),
                rs.getString("token"),
                rs.getInt("retention_days"),
                rs.getInt("sort_order"));
    }

    private static SetPlayPublishedSessionInfo mapSession(ResultSet rs) throws SQLException {
        long setlist = rs.getLong("setlist_id");
        Long setlistId = rs.wasNull() ? null : setlist;
        return new SetPlayPublishedSessionInfo(
                rs.getLong("id"),
                rs.getLong("relay_id"),
                nullToEmpty(rs.getString("code")),
                nullToEmpty(rs.getString("name")),
                rs.getString("passphrase"),
                setlistId);
    }

    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        throw new SQLException("No generated id");
    }

    private static int clampRetention(int days) {
        return Math.max(1, Math.min(365, days));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

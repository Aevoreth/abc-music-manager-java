package com.aevoreth.abcmm.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SettingsRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;

/**
 * JDBC implementation of {@link SettingsRepository}. Does not close the shared database.
 */
public final class SqliteSettingsRepository implements SettingsRepository {

    private final SqliteDatabase database;

    public SqliteSettingsRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
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
    public long addStatus(String name, String color, Integer sortOrder) throws LibraryException {
        Objects.requireNonNull(name, "name");
        String now = SqliteTimestamps.now();
        String sql = """
                INSERT INTO Status (name, color, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name.strip());
            statement.setString(2, color);
            if (sortOrder == null) {
                statement.setObject(3, null);
            } else {
                statement.setInt(3, sortOrder);
            }
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add status", ex);
        }
    }

    @Override
    public void updateStatus(long id, String name, String color, Integer sortOrder)
            throws LibraryException {
        Objects.requireNonNull(name, "name");
        String sql = """
                UPDATE Status SET name = ?, color = ?, sort_order = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, name.strip());
            statement.setString(2, color);
            if (sortOrder == null) {
                statement.setObject(3, null);
            } else {
                statement.setInt(3, sortOrder);
            }
            statement.setString(4, SqliteTimestamps.now());
            statement.setLong(5, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update status", ex);
        }
    }

    @Override
    public void deleteStatus(long id) throws LibraryException {
        try {
            try (PreparedStatement clear = database.connection().prepareStatement(
                    "UPDATE Song SET status_id = NULL WHERE status_id = ?")) {
                clear.setLong(1, id);
                clear.executeUpdate();
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM Status WHERE id = ?")) {
                delete.setLong(1, id);
                delete.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete status", ex);
        }
    }

    @Override
    public void reorderStatuses(List<Long> idOrder) throws LibraryException {
        Objects.requireNonNull(idOrder, "idOrder");
        String now = SqliteTimestamps.now();
        String sql = "UPDATE Status SET sort_order = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (int i = 0; i < idOrder.size(); i++) {
                statement.setInt(1, i);
                statement.setString(2, now);
                statement.setLong(3, idOrder.get(i));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to reorder statuses", ex);
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
    public long addFolderRule(String path, boolean enabled, boolean includeInExport)
            throws LibraryException {
        Objects.requireNonNull(path, "path");
        String now = SqliteTimestamps.now();
        String sql = """
                INSERT INTO FolderRule (rule_type, path, enabled, include_in_export, created_at, updated_at)
                VALUES ('exclude', ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, path.strip());
            statement.setInt(2, enabled ? 1 : 0);
            statement.setInt(3, includeInExport ? 1 : 0);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add folder rule", ex);
        }
    }

    @Override
    public void updateFolderRule(long id, String path, boolean enabled, boolean includeInExport)
            throws LibraryException {
        Objects.requireNonNull(path, "path");
        String sql = """
                UPDATE FolderRule SET path = ?, enabled = ?, include_in_export = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, path.strip());
            statement.setInt(2, enabled ? 1 : 0);
            statement.setInt(3, includeInExport ? 1 : 0);
            statement.setString(4, SqliteTimestamps.now());
            statement.setLong(5, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update folder rule", ex);
        }
    }

    @Override
    public void deleteFolderRule(long id) throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM FolderRule WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete folder rule", ex);
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
    public long addAccountTarget(String accountName, String pluginDataPath, boolean enabled)
            throws LibraryException {
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(pluginDataPath, "pluginDataPath");
        String now = SqliteTimestamps.now();
        String sql = """
                INSERT INTO AccountTarget (account_name, plugin_data_path, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, accountName.strip());
            statement.setString(2, pluginDataPath.strip());
            statement.setInt(3, enabled ? 1 : 0);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
            return generatedId(statement);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to add account target", ex);
        }
    }

    @Override
    public void updateAccountTarget(
            long id, String accountName, String pluginDataPath, boolean enabled)
            throws LibraryException {
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(pluginDataPath, "pluginDataPath");
        String sql = """
                UPDATE AccountTarget
                SET account_name = ?, plugin_data_path = ?, enabled = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, accountName.strip());
            statement.setString(2, pluginDataPath.strip());
            statement.setInt(3, enabled ? 1 : 0);
            statement.setString(4, SqliteTimestamps.now());
            statement.setLong(5, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update account target", ex);
        }
    }

    @Override
    public void deleteAccountTarget(long id) throws LibraryException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM AccountTarget WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new LibraryException("Failed to delete account target", ex);
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
}

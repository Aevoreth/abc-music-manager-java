package com.aevoreth.abcmm.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Connection to a Python-compatible SQLite database (schema v12).
 * Supports read-only open of an existing DB and read-write open-or-create with migrations.
 */
public final class SqliteDatabase implements AutoCloseable {

    public static final int REQUIRED_SCHEMA_VERSION = SchemaMigrator.CURRENT_SCHEMA_VERSION;

    private final Path databasePath;
    private final Connection connection;
    private final boolean readOnly;

    private SqliteDatabase(Path databasePath, Connection connection, boolean readOnly) {
        this.databasePath = databasePath;
        this.connection = connection;
        this.readOnly = readOnly;
    }

    public static SqliteDatabase openReadOnly(Path databasePath) throws LibraryException {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolute = databasePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new LibraryException(
                    "Database not found: " + absolute
                            + " (expected "
                            + DataPaths.DATABASE_FILE_NAME
                            + " under "
                            + DataPaths.DEFAULT_DIR_NAME
                            + " or $"
                            + DataPaths.DATA_ENV_VAR
                            + ")");
        }
        String url = "jdbc:sqlite:" + absolute.toUri() + "?mode=ro";
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(url);
            SqliteDatabase db = new SqliteDatabase(absolute, connection, true);
            db.verifySchemaVersionExact();
            return db;
        } catch (SQLException ex) {
            closeQuietly(connection);
            throw new LibraryException(
                    "Failed to open database: " + absolute + " (" + ex.getMessage() + ")", ex);
        } catch (LibraryException ex) {
            closeQuietly(connection);
            throw ex;
        }
    }

    /**
     * Opens an existing database for read-write use, or creates and migrates a new one
     * at {@code databasePath} to schema version {@link #REQUIRED_SCHEMA_VERSION}.
     */
    public static SqliteDatabase openOrCreate(Path databasePath) throws LibraryException {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolute = databasePath.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String url = "jdbc:sqlite:" + absolute.toUri();
            Connection connection = DriverManager.getConnection(url);
            try {
                connection.setAutoCommit(false);
                try (Statement pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA foreign_keys = ON");
                }
                SchemaMigrator.initDatabase(connection);
                connection.commit();
                connection.setAutoCommit(true);
                SqliteDatabase db = new SqliteDatabase(absolute, connection, false);
                db.verifySchemaVersionExact();
                return db;
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // best-effort
                }
                closeQuietly(connection);
                if (ex instanceof SQLException sqlEx) {
                    throw new LibraryException(
                            "Failed to open or create database: " + absolute
                                    + " (" + sqlEx.getMessage() + ")",
                            sqlEx);
                }
                throw ex;
            }
        } catch (LibraryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LibraryException(
                    "Failed to open or create database: " + absolute + " (" + ex.getMessage() + ")",
                    ex);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best-effort
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    public Connection connection() {
        return connection;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    private void verifySchemaVersionExact() throws LibraryException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM schema_version LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                throw new LibraryException("Database has no schema_version row: " + databasePath);
            }
            int version = rs.getInt(1);
            if (version != REQUIRED_SCHEMA_VERSION) {
                throw new LibraryException(
                        "Unsupported schema version " + version
                                + " (expected " + REQUIRED_SCHEMA_VERSION + "): " + databasePath);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read schema_version: " + databasePath, ex);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best-effort
        }
    }
}

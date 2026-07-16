package com.aevoreth.abcmm.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;

/**
 * Builds a minimal schema-v12 SQLite fixture for tests.
 */
final class FixtureDatabases {

    private FixtureDatabases() {
    }

    static Path createLibraryFixture(Path databaseFile) throws Exception {
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_version (version INTEGER PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (12)");

            statement.executeUpdate("""
                    CREATE TABLE Status (
                      id INTEGER PRIMARY KEY,
                      name TEXT NOT NULL UNIQUE,
                      color TEXT,
                      sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate(
                    "INSERT INTO Status (id, name, color, sort_order) VALUES (1, 'New', '#0044FF', 0)");
            statement.executeUpdate(
                    "INSERT INTO Status (id, name, color, sort_order) VALUES (2, 'Ready', '#00AA00', 1)");

            statement.executeUpdate("""
                    CREATE TABLE Song (
                      id INTEGER PRIMARY KEY,
                      title TEXT NOT NULL,
                      composers TEXT NOT NULL,
                      duration_seconds INTEGER,
                      transcriber TEXT,
                      rating INTEGER,
                      status_id INTEGER,
                      notes TEXT,
                      lyrics TEXT,
                      last_played_at TEXT,
                      total_plays INTEGER DEFAULT 0,
                      parts TEXT,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE SongFile (
                      id INTEGER PRIMARY KEY,
                      song_id INTEGER NOT NULL,
                      file_path TEXT UNIQUE NOT NULL,
                      file_mtime TEXT,
                      file_hash TEXT,
                      export_timestamp TEXT,
                      is_primary_library INTEGER DEFAULT 1,
                      is_set_copy INTEGER DEFAULT 0,
                      scan_excluded INTEGER DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE Setlist (
                      id INTEGER PRIMARY KEY,
                      name TEXT NOT NULL,
                      locked INTEGER NOT NULL DEFAULT 0,
                      sort_order INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE SetlistItem (
                      id INTEGER PRIMARY KEY,
                      setlist_id INTEGER NOT NULL,
                      song_id INTEGER NOT NULL,
                      position INTEGER NOT NULL,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE FolderRule (
                      id INTEGER PRIMARY KEY,
                      rule_type TEXT NOT NULL,
                      path TEXT NOT NULL,
                      enabled INTEGER NOT NULL DEFAULT 1,
                      include_in_export INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE AccountTarget (
                      id INTEGER PRIMARY KEY,
                      account_name TEXT NOT NULL,
                      plugin_data_path TEXT NOT NULL,
                      enabled INTEGER NOT NULL DEFAULT 1,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE PlayLog (
                      id INTEGER PRIMARY KEY,
                      song_id INTEGER NOT NULL,
                      played_at TEXT NOT NULL,
                      context_setlist_id INTEGER,
                      context_note TEXT,
                      created_at TEXT NOT NULL
                    )
                    """);

            String now = Instant.parse("2024-01-01T00:00:00Z").toString();
            insertSong(statement, 1, "Alpha March", "Composer A", "Ada", 180, 1, 3,
                    "[{\"part_number\":1,\"part_name\":\"Melody\"},{\"part_number\":2},{\"part_number\":3}]",
                    now, null);
            insertSong(statement, 2, "Beta Waltz", "Composer B", "Ben", 90, 2, 5,
                    "[{\"part_number\":1}]", now, now);
            insertSong(statement, 3, "Hidden Excluded", "Composer C", "Ada", 60, 1, 1,
                    "[{\"part_number\":1}]", now, null);
            insertSong(statement, 4, "Set Copy Only", "Composer D", "Ben", 120, 2, 2,
                    "[{\"part_number\":1},{\"part_number\":2}]", now, null);

            insertSongFile(statement, 1, 1, "/music/alpha.abc", 1, 0, 0, now);
            insertSongFile(statement, 2, 2, "/music/beta.abc", 1, 0, 0, now);
            insertSongFile(statement, 3, 3, "/music/excluded.abc", 1, 0, 1, now);
            insertSongFile(statement, 4, 4, "/music/sets/copy.abc", 0, 1, 0, now);

            statement.executeUpdate(
                    "INSERT INTO Setlist (id, name, locked, sort_order, created_at, updated_at) "
                            + "VALUES (1, 'Gig', 0, 0, '" + now + "', '" + now + "')");
            statement.executeUpdate(
                    "INSERT INTO SetlistItem (id, setlist_id, song_id, position, created_at, updated_at) "
                            + "VALUES (1, 1, 1, 0, '" + now + "', '" + now + "')");

            statement.executeUpdate(
                    "INSERT INTO FolderRule (id, rule_type, path, enabled, include_in_export, created_at, updated_at) "
                            + "VALUES (1, 'exclude', 'Archive', 1, 0, '" + now + "', '" + now + "')");
            statement.executeUpdate(
                    "INSERT INTO AccountTarget (id, account_name, plugin_data_path, enabled, created_at, updated_at) "
                            + "VALUES (1, 'Main', '/PluginData/Main', 1, '" + now + "', '" + now + "')");
        }
        return databaseFile;
    }

    static Path createWrongVersionFixture(Path databaseFile) throws Exception {
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_version (version INTEGER PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (11)");
        }
        return databaseFile;
    }

    private static void insertSong(
            Statement statement,
            int id,
            String title,
            String composers,
            String transcriber,
            int durationSeconds,
            int statusId,
            int rating,
            String partsJson,
            String now,
            String lastPlayed) throws Exception {
        String lastPlayedSql = lastPlayed == null ? "NULL" : "'" + lastPlayed + "'";
        statement.executeUpdate("""
                INSERT INTO Song (id, title, composers, duration_seconds, transcriber, rating, status_id, parts,
                                  last_played_at, total_plays, created_at, updated_at)
                VALUES (%d, '%s', '%s', %d, '%s', %d, %d, '%s', %s, 0, '%s', '%s')
                """.formatted(
                id, title, composers, durationSeconds, transcriber, rating, statusId, partsJson,
                lastPlayedSql, now, now));
    }

    private static void insertSongFile(
            Statement statement,
            int id,
            int songId,
            String path,
            int primary,
            int setCopy,
            int excluded,
            String now) throws Exception {
        statement.executeUpdate("""
                INSERT INTO SongFile (id, song_id, file_path, is_primary_library, is_set_copy,
                                     scan_excluded, created_at, updated_at)
                VALUES (%d, %d, '%s', %d, %d, %d, '%s', '%s')
                """.formatted(id, songId, path, primary, setCopy, excluded, now, now));
    }
}

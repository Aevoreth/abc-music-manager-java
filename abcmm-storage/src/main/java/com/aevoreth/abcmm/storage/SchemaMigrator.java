package com.aevoreth.abcmm.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates and migrates the Python-compatible SQLite schema through version 12.
 */
final class SchemaMigrator {

    static final int CURRENT_SCHEMA_VERSION = 12;

    /** 24 LOTRO instruments for Players tab (same order as Python). */
    static final List<String> PLAYER_INSTRUMENTS = List.of(
            "Basic Fiddle",
            "Student's Fiddle",
            "Bardic Fiddle",
            "Lonely Mountain Fiddle",
            "Sprightly Fiddle",
            "Traveler's Trusty Fiddle",
            "Basic Bassoon",
            "Lonely Mountain Bassoon",
            "Brusque Bassoon",
            "Basic Flute",
            "Basic Horn",
            "Basic Clarinet",
            "Basic Bagpipe",
            "Basic Pibgorn",
            "Basic Harp",
            "Misty Mountain Harp",
            "Basic Lute",
            "Lute of Ages",
            "Basic Theorbo",
            "Basic Drum",
            "Basic Cowbell",
            "Moor Cowbell",
            "Jaunty Hand-Knells");

    private static final ObjectMapper JSON = new ObjectMapper();

    private SchemaMigrator() {
    }

    static void initDatabase(Connection connection) throws SQLException {
        createSchema(connection);
        runMigrations(connection);
        seedDefaults(connection);
        seedPlayerInstruments(connection);
        mergeKnownInstrumentDuplicates(connection);
        backfillSongStatusIds(connection);
    }

    static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Instrument (
                        id INTEGER PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        alternative_names TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Status (
                        id INTEGER PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        color TEXT,
                        sort_order INTEGER,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Song (
                        id INTEGER PRIMARY KEY,
                        title TEXT NOT NULL,
                        composers TEXT NOT NULL,
                        duration_seconds INTEGER,
                        transcriber TEXT,
                        rating INTEGER,
                        status_id INTEGER REFERENCES Status(id),
                        notes TEXT,
                        lyrics TEXT,
                        last_played_at TEXT,
                        total_plays INTEGER NOT NULL DEFAULT 0,
                        parts TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SongFile (
                        id INTEGER PRIMARY KEY,
                        song_id INTEGER REFERENCES Song(id),
                        file_path TEXT UNIQUE NOT NULL,
                        file_mtime TEXT,
                        file_hash TEXT,
                        export_timestamp TEXT,
                        is_primary_library INTEGER NOT NULL DEFAULT 1,
                        is_set_copy INTEGER NOT NULL DEFAULT 0,
                        scan_excluded INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Band (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Player (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS PlayerInstrument (
                        id INTEGER PRIMARY KEY,
                        player_id INTEGER NOT NULL REFERENCES Player(id),
                        instrument_id INTEGER NOT NULL REFERENCES Instrument(id),
                        has_instrument INTEGER NOT NULL DEFAULT 1,
                        has_proficiency INTEGER NOT NULL DEFAULT 0,
                        notes TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BandMember (
                        band_id INTEGER NOT NULL REFERENCES Band(id),
                        player_id INTEGER NOT NULL REFERENCES Player(id),
                        PRIMARY KEY (band_id, player_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BandLayout (
                        id INTEGER PRIMARY KEY,
                        band_id INTEGER NOT NULL REFERENCES Band(id),
                        name TEXT NOT NULL,
                        export_column_order TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BandLayoutSlot (
                        id INTEGER PRIMARY KEY,
                        band_layout_id INTEGER NOT NULL REFERENCES BandLayout(id),
                        player_id INTEGER NOT NULL REFERENCES Player(id),
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        width_units INTEGER NOT NULL DEFAULT 7,
                        height_units INTEGER NOT NULL DEFAULT 5,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SongLayout (
                        id INTEGER PRIMARY KEY,
                        song_id INTEGER NOT NULL REFERENCES Song(id),
                        band_layout_id INTEGER NOT NULL REFERENCES BandLayout(id),
                        name TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SongLayoutAssignment (
                        id INTEGER PRIMARY KEY,
                        song_layout_id INTEGER NOT NULL REFERENCES SongLayout(id),
                        player_id INTEGER NOT NULL REFERENCES Player(id),
                        part_number INTEGER,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SetlistFolder (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Setlist (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        band_layout_id INTEGER REFERENCES BandLayout(id),
                        folder_id INTEGER REFERENCES SetlistFolder(id),
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        locked INTEGER NOT NULL DEFAULT 0,
                        default_change_duration_seconds INTEGER,
                        export_naming_rules TEXT,
                        notes TEXT,
                        set_date TEXT,
                        set_time TEXT,
                        target_duration_seconds INTEGER,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SetlistItem (
                        id INTEGER PRIMARY KEY,
                        setlist_id INTEGER NOT NULL REFERENCES Setlist(id),
                        song_id INTEGER NOT NULL REFERENCES Song(id),
                        position INTEGER NOT NULL,
                        override_change_duration_seconds INTEGER,
                        song_layout_id INTEGER REFERENCES SongLayout(id),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS PlayLog (
                        id INTEGER PRIMARY KEY,
                        song_id INTEGER NOT NULL REFERENCES Song(id),
                        played_at TEXT NOT NULL,
                        context_setlist_id INTEGER REFERENCES Setlist(id),
                        context_note TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SetlistBandAssignment (
                        id INTEGER PRIMARY KEY,
                        setlist_item_id INTEGER NOT NULL REFERENCES SetlistItem(id),
                        player_id INTEGER NOT NULL REFERENCES Player(id),
                        part_number INTEGER,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS AccountTarget (
                        id INTEGER PRIMARY KEY,
                        account_name TEXT NOT NULL,
                        plugin_data_path TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS FolderRule (
                        id INTEGER PRIMARY KEY,
                        rule_type TEXT NOT NULL,
                        path TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        include_in_export INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY
                    )
                    """);

            statement.execute("CREATE INDEX IF NOT EXISTS idx_songfile_song_id ON SongFile(song_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_song_status_id ON Song(status_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_playlog_song_id ON PlayLog(song_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_playlog_played_at ON PlayLog(played_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_setlistitem_setlist_id ON SetlistItem(setlist_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_folderrule_rule_type ON FolderRule(rule_type)");
        }
    }

    static void runMigrations(Connection connection) throws SQLException {
        int current = getSchemaVersion(connection);
        if (current > CURRENT_SCHEMA_VERSION) {
            throw new SQLException(
                    "Unsupported schema version " + current
                            + " (expected " + CURRENT_SCHEMA_VERSION + " or lower)");
        }
        if (current < 1) {
            migrateStatusDropIsActive(connection);
            setSchemaVersion(connection, 1);
            current = 1;
        }
        if (current < 2) {
            migrateFolderRuleIncludeInExport(connection);
            setSchemaVersion(connection, 2);
            current = 2;
        }
        if (current < 3) {
            migrateBandNotes(connection);
            setSchemaVersion(connection, 3);
            current = 3;
        }
        if (current < 4) {
            migrateBandSortOrder(connection);
            setSchemaVersion(connection, 4);
            current = 4;
        }
        if (current < 5) {
            migrateSetlistNotes(connection);
            setSchemaVersion(connection, 5);
            current = 5;
        }
        if (current < 6) {
            migrateSetlistDateTimeTarget(connection);
            setSchemaVersion(connection, 6);
            current = 6;
        }
        if (current < 7) {
            migrateSetlistFolders(connection);
            setSchemaVersion(connection, 7);
            current = 7;
        }
        if (current < 8) {
            migrateBandLayoutExportColumnOrder(connection);
            setSchemaVersion(connection, 8);
            current = 8;
        }
        if (current < 9) {
            migrateBandLayoutSortOrder(connection);
            setSchemaVersion(connection, 9);
            current = 9;
        }
        if (current < 10) {
            migratePlayerLevelClass(connection);
            setSchemaVersion(connection, 10);
            current = 10;
        }
        if (current < 11) {
            migrateSongLastLayout(connection);
            setSchemaVersion(connection, 11);
            current = 11;
        }
        if (current < 12) {
            mergeInstrumentInto(connection, "Student Fiddle", "Student's Fiddle");
            setSchemaVersion(connection, 12);
        }
    }

    static void seedDefaults(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Status")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        String now = Instant.now().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO Status (id, name, color, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insertDefaults(insert, 1, "New", "#0044FF", 0, now);
            insertDefaults(insert, 2, "Testing", "#FF8800", 1, now);
            insertDefaults(insert, 3, "Ready", "#00FF00", 2, now);
        }
        connection.commit();
    }

    private static void insertDefaults(
            PreparedStatement insert, int id, String name, String color, int sortOrder, String now)
            throws SQLException {
        insert.setInt(1, id);
        insert.setString(2, name);
        insert.setString(3, color);
        insert.setInt(4, sortOrder);
        insert.setString(5, now);
        insert.setString(6, now);
        insert.executeUpdate();
    }

    static void seedPlayerInstruments(Connection connection) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM Instrument WHERE name = ?");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO Instrument (name, alternative_names, created_at, updated_at) VALUES (?, NULL, ?, ?)")) {
            for (String name : PLAYER_INSTRUMENTS) {
                select.setString(1, name);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        continue;
                    }
                }
                insert.setString(1, name);
                insert.setString(2, now);
                insert.setString(3, now);
                insert.executeUpdate();
            }
        }
        connection.commit();
    }

    /**
     * Collapse ABC/scan spelling variants into the v12 catalog names.
     * Idempotent; safe to run on every open.
     */
    static void mergeKnownInstrumentDuplicates(Connection connection) throws SQLException {
        // Case-only duplicates of catalog names (e.g. Maestro "Jaunty Hand-knells").
        for (String canonical : PLAYER_INSTRUMENTS) {
            List<String> variants = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name FROM Instrument WHERE LOWER(name) = LOWER(?) AND name != ?")) {
                select.setString(1, canonical);
                select.setString(2, canonical);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        variants.add(rs.getString(1));
                    }
                }
            }
            for (String variant : variants) {
                mergeInstrumentInto(connection, variant, canonical);
            }
        }
        // Distinct spelling kept for ABC↔DB parity (see docs/SCHEMA_ISSUES.md).
        mergeInstrumentInto(connection, "Traveller's Trusty Fiddle", "Traveler's Trusty Fiddle");
    }

    static void backfillSongStatusIds(Connection connection) throws SQLException {
        Long defaultId = null;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id FROM Status ORDER BY sort_order ASC, id ASC LIMIT 1")) {
            if (rs.next()) {
                defaultId = rs.getLong(1);
            }
        }
        if (defaultId == null) {
            return;
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE Song SET status_id = ? WHERE status_id IS NULL")) {
            update.setLong(1, defaultId);
            update.executeUpdate();
        }
        connection.commit();
    }

    static int getSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            return 0;
        }
    }

    static void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
        connection.commit();
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void migrateStatusDropIsActive(Connection connection) throws SQLException {
        if (!hasColumn(connection, "Status", "is_active")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE Status_new (
                        id INTEGER PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        color TEXT,
                        sort_order INTEGER,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute(
                    "INSERT INTO Status_new (id, name, color, sort_order, created_at, updated_at)"
                            + " SELECT id, name, color, sort_order, created_at, updated_at FROM Status");
            statement.execute("DROP TABLE Status");
            statement.execute("ALTER TABLE Status_new RENAME TO Status");
        }
        connection.commit();
    }

    private static void migrateFolderRuleIncludeInExport(Connection connection) throws SQLException {
        if (hasColumn(connection, "FolderRule", "include_in_export")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE FolderRule ADD COLUMN include_in_export INTEGER NOT NULL DEFAULT 0");
        }
        connection.commit();
    }

    private static void migrateBandNotes(Connection connection) throws SQLException {
        if (hasColumn(connection, "Band", "notes")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Band ADD COLUMN notes TEXT");
        }
        connection.commit();
    }

    private static void migrateBandSortOrder(Connection connection) throws SQLException {
        if (hasColumn(connection, "Band", "sort_order")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Band ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            statement.execute(
                    "UPDATE Band SET sort_order = (SELECT COUNT(*) FROM Band b2 WHERE b2.id < Band.id)");
        }
        connection.commit();
    }

    private static void migrateSetlistNotes(Connection connection) throws SQLException {
        if (hasColumn(connection, "Setlist", "notes")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Setlist ADD COLUMN notes TEXT");
        }
        connection.commit();
    }

    private static void migrateSetlistDateTimeTarget(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "Setlist", "set_date")) {
                statement.execute("ALTER TABLE Setlist ADD COLUMN set_date TEXT");
            }
            if (!hasColumn(connection, "Setlist", "set_time")) {
                statement.execute("ALTER TABLE Setlist ADD COLUMN set_time TEXT");
            }
            if (!hasColumn(connection, "Setlist", "target_duration_seconds")) {
                statement.execute("ALTER TABLE Setlist ADD COLUMN target_duration_seconds INTEGER");
            }
        }
        connection.commit();
    }

    private static void migrateSetlistFolders(Connection connection) throws SQLException {
        if (!tableExists(connection, "SetlistFolder")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE SetlistFolder (
                            id INTEGER PRIMARY KEY,
                            name TEXT NOT NULL,
                            sort_order INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL
                        )
                        """);
            }
        }
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "Setlist", "folder_id")) {
                statement.execute(
                        "ALTER TABLE Setlist ADD COLUMN folder_id INTEGER REFERENCES SetlistFolder(id)");
            }
            if (!hasColumn(connection, "Setlist", "sort_order")) {
                statement.execute("ALTER TABLE Setlist ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            }
        }
        connection.commit();
    }

    private static void migrateBandLayoutExportColumnOrder(Connection connection) throws SQLException {
        if (hasColumn(connection, "BandLayout", "export_column_order")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE BandLayout ADD COLUMN export_column_order TEXT");
        }
        connection.commit();
    }

    private static void migrateBandLayoutSortOrder(Connection connection) throws SQLException {
        if (hasColumn(connection, "BandLayout", "sort_order")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE BandLayout ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            statement.execute("""
                    UPDATE BandLayout SET sort_order = (
                        SELECT COUNT(*) FROM BandLayout bl2
                        WHERE bl2.band_id = BandLayout.band_id AND bl2.id < BandLayout.id
                    )
                    """);
        }
        connection.commit();
    }

    private static void migratePlayerLevelClass(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "Player", "level")) {
                statement.execute("ALTER TABLE Player ADD COLUMN level INTEGER");
            }
            if (!hasColumn(connection, "Player", "class")) {
                statement.execute("ALTER TABLE Player ADD COLUMN class TEXT");
            }
        }
        connection.commit();
    }

    private static void migrateSongLastLayout(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "Song", "last_band_layout_id")) {
                statement.execute(
                        "ALTER TABLE Song ADD COLUMN last_band_layout_id INTEGER REFERENCES BandLayout(id)");
            }
            if (!hasColumn(connection, "Song", "last_song_layout_id")) {
                statement.execute(
                        "ALTER TABLE Song ADD COLUMN last_song_layout_id INTEGER REFERENCES SongLayout(id)");
            }
            if (!hasColumn(connection, "Song", "last_setlist_item_id")) {
                statement.execute(
                        "ALTER TABLE Song ADD COLUMN last_setlist_item_id INTEGER REFERENCES SetlistItem(id)");
            }
        }
        connection.commit();
    }

    /**
     * Rename {@code oldName} to {@code newName}, or merge into an existing {@code newName} row.
     */
    private static void mergeInstrumentInto(Connection connection, String oldName, String newName)
            throws SQLException {
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        String now = Instant.now().toString();

        Long oldId = null;
        Long newId = null;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM Instrument WHERE name = ?")) {
            select.setString(1, oldName);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    oldId = rs.getLong(1);
                }
            }
            select.setString(1, newName);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    newId = rs.getLong(1);
                }
            }
        }
        if (oldId == null) {
            return;
        }
        if (newId == null) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE Instrument SET name = ?, updated_at = ? WHERE id = ?")) {
                update.setString(1, newName);
                update.setString(2, now);
                update.setLong(3, oldId);
                update.executeUpdate();
            }
            connection.commit();
            return;
        }

        List<Long> playersWithBoth = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT DISTINCT pi1.player_id
                FROM PlayerInstrument pi1
                JOIN PlayerInstrument pi2 ON pi1.player_id = pi2.player_id
                WHERE pi1.instrument_id = ? AND pi2.instrument_id = ?
                """)) {
            select.setLong(1, oldId);
            select.setLong(2, newId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    playersWithBoth.add(rs.getLong(1));
                }
            }
        }
        for (long playerId : playersWithBoth) {
            mergePlayerInstrumentRows(connection, playerId, oldId, newId, now);
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE PlayerInstrument SET instrument_id = ?, updated_at = ? WHERE instrument_id = ?")) {
            update.setLong(1, newId);
            update.setString(2, now);
            update.setLong(3, oldId);
            update.executeUpdate();
        }
        remapSongPartsInstrumentIds(connection, oldId, newId, now);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM Instrument WHERE id = ?")) {
            delete.setLong(1, oldId);
            delete.executeUpdate();
        }
        connection.commit();
    }

    private static void mergePlayerInstrumentRows(
            Connection connection, long playerId, long oldId, long newId, String now)
            throws SQLException {
        Long newPiId = null;
        Long oldPiId = null;
        int newHi = 0;
        int newHp = 0;
        int oldHi = 0;
        int oldHp = 0;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, instrument_id, has_instrument, has_proficiency
                FROM PlayerInstrument
                WHERE player_id = ? AND instrument_id IN (?, ?)
                """)) {
            select.setLong(1, playerId);
            select.setLong(2, oldId);
            select.setLong(3, newId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    long instrumentId = rs.getLong(2);
                    if (instrumentId == newId) {
                        newPiId = id;
                        newHi = rs.getInt(3);
                        newHp = rs.getInt(4);
                    } else {
                        oldPiId = id;
                        oldHi = rs.getInt(3);
                        oldHp = rs.getInt(4);
                    }
                }
            }
        }
        if (newPiId == null || oldPiId == null) {
            return;
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE PlayerInstrument
                SET has_instrument = ?, has_proficiency = ?, updated_at = ?
                WHERE id = ?
                """)) {
            update.setInt(1, Math.max(newHi, oldHi));
            update.setInt(2, Math.max(newHp, oldHp));
            update.setString(3, now);
            update.setLong(4, newPiId);
            update.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM PlayerInstrument WHERE id = ?")) {
            delete.setLong(1, oldPiId);
            delete.executeUpdate();
        }
    }

    private static void remapSongPartsInstrumentIds(
            Connection connection, long oldId, long newId, String now) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, parts FROM Song WHERE parts IS NOT NULL AND parts != '' AND parts != '[]'");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE Song SET parts = ?, updated_at = ? WHERE id = ?")) {
            while (rs.next()) {
                long songId = rs.getLong(1);
                String partsJson = rs.getString(2);
                try {
                    JsonNode root = JSON.readTree(partsJson);
                    if (!root.isArray()) {
                        continue;
                    }
                    boolean changed = false;
                    ArrayNode array = (ArrayNode) root;
                    for (JsonNode node : array) {
                        if (node.isObject() && node.has("instrument_id")
                                && node.get("instrument_id").asLong() == oldId) {
                            ((ObjectNode) node).put("instrument_id", newId);
                            changed = true;
                        }
                    }
                    if (changed) {
                        update.setString(1, JSON.writeValueAsString(array));
                        update.setString(2, now);
                        update.setLong(3, songId);
                        update.executeUpdate();
                    }
                } catch (Exception ignored) {
                    // leave malformed parts alone
                }
            }
        }
    }
}

package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.SongLayoutInfo;

class SchemaMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void openOrCreateBuildsSchemaVersionSixteenWithSeeds() throws Exception {
        Path db = tempDir.resolve("new.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(version.next());
                assertEquals(16, version.getInt(1));
            }
            try (ResultSet statuses = statement.executeQuery("SELECT COUNT(*) FROM Status")) {
                assertTrue(statuses.next());
                assertEquals(3, statuses.getInt(1));
            }
            try (ResultSet instruments = statement.executeQuery("SELECT COUNT(*) FROM Instrument")) {
                assertTrue(instruments.next());
                assertEquals(SchemaMigrator.PLAYER_INSTRUMENTS.size(), instruments.getInt(1));
            }
            try (ResultSet tables = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ("
                            + "'Song','SongFile','Band','Player','Setlist','SetlistFolder',"
                            + "'BandLayout','AccountTarget','FolderRule','SetPlayRelay',"
                            + "'SetPlayPublishedSession')")) {
                assertTrue(tables.next());
                assertEquals(11, tables.getInt(1));
            }
            try (ResultSet profiles = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ("
                            + "'BandOptimizationProfile','BandPlayerSwitchWeight','BandInstrumentAffinity',"
                            + "'SetlistPartPreference','SetlistItemKeepWeight','SetlistPartKeepWeight',"
                            + "'SetlistAssignmentPin')")) {
                assertTrue(profiles.next());
                assertEquals(7, profiles.getInt(1));
            }
        }
    }

    @Test
    void openOrCreateMigratesOlderVersionToSixteen() throws Exception {
        Path db = FixtureDatabases.createWrongVersionFixture(tempDir.resolve("v11.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
            assertTrue(version.next());
            assertEquals(16, version.getInt(1));
        }
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet cols = statement.executeQuery("PRAGMA table_info(Setlist)")) {
            boolean hasSetCode = false;
            while (cols.next()) {
                if ("set_code".equals(cols.getString("name"))) {
                    hasSetCode = true;
                    break;
                }
            }
            assertTrue(hasSetCode);
        }
    }

    @Test
    void openOrCreateMigratesV14WithoutChangingAssignments() throws Exception {
        Path db = tempDir.resolve("v14-assignments.sqlite");
        long setlistId;
        long songLayoutId;
        int assignmentCount;
        int songLayoutAssignmentCount;
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteSongLayoutRepository layouts = new SqliteSongLayoutRepository(database);
            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long playerId = players.addPlayer("Ada", null, null);
            long songId = insertSong(database);
            SongLayoutInfo songLayout = layouts.getOrCreateSongLayout(songId, layoutId, "Default");
            songLayoutId = songLayout.id();
            layouts.setAssignment(songLayoutId, playerId, 1);
            setlistId = setlists.addSetlist("Gig", null);
            setlists.updateSetlist(
                    setlistId, "Gig", layoutId, null, 0, false, null, null, null, null, null, null);
            long itemId = setlists.addItem(setlistId, songId, 0, null, songLayoutId);
            setlists.upsertBandAssignment(itemId, playerId, 1);
            assignmentCount = count(database, "SetlistBandAssignment");
            songLayoutAssignmentCount = count(database, "SongLayoutAssignment");
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("DROP TABLE BandPlayerSwitchWeight");
                statement.execute("DROP TABLE BandInstrumentAffinity");
                statement.execute("DROP TABLE BandOptimizationProfile");
                statement.execute("DROP TABLE SetlistPartPreference");
                statement.execute("DROP TABLE SetlistItemKeepWeight");
                statement.execute("DROP TABLE SetlistPartKeepWeight");
                statement.execute("UPDATE schema_version SET version = 14");
            }
        }

        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(version.next());
                assertEquals(16, version.getInt(1));
            }
            for (String table : new String[] {
                    "BandOptimizationProfile",
                    "BandPlayerSwitchWeight",
                    "BandInstrumentAffinity",
                    "SetlistPartPreference",
                    "SetlistItemKeepWeight",
                    "SetlistPartKeepWeight",
                    "SetlistAssignmentPin"
            }) {
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1), table);
                }
            }
            try (ResultSet setlist = statement.executeQuery("SELECT name FROM Setlist WHERE id = " + setlistId)) {
                assertTrue(setlist.next());
                assertEquals("Gig", setlist.getString(1));
            }
            try (ResultSet layout = statement.executeQuery(
                    "SELECT id FROM SongLayout WHERE id = " + songLayoutId)) {
                assertTrue(layout.next());
            }
            assertEquals(assignmentCount, count(database, "SetlistBandAssignment"));
            assertEquals(songLayoutAssignmentCount, count(database, "SongLayoutAssignment"));
        }
    }

    private static int count(SqliteDatabase database, String table) throws Exception {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static long insertSong(SqliteDatabase database) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (title, composers, total_plays, parts, created_at, updated_at)
                VALUES ('Song', 'Composer', 0, '[]', ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, now);
            statement.setString(2, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    @Test
    void openOrCreateIsIdempotentOnExistingDatabase() throws Exception {
        Path db = tempDir.resolve("idempotent.sqlite");
        try (SqliteDatabase first = SqliteDatabase.openOrCreate(db)) {
            // create once
        }
        try (SqliteDatabase second = SqliteDatabase.openOrCreate(db);
             Connection connection = second.connection();
             Statement statement = connection.createStatement();
             ResultSet statuses = statement.executeQuery("SELECT COUNT(*) FROM Status")) {
            assertTrue(statuses.next());
            assertEquals(3, statuses.getInt(1));
        }
    }

    @Test
    void openMergesCaseVariantInstrumentDuplicates() throws Exception {
        Path db = tempDir.resolve("dup-instruments.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO Instrument (name, alternative_names, created_at, updated_at)
                    VALUES ('Jaunty Hand-knells', NULL, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')
                    """);
        }
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement()) {
            try (ResultSet knells = statement.executeQuery(
                    "SELECT COUNT(*) FROM Instrument WHERE LOWER(name) = 'jaunty hand-knells'")) {
                assertTrue(knells.next());
                assertEquals(1, knells.getInt(1));
            }
            try (ResultSet canonical = statement.executeQuery(
                    "SELECT COUNT(*) FROM Instrument WHERE name = 'Jaunty Hand-Knells'")) {
                assertTrue(canonical.next());
                assertEquals(1, canonical.getInt(1));
            }
            try (ResultSet traveler = statement.executeQuery(
                    "SELECT name FROM Instrument WHERE LOWER(name) LIKE '%trusty fiddle%'")) {
                assertTrue(traveler.next());
                assertEquals("Traveler's Trusty Fiddle", traveler.getString(1));
                assertTrue(!traveler.next());
            }
        }
    }
}

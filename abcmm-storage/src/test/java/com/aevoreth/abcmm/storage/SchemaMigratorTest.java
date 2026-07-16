package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void openOrCreateBuildsSchemaVersionTwelveWithSeeds() throws Exception {
        Path db = tempDir.resolve("new.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(version.next());
                assertEquals(12, version.getInt(1));
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
                            + "'BandLayout','AccountTarget','FolderRule')")) {
                assertTrue(tables.next());
                assertEquals(9, tables.getInt(1));
            }
        }
    }

    @Test
    void openOrCreateMigratesOlderVersionToTwelve() throws Exception {
        Path db = FixtureDatabases.createWrongVersionFixture(tempDir.resolve("v11.sqlite"));
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db);
             Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT version FROM schema_version")) {
            assertTrue(version.next());
            assertEquals(12, version.getInt(1));
        }
    }

    @Test
    void openOrCreateIsIdempotentOnExistingV12() throws Exception {
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

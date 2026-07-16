package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.scan.DuplicateDecision;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;

class SqliteLibraryScanServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void scansMusicRootAndUpsertsSongs() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        writeAbc(music.resolve("alpha.abc"), "Alpha", "Ada", 1);
        writeAbc(music.resolve("beta.abc"), "Beta", "Bea", 2);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            AtomicReference<ScanProgress> last = new AtomicReference<>();
            ScanProgress result = scanner.scan(
                    new ScanRequest(lotro, null, null),
                    candidate -> DuplicateDecision.SEPARATE,
                    last::set);

            assertEquals(2, result.filesScanned());
            assertEquals(2, result.songsAdded());
            assertEquals(0, result.songsUpdated());
            assertEquals(0, result.songsRemoved());
            assertTrue(last.get().message().contains("complete") || last.get().filesScanned() == 2);

            assertEquals(2, countSongs(database));
            assertEquals("Alpha", loadTitle(database, "alpha.abc"));
            assertTrue(loadPartsJson(database, "alpha.abc").contains("\"part_number\":1"));
        }
    }

    @Test
    void updatesExistingPathAndRemovesMissingFiles() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        Path alpha = music.resolve("alpha.abc");
        writeAbc(alpha, "Alpha", "Ada", 1);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            scanner.scan(new ScanRequest(lotro, null, 1L), null, null);
            assertEquals(1, countSongs(database));

            writeAbc(alpha, "Alpha Revised", "Ada", 1);
            ScanProgress update = scanner.scan(new ScanRequest(lotro, null, 1L), null, null);
            assertEquals(1, update.filesScanned());
            assertEquals(0, update.songsAdded());
            assertEquals(1, update.songsUpdated());
            assertEquals("Alpha Revised", loadTitle(database, "alpha.abc"));

            Files.delete(alpha);
            ScanProgress cleanup = scanner.scan(new ScanRequest(lotro, null, 1L), null, null);
            assertEquals(0, cleanup.filesScanned());
            assertEquals(1, cleanup.songsRemoved());
            assertEquals(0, countSongs(database));
        }
    }

    @Test
    void excludesSetExportAndFolderRules() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Path setDir = music.resolve("Sets");
        Path excluded = music.resolve("SkipMe");
        Files.createDirectories(setDir);
        Files.createDirectories(excluded);
        writeAbc(music.resolve("keep.abc"), "Keep", "Ada", 1);
        writeAbc(setDir.resolve("set-song.abc"), "Set Song", "Ada", 1);
        writeAbc(excluded.resolve("hidden.abc"), "Hidden", "Ada", 1);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            insertExcludeRule(database, "SkipMe");
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress result = scanner.scan(
                    new ScanRequest(lotro, setDir, null),
                    null,
                    null);
            assertEquals(1, result.songsAdded());
            assertEquals(1, countSongs(database));
            assertEquals("Keep", loadTitle(database, "keep.abc"));
            assertFalse(songFileExists(database, setDir.resolve("set-song.abc")));
            assertFalse(songFileExists(database, excluded.resolve("hidden.abc")));
        }
    }

    @Test
    void duplicateResolverCanKeepExistingOrSeparate() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        writeAbc(music.resolve("one.abc"), "Same", "Ada", 2);
        writeAbc(music.resolve("two.abc"), "Same", "Ada", 2);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress keepExisting = scanner.scan(
                    new ScanRequest(lotro, null, null),
                    candidate -> DuplicateDecision.KEEP_EXISTING,
                    null);
            assertEquals(1, keepExisting.songsAdded());
            assertEquals(1, countSongs(database));

            // Reset DB content for SEPARATE path
        }

        Path dbPath2 = tempDir.resolve("library2.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath2)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress separate = scanner.scan(
                    new ScanRequest(lotro, null, null),
                    candidate -> DuplicateDecision.SEPARATE,
                    null);
            assertEquals(2, separate.songsAdded());
            assertEquals(2, countSongs(database));
        }
    }

    @Test
    void assignsDefaultStatusIdToNewSongs() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        writeAbc(music.resolve("status.abc"), "Status Song", "Ada", 1);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            scanner.scan(new ScanRequest(lotro, null, 2L), null, null);
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT status_id FROM Song");
                 ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }
        }
    }

    private static void writeAbc(Path path, String title, String composer, int parts) throws Exception {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("%%song-title ").append(title).append('\n');
        sb.append("%%song-composer ").append(composer).append('\n');
        sb.append("%%song-duration 1:00\n");
        for (int i = 1; i <= parts; i++) {
            sb.append("X:").append(i).append('\n');
            sb.append("%%part-name Part ").append(i).append('\n');
            sb.append("%%made-for Basic Lute\n");
        }
        Files.writeString(path, sb.toString());
    }

    private static void insertExcludeRule(SqliteDatabase database, String relativePath) throws Exception {
        String now = Instant.now().toString();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        INSERT INTO FolderRule (rule_type, path, enabled, include_in_export, created_at, updated_at)
                        VALUES ('exclude', ?, 1, 0, ?, ?)
                        """)) {
            statement.setString(1, relativePath);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
        }
    }

    private static int countSongs(SqliteDatabase database) throws Exception {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Song")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static String loadTitle(SqliteDatabase database, String fileNameSuffix) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        SELECT s.title FROM Song s
                        JOIN SongFile sf ON sf.song_id = s.id
                        WHERE sf.file_path LIKE ?
                        """)) {
            statement.setString(1, "%" + fileNameSuffix);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private static String loadPartsJson(SqliteDatabase database, String fileNameSuffix) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        SELECT s.parts FROM Song s
                        JOIN SongFile sf ON sf.song_id = s.id
                        WHERE sf.file_path LIKE ?
                        """)) {
            statement.setString(1, "%" + fileNameSuffix);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private static boolean songFileExists(SqliteDatabase database, Path path) throws Exception {
        String normalized = path.toAbsolutePath().normalize().toString();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT 1 FROM SongFile WHERE file_path = ?")) {
            statement.setString(1, normalized);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}

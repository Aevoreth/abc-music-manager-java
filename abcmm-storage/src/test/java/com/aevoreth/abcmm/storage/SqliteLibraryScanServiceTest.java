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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.scan.CleanupApplyResult;
import com.aevoreth.abcmm.domain.scan.DuplicateAnalysis;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlan;
import com.aevoreth.abcmm.domain.scan.DuplicateGroup;
import com.aevoreth.abcmm.domain.scan.DuplicateMatchType;
import com.aevoreth.abcmm.domain.scan.FileDisposition;
import com.aevoreth.abcmm.domain.scan.FileResolution;
import com.aevoreth.abcmm.domain.scan.FolderDisposition;
import com.aevoreth.abcmm.domain.scan.FolderDuplicateCluster;
import com.aevoreth.abcmm.domain.scan.FolderResolution;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;
import com.aevoreth.abcmm.domain.scan.TrashService;

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
            ScanProgress result = fullScan(scanner, new ScanRequest(lotro, null, null), last);

            assertEquals(2, result.songsAdded());
            assertEquals(0, result.songsRemoved());
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
            fullScan(scanner, new ScanRequest(lotro, null, 1L), null);
            assertEquals(1, countSongs(database));

            writeAbc(alpha, "Alpha Revised", "Ada", 1);
            ScanProgress update = fullScan(scanner, new ScanRequest(lotro, null, 1L), null);
            assertEquals(0, update.songsAdded());
            assertEquals(1, update.songsUpdated());
            assertEquals("Alpha Revised", loadTitle(database, "alpha.abc"));

            Files.delete(alpha);
            ScanProgress cleanup = fullScan(scanner, new ScanRequest(lotro, null, 1L), null);
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
            ScanProgress result = fullScan(scanner, new ScanRequest(lotro, setDir, null), null);
            assertEquals(1, result.songsAdded());
            assertEquals(1, countSongs(database));
            assertEquals("Keep", loadTitle(database, "keep.abc"));
            assertFalse(songFileExists(database, setDir.resolve("set-song.abc")));
            assertFalse(songFileExists(database, excluded.resolve("hidden.abc")));
        }
    }

    @Test
    void excludesNestedRelativeSetExportDir() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Path setDir = music.resolve("Aev").resolve("Sets");
        Files.createDirectories(setDir);
        writeAbc(music.resolve("Aev").resolve("keep.abc"), "Keep", "Ada", 1);
        writeAbc(setDir.resolve("set-song.abc"), "Set Song", "Ada", 1);

        Path dbPath = tempDir.resolve("library-nested-sets.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress result = fullScan(scanner, new ScanRequest(lotro, Path.of("Aev/Sets"), null), null);
            assertEquals(1, result.songsAdded());
            assertEquals(1, countSongs(database));
            assertEquals("Keep", loadTitle(database, "keep.abc"));
            assertFalse(songFileExists(database, setDir.resolve("set-song.abc")));
        }
    }

    @Test
    void missingSetExportDirDoesNotExcludeMusicSets() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Path setDir = music.resolve("Aev").resolve("Sets");
        Files.createDirectories(setDir);
        writeAbc(setDir.resolve("set-song.abc"), "Set Song", "Ada", 1);

        Path dbPath = tempDir.resolve("library-missing-sets.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress result = fullScan(
                    scanner, new ScanRequest(lotro, Path.of("C:/does/not/exist/Sets"), null), null);
            assertEquals(1, result.songsAdded());
            assertTrue(songFileExists(database, setDir.resolve("set-song.abc")));
        }
    }

    @Test
    void ignoresSetExportAbsolutePathOutsideMusic() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Path setDir = music.resolve("Aev").resolve("Sets");
        Path outside = tempDir.resolve("cwd-Aev").resolve("Sets");
        Files.createDirectories(setDir);
        Files.createDirectories(outside);
        writeAbc(setDir.resolve("set-song.abc"), "Set Song", "Ada", 1);

        Path dbPath = tempDir.resolve("library-outside-sets.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            ScanProgress result = fullScan(scanner, new ScanRequest(lotro, outside, null), null);
            assertEquals(1, result.songsAdded());
            assertTrue(songFileExists(database, setDir.resolve("set-song.abc")));
        }
    }

    @Test
    void analyzeDoesNotInsertDuplicatesUntilPlanApplied() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        // Reverse alpha order names so first-walked would previously win
        writeAbc(music.resolve("aabc.abc"), "ABC", "Ada", 1);
        writeAbc(music.resolve("aabcc.abc"), "ABC", "Ada", 1);
        writeAbc(music.resolve("abc.abc"), "ABC", "Ada", 1);

        Path dbPath = tempDir.resolve("library-peers.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            assertEquals(0, countSongs(database));
            assertEquals(1, analysis.groups().size());
            DuplicateGroup group = analysis.groups().get(0);
            assertEquals(3, group.files().size());
            assertEquals(DuplicateMatchType.EXACT_FILE, group.matchType());
        }
    }

    @Test
    void keepExistingIgnoresOtherPeers() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        writeAbc(music.resolve("one.abc"), "Same", "Ada", 2);
        writeAbc(music.resolve("two.abc"), "Same", "Ada", 2);

        Path dbPath = tempDir.resolve("library.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            DuplicateGroup group = analysis.groups().get(0);
            List<FileResolution> resolutions = new ArrayList<>();
            resolutions.add(new FileResolution(
                    group.groupId(), group.files().get(0).path(), FileDisposition.KEEP));
            for (int i = 1; i < group.files().size(); i++) {
                resolutions.add(new FileResolution(
                        group.groupId(), group.files().get(i).path(), FileDisposition.IGNORE));
            }
            scanner.apply(new DuplicateCleanupPlan(resolutions, List.of()), null);
            ScanProgress reconcile = scanner.reconcile(new ScanRequest(lotro, null, null), null);
            assertEquals(1, countSongs(database));
            assertEquals(0, reconcile.songsAdded()); // ignored peer stays out
        }
    }

    @Test
    void keepSeparateIndexesMultipleCopies() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        writeAbc(music.resolve("one.abc"), "Same", "Ada", 2);
        writeAbc(music.resolve("two.abc"), "Same", "Ada", 2);

        Path dbPath = tempDir.resolve("library2.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            DuplicateGroup group = analysis.groups().get(0);
            List<FileResolution> resolutions = group.files().stream()
                    .map(f -> new FileResolution(group.groupId(), f.path(), FileDisposition.KEEP_SEPARATE))
                    .toList();
            scanner.apply(new DuplicateCleanupPlan(resolutions, List.of()), null);
            scanner.reconcile(new ScanRequest(lotro, null, null), null);
            assertEquals(2, countSongs(database));
        }
    }

    @Test
    void keepRemapPreservesSongId() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        Path aabc = music.resolve("aabc.abc");
        Path abc = music.resolve("abc.abc");
        writeAbc(aabc, "ABC", "Ada", 1);

        Path dbPath = tempDir.resolve("library-remap.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            fullScan(scanner, new ScanRequest(lotro, null, null), null);
            long songId = loadSongId(database, "aabc.abc");
            assertTrue(songId > 0);

            writeAbc(abc, "ABC", "Ada", 1);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            assertFalse(analysis.groups().isEmpty());
            DuplicateGroup group = analysis.groups().get(0);
            List<FileResolution> resolutions = new ArrayList<>();
            for (var file : group.files()) {
                if (file.path().endsWith("abc.abc")) {
                    resolutions.add(new FileResolution(
                            group.groupId(), file.path(), FileDisposition.KEEP, songId));
                } else {
                    resolutions.add(new FileResolution(
                            group.groupId(), file.path(), FileDisposition.IGNORE));
                }
            }
            scanner.apply(new DuplicateCleanupPlan(resolutions, List.of()), null);
            scanner.reconcile(new ScanRequest(lotro, null, null), null);

            assertEquals(1, countSongs(database));
            assertEquals(songId, loadSongId(database, "abc.abc"));
            assertFalse(songFileExists(database, aabc));
        }
    }

    @Test
    void cancelAfterAnalyzePerformsNoTrash() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        Path one = music.resolve("one.abc");
        Path two = music.resolve("two.abc");
        writeAbc(one, "Same", "Ada", 1);
        writeAbc(two, "Same", "Ada", 1);

        RecordingTrash trash = new RecordingTrash();
        Path dbPath = tempDir.resolve("library-cancel.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database, trash);
            scanner.analyze(new ScanRequest(lotro, null, null), null);
            // cancel = skip apply
            scanner.reconcile(new ScanRequest(lotro, null, null), null);
            assertTrue(trash.trashed.isEmpty());
            assertTrue(Files.exists(one));
            assertTrue(Files.exists(two));
            assertEquals(0, countSongs(database));
        }
    }

    @Test
    void trashOnlySelectedFiles() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        Path one = music.resolve("one.abc");
        Path two = music.resolve("two.abc");
        writeAbc(one, "Same", "Ada", 1);
        writeAbc(two, "Same", "Ada", 1);

        RecordingTrash trash = new RecordingTrash();
        Path dbPath = tempDir.resolve("library-trash.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database, trash);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            DuplicateGroup group = analysis.groups().get(0);
            List<FileResolution> resolutions = new ArrayList<>();
            for (var file : group.files()) {
                if (file.path().equals(one.toAbsolutePath().normalize())) {
                    resolutions.add(new FileResolution(
                            group.groupId(), file.path(), FileDisposition.KEEP));
                } else {
                    resolutions.add(new FileResolution(
                            group.groupId(), file.path(), FileDisposition.TRASH));
                }
            }
            CleanupApplyResult result = scanner.apply(new DuplicateCleanupPlan(resolutions, List.of()), null);
            assertEquals(1, result.filesTrashed());
            assertEquals(1, trash.trashed.size());
            assertTrue(trash.trashed.get(0).endsWith("two.abc") || trash.trashed.get(0).getFileName().toString().equals("two.abc"));
            assertTrue(Files.exists(one));
        }
    }

    @Test
    void copyingFolderDoesNotStealSongFilesFromOriginal() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        // "Main - Copy" sorts before "Main\" — this previously triggered a false identity-move.
        Path main = music.resolve("Main");
        Path copy = music.resolve("Main - Copy");
        Files.createDirectories(main);
        writeAbc(main.resolve("a.abc"), "A", "Ada", 1);
        writeAbc(main.resolve("b.abc"), "B", "Bea", 1);

        Path dbPath = tempDir.resolve("library-copy-steal.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database, new RecordingTrash());
            fullScan(scanner, new ScanRequest(lotro, null, null), null);
            long songA = loadSongId(database, "Main" + java.io.File.separator + "a.abc");
            long songB = loadSongId(database, "Main" + java.io.File.separator + "b.abc");
            assertEquals(2, countSongs(database));

            Files.createDirectories(copy);
            Files.copy(main.resolve("a.abc"), copy.resolve("a.abc"));
            Files.copy(main.resolve("b.abc"), copy.resolve("b.abc"));

            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            // Originals must still be indexed at Main — copy must not steal SongFile rows.
            assertEquals(songA, loadSongId(database, "Main" + java.io.File.separator + "a.abc"));
            assertEquals(songB, loadSongId(database, "Main" + java.io.File.separator + "b.abc"));
            assertFalse(songFileExists(database, copy.resolve("a.abc")));
            assertFalse(analysis.folderClusters().isEmpty());

            FolderDuplicateCluster cluster = analysis.folderClusters().get(0);
            List<FolderResolution> folderResolutions = List.of(
                    new FolderResolution(cluster.clusterId(), main.toAbsolutePath().normalize(),
                            FolderDisposition.KEEP_AND_SCAN),
                    new FolderResolution(cluster.clusterId(), copy.toAbsolutePath().normalize(),
                            FolderDisposition.TRASH));
            scanner.apply(new DuplicateCleanupPlan(List.of(), folderResolutions), null);
            ScanProgress reconcile = scanner.reconcile(new ScanRequest(lotro, null, null), null);

            assertEquals(songA, loadSongId(database, "Main" + java.io.File.separator + "a.abc"));
            assertEquals(songB, loadSongId(database, "Main" + java.io.File.separator + "b.abc"));
            assertEquals(2, countSongs(database));
            assertEquals(0, reconcile.songsAdded());
            assertTrue(Files.exists(main.resolve("a.abc")));
            assertFalse(Files.exists(copy.resolve("a.abc")));
        }
    }

    @Test
    void excludeFolderCreatesRuleAndSkipsNextScan() throws Exception {
        Path lotro = tempDir.resolve("lotro");
        Path music = lotro.resolve("Music");
        Path main = music.resolve("Main");
        Path copy = music.resolve("Main - Copy");
        Files.createDirectories(main);
        Files.createDirectories(copy);
        writeAbc(main.resolve("a.abc"), "A", "Ada", 1);
        writeAbc(main.resolve("b.abc"), "B", "Bea", 1);
        writeAbc(copy.resolve("a.abc"), "A", "Ada", 1);
        writeAbc(copy.resolve("b.abc"), "B", "Bea", 1);

        Path dbPath = tempDir.resolve("library-folder.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            SqliteLibraryScanService scanner = new SqliteLibraryScanService(database);
            DuplicateAnalysis analysis = scanner.analyze(new ScanRequest(lotro, null, null), null);
            assertFalse(analysis.folderClusters().isEmpty());
            FolderDuplicateCluster cluster = analysis.folderClusters().get(0);
            assertEquals(2, cluster.folderPaths().size());

            List<FolderResolution> folderResolutions = List.of(
                    new FolderResolution(cluster.clusterId(), main.toAbsolutePath().normalize(),
                            FolderDisposition.KEEP_AND_SCAN),
                    new FolderResolution(cluster.clusterId(), copy.toAbsolutePath().normalize(),
                            FolderDisposition.EXCLUDE_FROM_SCANS));
            // Resolve file groups under kept folder as keep-separate / ignore as needed
            List<FileResolution> fileResolutions = new ArrayList<>();
            for (DuplicateGroup group : analysis.groups()) {
                for (var file : group.files()) {
                    if (file.path().startsWith(copy.toAbsolutePath().normalize())) {
                        fileResolutions.add(new FileResolution(
                                group.groupId(), file.path(), FileDisposition.IGNORE));
                    } else {
                        fileResolutions.add(new FileResolution(
                                group.groupId(), file.path(), FileDisposition.KEEP_SEPARATE));
                    }
                }
            }
            CleanupApplyResult apply = scanner.apply(
                    new DuplicateCleanupPlan(fileResolutions, folderResolutions), null);
            assertEquals(1, apply.foldersExcluded());
            assertTrue(folderRuleExists(database, "Main - Copy"));

            DuplicateAnalysis again = scanner.analyze(new ScanRequest(lotro, null, null), null);
            assertTrue(again.folderClusters().isEmpty()
                    || again.folderClusters().stream().noneMatch(c ->
                    c.folderPaths().stream().anyMatch(p -> p.endsWith("Main - Copy"))));
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
            fullScan(scanner, new ScanRequest(lotro, null, 2L), null);
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT status_id FROM Song");
                 ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }
        }
    }

    private static ScanProgress fullScan(
            SqliteLibraryScanService scanner,
            ScanRequest request,
            AtomicReference<ScanProgress> last) throws Exception {
        scanner.analyze(request, last == null ? null : last::set);
        return scanner.reconcile(request, last == null ? null : last::set);
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

    private static boolean folderRuleExists(SqliteDatabase database, String pathSuffix) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT path FROM FolderRule WHERE rule_type = 'exclude'");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String path = rs.getString(1);
                if (path != null && path.replace('\\', '/').endsWith(pathSuffix.replace('\\', '/'))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countSongs(SqliteDatabase database) throws Exception {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Song")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static long loadSongId(SqliteDatabase database, String fileNameSuffix) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        SELECT s.id FROM Song s
                        JOIN SongFile sf ON sf.song_id = s.id
                        WHERE sf.file_path LIKE ?
                        """)) {
            statement.setString(1, "%" + fileNameSuffix);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
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

    private static final class RecordingTrash implements TrashService {
        private final List<Path> trashed = new ArrayList<>();

        @Override
        public boolean moveToTrash(Path path) {
            trashed.add(path.toAbsolutePath().normalize());
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
                // ignore
            }
            return true;
        }
    }
}

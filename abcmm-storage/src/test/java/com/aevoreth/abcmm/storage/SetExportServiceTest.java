package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.aevoreth.abcmm.domain.export.SetExportException;
import com.aevoreth.abcmm.domain.export.SetExportItemInfo;
import com.aevoreth.abcmm.domain.export.SetExportService;
import com.aevoreth.abcmm.domain.export.SetExportSettings;

class SetExportServiceTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exportSetOptionallyWritesRelativeAbcp(boolean exportAbcp) throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path aPath = src.resolve("a.abc");
        Path bPath = src.resolve("b.abc");
        Files.writeString(aPath, "X:1\nT:test\n", StandardCharsets.UTF_8);
        Files.writeString(bPath, "X:1\nT:test2\n", StandardCharsets.UTF_8);

        Path outParent = tempDir.resolve("out");
        Files.createDirectories(outParent);

        Path dbPath = tempDir.resolve("export.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            seedSong(database, 1, "A", aPath.toString(),
                    "[{\"part_number\":1,\"part_name\":\"P1\",\"instrument_id\":null}]");
            seedSong(database, 2, "B", bPath.toString(),
                    "[{\"part_number\":1,\"part_name\":\"P1\",\"instrument_id\":null}]");

            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteSongRepository songs = new SqliteSongRepository(database);
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);

            long setlistId = setlists.addSetlist("Test Set", null);
            setlists.addItem(setlistId, 1, 0, null, null);
            setlists.addItem(setlistId, 2, 1, null, null);

            SetExportSettings settings = new SetExportSettings();
            settings.setOutputDirectory(outParent);
            settings.setSetName("MySet");
            settings.setExportAsFolder(true);
            settings.setExportAsZip(false);
            settings.setRenameAbcFiles(true);
            settings.setFilenamePattern("$SongIndex_$FileName");
            settings.setWhitespaceReplace(" ");
            settings.setPartCountZeroPadded(true);
            settings.setExportCsvPartSheet(false);
            settings.setExportAbcpPlaylist(exportAbcp);
            settings.setRenameParts(false);

            List<SetExportItemInfo> items = setlists.listItemsForExport(setlistId);
            SetExportService service = new SetExportService(setlists, songs, bands, players);
            service.exportSet(setlistId, "Test Set", null, items, settings, null, null);

            Path folder = outParent.resolve("MySet");
            assertTrue(Files.isDirectory(folder));
            assertTrue(Files.isRegularFile(folder.resolve("001_a.abc")));
            assertTrue(Files.isRegularFile(folder.resolve("002_b.abc")));
            Path abcp = folder.resolve("MySet.abcp");
            if (exportAbcp) {
                assertTrue(Files.isRegularFile(abcp));
                String text = Files.readString(abcp, StandardCharsets.UTF_8);
                assertTrue(text.contains("<location>001_a.abc</location>"));
                assertTrue(text.contains("<location>002_b.abc</location>"));
                assertFalse(text.contains(aPath.toString()));
            } else {
                assertFalse(Files.exists(abcp));
            }
        }
    }

    @Test
    void exportCsvAppliesPartRenameRules() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path aPath = src.resolve("a.abc");
        Files.writeString(aPath, "X:1\nT:test\n", StandardCharsets.UTF_8);

        Path outParent = tempDir.resolve("out");
        Files.createDirectories(outParent);

        Path dbPath = tempDir.resolve("csv.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            String parts = """
                    [{"part_number":1,"part_name":"Misty Mountain Harp","instrument_id":null},\
                    {"part_number":2,"part_name":"Basic Theorbo","instrument_id":null}]
                    """;
            seedSong(database, 1, "A", aPath.toString(), parts);

            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteSongRepository songs = new SqliteSongRepository(database);
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);

            long setlistId = setlists.addSetlist("Test Set", null);
            setlists.addItem(setlistId, 1, 0, null, null);

            SetExportSettings settings = new SetExportSettings();
            settings.setOutputDirectory(outParent);
            settings.setSetName("MySet");
            settings.setExportAsFolder(true);
            settings.setExportAsZip(false);
            settings.setRenameAbcFiles(false);
            settings.setExportCsvPartSheet(true);
            settings.setExportAbcpPlaylist(false);
            settings.setCsvUseVisibleColumns(true);
            settings.setIncludeComposerInCsv(false);
            settings.setCsvPartColumns("part");
            settings.setCsvPartRenameRules(List.of(
                    new SetExportSettings.FindReplaceRule("Misty Mountain Harp", "MMH"),
                    new SetExportSettings.FindReplaceRule("Basic Theorbo", "Theorbo")));

            SetExportService service = new SetExportService(setlists, songs, bands, players);
            service.exportSet(
                    setlistId,
                    "Test Set",
                    null,
                    setlists.listItemsForExport(setlistId),
                    settings,
                    null,
                    null);

            Path csvPath = outParent.resolve("MySet").resolve("MySet.csv");
            assertTrue(Files.isRegularFile(csvPath));
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).endsWith("Part 1,Part 2"));
            assertTrue(lines.get(1).endsWith("MMH,Theorbo"));
        }
    }

    @Test
    void refusesExistingFolder() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path aPath = src.resolve("a.abc");
        Files.writeString(aPath, "X:1\nT:test\n", StandardCharsets.UTF_8);

        Path outParent = tempDir.resolve("out");
        Files.createDirectories(outParent.resolve("MySet"));

        Path dbPath = tempDir.resolve("exists.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            seedSong(database, 1, "A", aPath.toString(), "[]");
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            long setlistId = setlists.addSetlist("Test Set", null);
            setlists.addItem(setlistId, 1, 0, null, null);

            SetExportSettings settings = new SetExportSettings();
            settings.setOutputDirectory(outParent);
            settings.setSetName("MySet");
            settings.setExportAsFolder(true);
            settings.setExportAsZip(false);

            SetExportService service = new SetExportService(
                    setlists,
                    new SqliteSongRepository(database),
                    new SqliteBandRepository(database),
                    new SqlitePlayerRepository(database));
            assertThrows(SetExportException.class, () -> service.exportSet(
                    setlistId,
                    "Test Set",
                    null,
                    setlists.listItemsForExport(setlistId),
                    settings,
                    null,
                    null));
        }
    }

    private static void seedSong(
            SqliteDatabase database,
            long id,
            String title,
            String filePath,
            String partsJson) throws Exception {
        try (var statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (id, title, composers, duration_seconds, parts, created_at, updated_at)
                VALUES (?, ?, 'C', 60, ?, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, title);
            statement.setString(3, partsJson);
            statement.executeUpdate();
        }
        try (var statement = database.connection().prepareStatement(
                """
                INSERT INTO SongFile (song_id, file_path, is_primary_library, is_set_copy,
                                     scan_excluded, created_at, updated_at)
                VALUES (?, ?, 1, 0, 0, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, filePath);
            statement.executeUpdate();
        }
    }
}

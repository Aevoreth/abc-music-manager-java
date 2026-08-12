package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.export.PluginDataLuaBuilder;
import com.aevoreth.abcmm.domain.library.SongFileMetadata;
import com.aevoreth.abcmm.domain.prefs.Preferences;

class PluginDataExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void findMetadataByFilePathReturnsSongFields() throws Exception {
        Path abc = tempDir.resolve("song.abc");
        Files.writeString(abc, "X:1\nT:Hello\n", StandardCharsets.UTF_8);
        Path dbPath = tempDir.resolve("meta.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            seedSong(database, 1, "Hello", "Composer", "Trans", 90,
                    "[{\"part_number\":1,\"part_name\":\"P1\",\"title_from_t\":\"Hello Part\",\"instrument_id\":null}]",
                    abc.toAbsolutePath().normalize().toString());
            SqliteSongRepository songs = new SqliteSongRepository(database);
            Optional<SongFileMetadata> meta =
                    songs.findMetadataByFilePath(abc.toAbsolutePath().normalize().toString());
            assertTrue(meta.isPresent());
            assertEquals("Hello", meta.get().title());
            assertEquals("Composer", meta.get().composers());
            assertEquals("Trans", meta.get().transcriber());
            assertEquals(90, meta.get().durationSeconds());
            assertTrue(meta.get().partsJson().contains("title_from_t"));
        }
    }

    @Test
    void writeAllTargetsCreatesAndOverwritesPluginData() throws Exception {
        Path lotro = tempDir.resolve("LOTRO");
        Path music = lotro.resolve("Music");
        Path folk = music.resolve("Folk");
        Files.createDirectories(folk);
        Path abc = folk.resolve("jig.abc");
        Files.writeString(abc, """
                X:1
                T:Jig Title
                C:Traditional
                Z:Ann
                K:C
                C
                """, StandardCharsets.UTF_8);

        Path targetDir = tempDir.resolve("PluginData").resolve("Acct").resolve("AllServers");
        // parent missing on purpose — service should create it
        Path dbPath = tempDir.resolve("export.sqlite");

        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            String pathStr = abc.toRealPath().toString();
            seedSong(database, 1, "Jig Title", "Traditional", "Ann", 30,
                    "[{\"part_number\":1,\"part_name\":\"Melody\",\"title_from_t\":\"Jig Title\",\"instrument_id\":null}]",
                    pathStr);
            try (PreparedStatement insert = database.connection().prepareStatement(
                    """
                            INSERT INTO AccountTarget (account_name, plugin_data_path, enabled, created_at, updated_at)
                            VALUES (?, ?, 1, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                            """)) {
                insert.setString(1, "Acct");
                insert.setString(2, targetDir.toString());
                insert.executeUpdate();
            }

            Preferences prefs = new Preferences();
            prefs.setLotroRoot(lotro.toString());

            SqliteSongRepository songs = new SqliteSongRepository(database, false);
            PluginDataExportService service = new PluginDataExportService(songs);
            List<String> log = new ArrayList<>();
            PluginDataExportService.ExportResult result = service.writeAllTargets(
                    prefs, (msg, err) -> log.add((err ? "E:" : "") + msg));

            assertEquals(1, result.successCount());
            assertTrue(result.errors().isEmpty());
            assertEquals(1, result.songCount());
            Path out = targetDir.resolve(PluginDataExportService.PLUGINDATA_FILENAME);
            assertTrue(Files.isRegularFile(out));
            String lua = Files.readString(out, StandardCharsets.UTF_8);
            assertTrue(lua.contains("[\"Filepath\"] = \"/Folk/\""));
            assertTrue(lua.contains("[\"Filename\"] = \"jig\""));
            assertTrue(lua.contains("[\"Name\"] =\"Jig Title\""));
            assertTrue(lua.contains("[\"Transcriber\"] = \"Ann\""));
            assertTrue(lua.contains("[\"Artist\"] = \"Traditional\""));
            assertTrue(lua.contains("[\"Directories\"] ="));
            assertTrue(log.stream().anyMatch(l -> l.contains("OK")));

            // overwrite
            Files.writeString(out, "stale", StandardCharsets.UTF_8);
            service.writeAllTargets(prefs, null);
            String lua2 = Files.readString(out, StandardCharsets.UTF_8);
            assertFalse(lua2.contains("stale"));
            assertTrue(lua2.startsWith("return"));
        }
    }

    @Test
    void buildLuaFallsBackToParserWhenNotInDatabase() throws Exception {
        Path lotro = tempDir.resolve("LOTRO2");
        Path music = lotro.resolve("Music");
        Files.createDirectories(music);
        Path abc = music.resolve("orphan.abc");
        Files.writeString(abc, """
                X:1
                T:Orphan Song
                C:Someone
                K:C
                C
                """, StandardCharsets.UTF_8);

        Path dbPath = tempDir.resolve("orphan.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            Preferences prefs = new Preferences();
            prefs.setLotroRoot(lotro.toString());
            PluginDataExportService service =
                    new PluginDataExportService(new SqliteSongRepository(database, false));
            PluginDataLuaBuilder.BuildResult built = service.buildLua(prefs);
            assertEquals(1, built.songCount());
            assertTrue(built.lua().contains("[\"Filename\"] = \"orphan\""));
            assertTrue(built.lua().contains("Orphan Song") || built.lua().contains("Someone"));
        }
    }

    @Test
    void excludedFolderSkippedUnlessIncludeInExport() throws Exception {
        Path lotro = tempDir.resolve("LOTRO3");
        Path music = lotro.resolve("Music");
        Path archive = music.resolve("Archive");
        Path keep = archive.resolve("Keep");
        Files.createDirectories(keep);
        Files.createDirectories(music.resolve("Open"));
        Path excluded = archive.resolve("skip.abc");
        Path included = keep.resolve("keep.abc");
        Path open = music.resolve("Open").resolve("open.abc");
        String abcBody = "X:1\nT:T\nC:C\nK:C\nC\n";
        Files.writeString(excluded, abcBody, StandardCharsets.UTF_8);
        Files.writeString(included, abcBody, StandardCharsets.UTF_8);
        Files.writeString(open, abcBody, StandardCharsets.UTF_8);

        Path dbPath = tempDir.resolve("rules.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(dbPath)) {
            try (Statement st = database.connection().createStatement()) {
                st.executeUpdate("""
                        INSERT INTO FolderRule (rule_type, path, enabled, include_in_export, created_at, updated_at)
                        VALUES ('exclude', 'Archive', 1, 0, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                        """);
                st.executeUpdate("""
                        INSERT INTO FolderRule (rule_type, path, enabled, include_in_export, created_at, updated_at)
                        VALUES ('exclude', 'Archive/Keep', 1, 1, '2020-01-01T00:00:00', '2020-01-01T00:00:00')
                        """);
            }
            Preferences prefs = new Preferences();
            prefs.setLotroRoot(lotro.toString());
            PluginDataExportService service =
                    new PluginDataExportService(new SqliteSongRepository(database, false));
            String lua = service.buildLua(prefs).lua();
            assertTrue(lua.contains("[\"Filename\"] = \"keep\""));
            assertTrue(lua.contains("[\"Filename\"] = \"open\""));
            assertFalse(lua.contains("[\"Filename\"] = \"skip\""));
        }
    }

    private static void seedSong(
            SqliteDatabase database,
            long id,
            String title,
            String composers,
            String transcriber,
            int duration,
            String partsJson,
            String filePath) throws Exception {
        String now = "2020-01-01T00:00:00";
        try (PreparedStatement song = database.connection().prepareStatement(
                """
                        INSERT INTO Song (id, title, composers, duration_seconds, transcriber, parts,
                                          total_plays, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """);
             PreparedStatement file = database.connection().prepareStatement(
                     """
                             INSERT INTO SongFile (song_id, file_path, is_primary_library, is_set_copy,
                                                   created_at, updated_at)
                             VALUES (?, ?, 1, 0, ?, ?)
                             """)) {
            song.setLong(1, id);
            song.setString(2, title);
            song.setString(3, composers);
            song.setInt(4, duration);
            song.setString(5, transcriber);
            song.setString(6, partsJson);
            song.setString(7, now);
            song.setString(8, now);
            song.executeUpdate();
            file.setLong(1, id);
            file.setString(2, filePath);
            file.setString(3, now);
            file.setString(4, now);
            file.executeUpdate();
        }
    }
}

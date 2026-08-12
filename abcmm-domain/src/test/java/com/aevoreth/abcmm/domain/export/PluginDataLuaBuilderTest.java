package com.aevoreth.abcmm.domain.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginDataLuaBuilderTest {

    @Test
    void luaEscapeEscapesBackslashQuoteAndNewlines() {
        assertEquals("a\\\\b\\\"c\\nd\\re", PluginDataPathRules.luaEscape("a\\b\"c\nd\re"));
        assertEquals("", PluginDataPathRules.luaEscape(null));
    }

    @Test
    void dirSortKeyPutsSpaceAfterZ() {
        assertTrue(PluginDataPathRules.dirSortKey("a b").compareTo(PluginDataPathRules.dirSortKey("az")) > 0);
        assertEquals("abc", PluginDataPathRules.dirSortKey("ABC"));
    }

    @Test
    void includePathRespectsExcludeAndIncludeInExport(@TempDir Path root) {
        Path music = root.resolve("Music");
        Path archive = music.resolve("Archive");
        Path keep = archive.resolve("Keep");
        Path song = keep.resolve("song.abc");
        Path setExport = root.resolve("Sets");

        List<PluginDataPathRules.ExcludeRule> rules = List.of(
                new PluginDataPathRules.ExcludeRule(archive.toAbsolutePath().normalize().toString(), false),
                new PluginDataPathRules.ExcludeRule(keep.toAbsolutePath().normalize().toString(), true));

        assertTrue(PluginDataPathRules.includePathInSongbook(
                song, music, setExport, rules));

        Path excluded = archive.resolve("skip.abc");
        assertFalse(PluginDataPathRules.includePathInSongbook(
                excluded, music, setExport, rules));

        Path setSong = setExport.resolve("exported.abc");
        assertTrue(PluginDataPathRules.includePathInSongbook(
                setSong, music, setExport, rules));

        Path outside = root.resolve("elsewhere").resolve("x.abc");
        assertFalse(PluginDataPathRules.includePathInSongbook(
                outside, music, setExport, rules));
    }

    @Test
    void buildEmitsSongbookCompatibleLuaWithTranscriberAndArtist() {
        List<PluginDataSongEntry.Track> tracks = List.of(
                new PluginDataSongEntry.Track("1", "Hello"),
                new PluginDataSongEntry.Track("2", "Part \"Two\""));
        PluginDataSongEntry song = new PluginDataSongEntry(
                "/Band/",
                "hello",
                tracks,
                "Transcriber",
                "Artist");

        PluginDataLuaBuilder.BuildResult result = PluginDataLuaBuilder.build(List.of(song));
        String lua = result.lua();

        assertEquals(1, result.songCount());
        assertEquals(2, result.directoryCount()); // / and /Band/
        assertTrue(lua.startsWith("return\n{"));
        assertTrue(lua.contains("[\"Directories\"] ="));
        assertTrue(lua.contains("[\"Songs\"] ="));
        assertTrue(lua.contains("[\"Filepath\"] = \"/Band/\""));
        assertTrue(lua.contains("[\"Filename\"] = \"hello\""));
        assertTrue(lua.contains("[\"Id\"] =\"1\""));
        assertTrue(lua.contains("[\"Name\"] =\"Hello\""));
        assertTrue(lua.contains("[\"Name\"] =\"Part \\\"Two\\\"\""));
        assertTrue(lua.contains("[\"Transcriber\"] = \"Transcriber\""));
        assertTrue(lua.contains("[\"Artist\"] = \"Artist\""));
        assertTrue(lua.contains("[1] = \"/\""));
        assertTrue(lua.contains("[2] = \"/Band/\""));
    }

    @Test
    void tracksFromPartsUsesTitleFromTThenTitleThenPartName() {
        List<ExportPartMeta> parts = List.of(
                new ExportPartMeta(1, "Drum", "From T", null),
                new ExportPartMeta(2, "Lute", "", null),
                new ExportPartMeta(3, "", "", null));
        List<PluginDataSongEntry.Track> tracks =
                PluginDataLuaBuilder.tracksFromParts(parts, "Song Title");
        assertEquals("From T", tracks.get(0).name());
        assertEquals("Song Title", tracks.get(1).name());
        assertEquals("Song Title", tracks.get(2).name());
    }

    @Test
    void emptyPartsYieldDefaultTrack() {
        List<PluginDataSongEntry.Track> tracks =
                PluginDataLuaBuilder.tracksFromParts(List.of(), "Title");
        assertEquals(1, tracks.size());
        assertEquals("1", tracks.get(0).id());
        assertEquals("Part 1", tracks.get(0).name());
    }

    @Test
    void goldenMinimalLibraryMatchesExpectedShape() {
        PluginDataSongEntry rootSong = new PluginDataSongEntry(
                "/",
                "hello",
                List.of(new PluginDataSongEntry.Track("1", "Hello")),
                "",
                "Unknown");
        PluginDataSongEntry nested = new PluginDataSongEntry(
                "/Folk/",
                "jig",
                List.of(new PluginDataSongEntry.Track("1", "Jig")),
                "Ann",
                "Traditional");

        String lua = PluginDataLuaBuilder.build(List.of(nested, rootSong)).lua();
        String expected = """
                return
                {
                	["Directories"] =
                	{
                		[1] = "/",
                		[2] = "/Folk/",
                	},
                	["Songs"] =
                	{
                		[1] =
                		{
                			["Filepath"] = "/Folk/",
                			["Filename"] = "jig",
                			["Tracks"] =
                			{
                				[1] =
                				{
                					["Id"] ="1",
                					["Name"] ="Jig"
                				},
                			},
                			["Transcriber"] = "Ann",
                			["Artist"] = "Traditional"
                		},
                		[2] =
                		{
                			["Filepath"] = "/",
                			["Filename"] = "hello",
                			["Tracks"] =
                			{
                				[1] =
                				{
                					["Id"] ="1",
                					["Name"] ="Hello"
                				},
                			},
                			["Transcriber"] = "",
                			["Artist"] = "Unknown"
                		},
                	}
                }""".replace("\r\n", "\n");
        assertEquals(expected, lua.replace("\r\n", "\n"));
    }
}

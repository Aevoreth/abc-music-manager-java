package com.aevoreth.abcmm.domain.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;

class SetExportHelpersTest {

    @Test
    void formatFilenameUsesSongIndexAndFileStem() {
        String name = SetFilenameTemplate.formatFilename(
                "$SongIndex_$FileName",
                "C:/music/my song.abc",
                0,
                "Title",
                "Composer",
                null,
                65,
                2,
                " ",
                true);
        assertEquals("001_my song.abc", name);
    }

    @Test
    void formatFilenamePadsSongLengthWithUnderscore() {
        Map<String, String> vars = SetFilenameTemplate.buildSongVariableMap(
                "a.abc",
                2,
                "T",
                "C",
                null,
                125,
                3,
                true,
                "_",
                SetFilenameTemplate.SongLengthPad.BOTH);
        assertEquals("02_05", vars.get("$SongLength"));
        assertEquals("003", vars.get("$SongIndex"));
    }

    @Test
    void formatPartNameUsesColonDuration() {
        String title = SetFilenameTemplate.formatPartName(
                "$SongLength-$PartTitle",
                "a.abc",
                0,
                "Song",
                "C",
                null,
                125,
                1,
                "Lute",
                "Flute",
                "Part Title",
                "1",
                "Alice",
                "",
                " ",
                true);
        assertEquals("2:05-Part Title", title);
    }

    @Test
    void computePartNumerationMarksDuplicates() {
        List<ExportPartMeta> parts = List.of(
                new ExportPartMeta(1, "Flute", "A", null),
                new ExportPartMeta(2, "Flute", "B", null),
                new ExportPartMeta(3, "Lute", "C", null));
        Map<Integer, String> numer = SetFilenameTemplate.computePartNumeration(parts);
        assertEquals("1", numer.get(1));
        assertEquals("2", numer.get(2));
        assertEquals("", numer.get(3));
    }

    @Test
    void whitespaceRemoveAndCaps() {
        assertEquals("HelloWorld", SetFilenameTemplate.applyWhitespaceReplace("hello world", "RemoveAndCaps"));
    }

    @Test
    void rewritesFirstTLinePerXBlock() {
        String abc = "X:1\nT:Old\nM:4/4\nX:2\nT:Keep\n";
        String out = AbcPartTitleRewrite.rewriteAbcPartTLines(abc, Map.of(1, "New Title"));
        assertTrue(out.contains("T: New Title"));
        assertTrue(out.contains("T:Keep"));
        assertFalse(out.contains("T:Old"));
    }

    @Test
    void insertsTWhenMissing() {
        String abc = "X:1\nM:4/4\n";
        String out = AbcPartTitleRewrite.rewriteAbcPartTLines(abc, Map.of(1, "Inserted"));
        assertTrue(out.contains("T: Inserted"));
    }

    @Test
    void writesXml11Playlist(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("set.abcp");
        AbcpWriter.write(path, List.of("001_a.abc", "002_b.abc"));
        String text = Files.readString(path);
        assertTrue(text.startsWith("<?xml version=\"1.1\""));
        assertTrue(text.contains("fileVersion=\"3.4.0.300\""));
        assertTrue(text.contains("<location>001_a.abc</location>"));
        assertTrue(text.contains("<location>002_b.abc</location>"));
    }

    @Test
    void applyDisplayRenamesInOrder() {
        List<SetExportSettings.FindReplaceRule> rules = List.of(
                new SetExportSettings.FindReplaceRule("Basic Theorbo", "Theorbo"),
                new SetExportSettings.FindReplaceRule("Misty Mountain Harp", "MMH"));
        assertEquals(
                "Theorbo / MMH",
                CsvPartSheet.applyDisplayRenames("Basic Theorbo / Misty Mountain Harp", rules));
    }

    @Test
    void ordersBySavedThenAppendsNew() {
        List<BandLayoutSlotInfo> slots = List.of(
                new BandLayoutSlotInfo(1, 1, 10, "A", 0, 0, 9, 7),
                new BandLayoutSlotInfo(2, 1, 20, "B", 9, 0, 9, 7),
                new BandLayoutSlotInfo(3, 1, 30, "C", 0, 7, 9, 7));
        List<BandLayoutSlotInfo> ordered =
                LayoutExportOrder.listSlotsForExport(slots, List.of(20L, 99L, 10L));
        assertEquals(20L, ordered.get(0).playerId());
        assertEquals(10L, ordered.get(1).playerId());
        assertEquals(30L, ordered.get(2).playerId());
    }

    @Test
    void roundTripsExportColumnOrderJson() {
        assertEquals(List.of(1L, 2L, 3L), LayoutExportOrder.parseExportColumnOrderJson("[1, 2, 3]"));
        assertEquals("[1, 2, 3]", LayoutExportOrder.toExportColumnOrderJson(List.of(1L, 2L, 3L)));
    }
}

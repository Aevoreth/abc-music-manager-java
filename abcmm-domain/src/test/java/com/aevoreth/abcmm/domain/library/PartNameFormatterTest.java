package com.aevoreth.abcmm.domain.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.prefs.Preferences;

class PartNameFormatterTest {

    @Test
    void substitutesPartNumberAndInstrument() {
        PartNameFormatter.SongContext song = new PartNameFormatter.SongContext(
                "My Song", "Ada", "Bea", 125, 2, "tune.abc", null, null);
        PartNameFormatter.PartContext part = new PartNameFormatter.PartContext(51, "Flute", "Basic Flute");
        String out = PartNameFormatter.format(
                "$PartNumber: $PartName ($PartInstrument)",
                Preferences.DEFAULT_PART_NAME_WHITESPACE_REPLACE,
                song,
                part);
        assertEquals("51: Flute (Basic Flute)", out);
    }

    @Test
    void replacesWhitespaceInVariableValues() {
        PartNameFormatter.SongContext song = new PartNameFormatter.SongContext(
                "My Song", "", "", 0, 1, "a.abc", null, null);
        PartNameFormatter.PartContext part = new PartNameFormatter.PartContext(1, "Basic Flute", "");
        String out = PartNameFormatter.format("$PartName", "_", song, part);
        assertEquals("Basic_Flute", out);
    }

    @Test
    void formatsSongLengthAsMmSs() {
        PartNameFormatter.SongContext song = new PartNameFormatter.SongContext(
                "T", "", "", 65, 1, "a.abc", null, null);
        PartNameFormatter.PartContext part = new PartNameFormatter.PartContext(1, "P", "");
        assertEquals("1:05", PartNameFormatter.format("$SongLength", " ", song, part));
    }
}

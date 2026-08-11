package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbcMetadataRewriterTest {

    @Test
    void maestroTagValuesStartAtColumn20() {
        assertEquals("%%song-title       My Song", AbcMetadataRewriter.formatMaestroTag("song-title", "My Song"));
        assertEquals("%%song-composer    Ada", AbcMetadataRewriter.formatMaestroTag("song-composer", "Ada"));
        assertEquals(19, "%%song-title       ".length());
        assertEquals(19, "%%song-composer    ".length());
    }

    @Test
    void updatesSongTitleTagAndReplacesTitleInTLines() {
        String abc = """
                %%song-title       Old Title
                %%song-composer    Ada
                T: Old Title - Ada
                C: Ada
                X:1
                T: Old Title Melody
                """;
        String out = AbcMetadataRewriter.applyTitleAndComposer(
                abc, "Old Title", "New Title", null, null);
        assertEquals("""
                %%song-title       New Title
                %%song-composer    Ada
                T: New Title - Ada
                C: Ada
                X:1
                T: New Title Melody
                """, out);
    }

    @Test
    void updatesSongComposerTagAllCLinesAndComposerInTLines() {
        String abc = """
                %%song-title       Tune
                %%song-composer    Old Comp
                T: Tune - Old Comp
                C: Old Comp
                X:1
                C: Old Comp
                T: Part - Old Comp
                """;
        String out = AbcMetadataRewriter.applyTitleAndComposer(
                abc, null, null, "Old Comp", "New Comp");
        assertEquals("""
                %%song-title       Tune
                %%song-composer    New Comp
                T: Tune - New Comp
                C: New Comp
                X:1
                C: New Comp
                T: Part - New Comp
                """, out);
    }

    @Test
    void insertsMissingMaestroTagsAtTopPaddedToColumn20() {
        String abc = """
                T: Solo
                C: Anon
                X:1
                """;
        String out = AbcMetadataRewriter.applyTitleAndComposer(
                abc, "Solo", "Duet", "Anon", "Bea");
        assertEquals("""
                %%song-title       Duet
                %%song-composer    Bea
                T: Duet
                C: Bea
                X:1
                """, out);
        assertTrue(out.startsWith("%%song-title       Duet"));
    }

    @Test
    void noOpWhenValuesUnchanged() {
        String abc = "%%song-title       A\nT: A\n";
        assertEquals(abc, AbcMetadataRewriter.applyTitleAndComposer(abc, "A", "A", "C", "C"));
        assertEquals(abc, AbcMetadataRewriter.applyTitleAndComposer(abc, null, null, null, null));
    }

    @Test
    void replacesEntireCBodyEvenWhenOldComposerAbsent() {
        String abc = "%%song-composer    Old\nC: Someone Else\n";
        String out = AbcMetadataRewriter.applyTitleAndComposer(abc, null, null, "Old", "New");
        assertEquals("%%song-composer    New\nC: New\n", out);
        assertFalse(out.contains("Someone Else"));
    }

    @Test
    void appliesTitleThenComposerOnSameTLine() {
        String abc = "%%song-title       Foo\n%%song-composer    Bar\nT: Foo / Bar\n";
        String out = AbcMetadataRewriter.applyTitleAndComposer(abc, "Foo", "Baz", "Bar", "Qux");
        assertEquals("%%song-title       Baz\n%%song-composer    Qux\nT: Baz / Qux\n", out);
    }

    @Test
    void realignsCompactTagsToColumn20OnUpdate() {
        String abc = "%%song-title Old\n%%song-composer X\n";
        String out = AbcMetadataRewriter.applyTitleAndComposer(abc, "Old", "New", "X", "Y");
        assertEquals("%%song-title       New\n%%song-composer    Y\n", out);
    }
}

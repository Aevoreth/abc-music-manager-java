package com.aevoreth.abcmm.domain.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AbcDiffTest {

    @Test
    void diffsChangedLines() {
        List<AbcDiff.DiffLine> lines = AbcDiff.diff("A\nB\nC\n", "A\nX\nC\n");
        assertTrue(lines.stream().anyMatch(l -> l.kind() == AbcDiff.Kind.LEFT_ONLY || l.kind() == AbcDiff.Kind.RIGHT_ONLY));
        assertEquals("A", lines.get(0).left());
    }

    @Test
    void toHtmlContainsBothPaths() {
        String html = AbcDiff.toHtml("left.abc", "right.abc", "X:1\n", "X:2\n");
        assertTrue(html.contains("left.abc"));
        assertTrue(html.contains("right.abc"));
    }
}

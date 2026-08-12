package com.aevoreth.abcmm.domain.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class DuplicateCleanupPlanValidatorTest {

    @Test
    void rejectsUnresolvedFiles() {
        DuplicateGroup group = sampleGroup();
        DuplicateAnalysis analysis = new DuplicateAnalysis(List.of(group), List.of(), 2, 0, null);
        DuplicateCleanupPlan plan = new DuplicateCleanupPlan(List.of(
                new FileResolution(group.groupId(), group.files().get(0).path(), FileDisposition.KEEP)),
                List.of());
        List<String> errors = DuplicateCleanupPlanValidator.validate(analysis, plan);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Unresolved") || e.contains("Incomplete") || e.contains("missing")));
    }

    @Test
    void rejectsMultipleSongKeepWithoutBind() {
        Path p1 = Path.of("C:/Music/one.abc");
        Path p2 = Path.of("C:/Music/two.abc");
        AbcFileMetadata meta = new AbcFileMetadata("Same", "Ada", null, null, null, List.of());
        DuplicateGroup group = new DuplicateGroup("g1", DuplicateMatchType.STRONG_METADATA_MATCH, List.of(
                new DuplicateFile(p1, meta, "h1", null, 10L, true, Path.of("C:/Music")),
                new DuplicateFile(p2, meta, "h2", null, 20L, true, Path.of("C:/Music"))));
        DuplicateAnalysis analysis = new DuplicateAnalysis(List.of(group), List.of(), 2, 0, null);
        DuplicateCleanupPlan plan = new DuplicateCleanupPlan(List.of(
                new FileResolution("g1", p1, FileDisposition.KEEP, null),
                new FileResolution("g1", p2, FileDisposition.IGNORE, null)), List.of());
        List<String> errors = DuplicateCleanupPlanValidator.validate(analysis, plan);
        assertTrue(errors.stream().anyMatch(e -> e.contains("bindSongId")));
    }

    @Test
    void acceptsValidPlan() {
        DuplicateGroup group = sampleGroup();
        DuplicateAnalysis analysis = new DuplicateAnalysis(List.of(group), List.of(), 2, 0, null);
        DuplicateCleanupPlan plan = new DuplicateCleanupPlan(List.of(
                new FileResolution(group.groupId(), group.files().get(0).path(), FileDisposition.KEEP),
                new FileResolution(group.groupId(), group.files().get(1).path(), FileDisposition.IGNORE)),
                List.of());
        assertTrue(DuplicateCleanupPlanValidator.validate(analysis, plan).isEmpty());
    }

    @Test
    void validatePartialAllowsOmittingUnresolvedGroups() {
        DuplicateGroup keep = sampleGroup();
        Path p3 = Path.of("C:/Music/c.abc");
        Path p4 = Path.of("C:/Music/d.abc");
        AbcFileMetadata meta = new AbcFileMetadata("Other", "C", null, null, null, List.of());
        DuplicateGroup omit = new DuplicateGroup("g2", DuplicateMatchType.EXACT_FILE, List.of(
                new DuplicateFile(p3, meta, "h2", null, null, false, Path.of("C:/Music")),
                new DuplicateFile(p4, meta, "h2", null, null, false, Path.of("C:/Music"))));
        DuplicateAnalysis analysis = new DuplicateAnalysis(List.of(keep, omit), List.of(), 4, 0, null);
        DuplicateCleanupPlan partial = new DuplicateCleanupPlan(List.of(
                new FileResolution(keep.groupId(), keep.files().get(0).path(), FileDisposition.KEEP),
                new FileResolution(keep.groupId(), keep.files().get(1).path(), FileDisposition.TRASH)),
                List.of());
        assertTrue(DuplicateCleanupPlanValidator.validatePartial(analysis, partial).isEmpty());
        assertFalse(DuplicateCleanupPlanValidator.validate(analysis, partial).isEmpty());
    }

    @Test
    void validatePartialRejectsEmptyPlan() {
        DuplicateGroup group = sampleGroup();
        DuplicateAnalysis analysis = new DuplicateAnalysis(List.of(group), List.of(), 2, 0, null);
        assertFalse(DuplicateCleanupPlanValidator.validatePartial(analysis, DuplicateCleanupPlan.empty()).isEmpty());
    }

    private static DuplicateGroup sampleGroup() {
        Path p1 = Path.of("C:/Music/a.abc");
        Path p2 = Path.of("C:/Music/b.abc");
        AbcFileMetadata meta = new AbcFileMetadata("T", "C", null, null, null, List.of());
        return new DuplicateGroup("g", DuplicateMatchType.EXACT_FILE, List.of(
                new DuplicateFile(p1, meta, "h", null, null, false, Path.of("C:/Music")),
                new DuplicateFile(p2, meta, "h", null, null, false, Path.of("C:/Music"))));
    }
}

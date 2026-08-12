package com.aevoreth.abcmm.domain.export;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Include/exclude logic for Songbook PluginData export (Python {@code plugindata_writer}).
 *
 * <p>Include: under set-export dir; or under Music and (not under any exclude, or the most
 * specific matching exclude has {@code includeInExport}).
 */
public final class PluginDataPathRules {

    private PluginDataPathRules() {
    }

    /**
     * Resolved exclude path plus whether files under it are still included in songbook export.
     */
    public record ExcludeRule(String resolvedPath, boolean includeInExport) {
        public ExcludeRule {
            Objects.requireNonNull(resolvedPath, "resolvedPath");
        }
    }

    /**
     * True if {@code path} should be included in SongbookData.
     */
    public static boolean includePathInSongbook(
            Path path,
            Path musicRoot,
            Path setExportDir,
            List<ExcludeRule> excludeRules) {
        Path normalized = normalize(path);
        if (normalized == null) {
            return false;
        }
        Path setNorm = normalize(setExportDir);
        if (setNorm != null && isUnder(normalized, setNorm)) {
            return true;
        }
        Path musicNorm = normalize(musicRoot);
        if (musicNorm == null || !isUnder(normalized, musicNorm)) {
            return false;
        }
        ExcludeRule rule = mostSpecificExcludeRule(normalized, excludeRules);
        if (rule == null) {
            return true;
        }
        return rule.includeInExport();
    }

    /**
     * Return the exclude rule with the longest resolved path that contains {@code path}, or null.
     */
    public static ExcludeRule mostSpecificExcludeRule(Path path, List<ExcludeRule> rules) {
        if (path == null || rules == null || rules.isEmpty()) {
            return null;
        }
        Path normalized = normalize(path);
        if (normalized == null) {
            return null;
        }
        return rules.stream()
                .filter(r -> {
                    Path pre = normalize(Path.of(r.resolvedPath()));
                    return pre != null && isUnder(normalized, pre);
                })
                .max(Comparator.comparingInt(r -> r.resolvedPath().length()))
                .orElse(null);
    }

    /**
     * Case-insensitive sort key; space sorts after {@code z} (Python {@code _dir_sort_key}).
     */
    public static String dirSortKey(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase().replace(' ', '\u007f');
    }

    /**
     * Escape a string for a Lua double-quoted literal.
     */
    public static String luaEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static Path normalize(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static boolean isUnder(Path path, Path prefix) {
        if (path == null || prefix == null) {
            return false;
        }
        Path p = path.toAbsolutePath().normalize();
        Path pre = prefix.toAbsolutePath().normalize();
        return p.startsWith(pre);
    }
}

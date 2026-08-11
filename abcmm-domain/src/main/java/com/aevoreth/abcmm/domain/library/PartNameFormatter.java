package com.aevoreth.abcmm.domain.library;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.aevoreth.abcmm.domain.prefs.Preferences;

/**
 * Maestro-style part naming template: substitutes {@code $Variable} tokens and optional
 * whitespace replacement in variable values.
 */
public final class PartNameFormatter {

    public static final String SPACE_REPLACE_REMOVE_AND_CAPS = "RemoveAndCaps";

    public static final String[] SPACE_REPLACE_CHARS = {
            " ", "", "_", "-", SPACE_REPLACE_REMOVE_AND_CAPS
    };

    public static final String[] SPACE_REPLACE_LABELS = {
            "Don't Replace",
            "Remove Spaces",
            "_ (Underscore)",
            "- (Dash)",
            "Remove Spaces and Capitalize first letter"
    };

    private static final Pattern VARIABLE = Pattern.compile("\\$[A-Za-z]+");

    private PartNameFormatter() {
    }

    /**
     * Variable names and short descriptions shown in the Parts tab reference table.
     */
    public static Map<String, String> variableDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("$SongTitle", "The song title");
        map.put("$SongLength", "Duration as mm:ss");
        map.put("$SongComposer", "Composer(s)");
        map.put("$SongTranscriber", "Transcriber");
        map.put("$PartName", "Current part name (before Apply)");
        map.put("$PartNumber", "Part number");
        map.put("$PartCount", "Number of parts");
        map.put("$PartInstrument", "Instrument (Made-for)");
        map.put("$FilePath", "ABC path relative to Music folder when possible");
        map.put("$FileName", "ABC filename without .abc");
        return Map.copyOf(map);
    }

    public static String format(
            String pattern,
            String whitespaceReplace,
            SongContext song,
            PartContext part) {
        Objects.requireNonNull(song, "song");
        Objects.requireNonNull(part, "part");
        String name = pattern == null || pattern.isBlank()
                ? Preferences.DEFAULT_PART_NAME_PATTERN
                : pattern;
        String replace = whitespaceReplace == null
                ? Preferences.DEFAULT_PART_NAME_WHITESPACE_REPLACE
                : whitespaceReplace;

        Matcher matcher = VARIABLE.matcher(name);
        List<int[]> matches = new java.util.ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[] {matcher.start(), matcher.end()});
        }

        for (int i = matches.size() - 1; i >= 0; i--) {
            int start = matches.get(i)[0];
            int end = matches.get(i)[1];
            String token = name.substring(start, end);
            String value = resolve(token, song, part);
            if (value == null) {
                continue;
            }
            String substituted = applyWhitespace(value, replace);
            name = name.substring(0, start) + substituted + name.substring(end);
        }
        return name;
    }

    public static int spaceReplaceIndex(String whitespaceReplace) {
        String value = whitespaceReplace == null
                ? Preferences.DEFAULT_PART_NAME_WHITESPACE_REPLACE
                : whitespaceReplace;
        for (int i = 0; i < SPACE_REPLACE_CHARS.length; i++) {
            if (SPACE_REPLACE_CHARS[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static String resolve(String token, SongContext song, PartContext part) {
        return switch (token) {
            case "$SongTitle" -> nullToEmpty(song.title());
            case "$SongLength" -> formatDuration(song.durationSeconds());
            case "$SongComposer" -> nullToEmpty(song.composers());
            case "$SongTranscriber" -> nullToEmpty(song.transcriber());
            case "$PartName" -> nullToEmpty(part.partName());
            case "$PartNumber" -> String.valueOf(part.partNumber());
            case "$PartCount" -> String.valueOf(Math.max(0, song.partCount()));
            case "$PartInstrument" -> nullToEmpty(part.madeFor());
            case "$FilePath" -> filePath(song);
            case "$FileName" -> fileNameWithoutAbc(song.fileName());
            default -> null;
        };
    }

    private static String applyWhitespace(String value, String replace) {
        if (SPACE_REPLACE_REMOVE_AND_CAPS.equals(replace)) {
            return Arrays.stream(value.split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(Collectors.joining(""));
        }
        return value.replaceAll("\\s+", replace);
    }

    private static String formatDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds < 0) {
            return "";
        }
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static String filePath(SongContext song) {
        Path path = song.filePath();
        if (path == null) {
            return fileNameWithoutAbc(song.fileName());
        }
        Path lotroRoot = song.lotroMusicRoot();
        if (lotroRoot != null) {
            try {
                Path absolute = path.toAbsolutePath().normalize();
                Path root = lotroRoot.toAbsolutePath().normalize();
                if (absolute.startsWith(root)) {
                    Path relative = root.relativize(absolute);
                    String asString = relative.toString().replace('\\', '/');
                    if (asString.toLowerCase(Locale.ROOT).endsWith(".abc")) {
                        asString = asString.substring(0, asString.length() - 4);
                    }
                    return asString;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return fileNameWithoutAbc(path.getFileName() == null ? null : path.getFileName().toString());
    }

    private static String fileNameWithoutAbc(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String name = fileName;
        if (name.toLowerCase(Locale.ROOT).endsWith(".abc")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Song-level fields available to the template.
     */
    public record SongContext(
            String title,
            String composers,
            String transcriber,
            Integer durationSeconds,
            int partCount,
            String fileName,
            Path filePath,
            Path lotroMusicRoot) {
    }

    /**
     * Per-part fields available to the template.
     */
    public record PartContext(int partNumber, String partName, String madeFor) {
    }
}

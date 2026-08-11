package com.aevoreth.abcmm.domain.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aevoreth.abcmm.domain.library.PartNameFormatter;

/**
 * Maestro-inspired filename / part T: pattern substitution for set export
 * (Python {@code filename_template.py}).
 */
public final class SetFilenameTemplate {

    public static final String[] SPACE_REPLACE_CHARS = PartNameFormatter.SPACE_REPLACE_CHARS;
    public static final String[] SPACE_REPLACE_LABELS = PartNameFormatter.SPACE_REPLACE_LABELS;

    private static final Pattern VARIABLE = Pattern.compile("\\$[A-Za-z]+");

    private SetFilenameTemplate() {
    }

    public enum SongLengthPad {
        BOTH,
        SECONDS
    }

    public static Map<String, String> buildSongVariableMap(
            String filePath,
            int index,
            String title,
            String composers,
            String transcriber,
            Integer durationSeconds,
            int partCount,
            boolean partCountZeroPadded,
            String songLengthSep,
            SongLengthPad songLengthPad) {
        String filenameStem = "unknown";
        if (filePath != null && !filePath.isBlank()) {
            Path path = Path.of(filePath);
            Path name = path.getFileName();
            if (name != null) {
                String fileName = name.toString();
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".abc")) {
                    filenameStem = fileName.substring(0, fileName.length() - 4);
                } else {
                    filenameStem = fileName;
                }
            }
        }

        String durationStr = "";
        if (durationSeconds != null) {
            int m = durationSeconds / 60;
            int s = durationSeconds % 60;
            if (songLengthPad == SongLengthPad.BOTH) {
                durationStr = String.format(Locale.ROOT, "%02d%s%02d", m, songLengthSep, s);
            } else {
                durationStr = String.format(Locale.ROOT, "%d%s%02d", m, songLengthSep, s);
            }
        }

        String partFmt = partCountZeroPadded
                ? String.format(Locale.ROOT, "%02d", partCount)
                : String.valueOf(partCount);
        String indexFmt = String.format(Locale.ROOT, "%03d", index + 1);

        Map<String, String> map = new LinkedHashMap<>();
        map.put("$FileName", filenameStem);
        map.put("$SongIndex", indexFmt);
        map.put("$PartCount", partFmt);
        map.put("$SongComposer", composers == null ? "" : composers);
        map.put("$SongTranscriber", transcriber == null ? "" : transcriber);
        map.put("$SongLength", durationStr);
        map.put("$SongTitle", title == null ? "" : title);
        return map;
    }

    /**
     * Map part_number → numeration: empty if unique %%part-name, else 1,2,… among duplicates.
     */
    public static Map<Integer, String> computePartNumeration(List<ExportPartMeta> parts) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (ExportPartMeta part : parts) {
            int pn = part.partNumber();
            if (pn <= 0) {
                continue;
            }
            String key = part.partName() == null ? "" : part.partName();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(pn);
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (List<Integer> pnums : groups.values()) {
            if (pnums.size() <= 1) {
                result.put(pnums.get(0), "");
            } else {
                for (int i = 0; i < pnums.size(); i++) {
                    result.put(pnums.get(i), String.valueOf(i + 1));
                }
            }
        }
        return result;
    }

    public static String formatFilename(
            String pattern,
            String filePath,
            int index,
            String title,
            String composers,
            String transcriber,
            Integer durationSeconds,
            int partCount,
            String whitespaceReplace,
            boolean partCountZeroPadded) {
        Map<String, String> variables = buildSongVariableMap(
                filePath,
                index,
                title,
                composers,
                transcriber,
                durationSeconds,
                partCount,
                partCountZeroPadded,
                "_",
                SongLengthPad.BOTH);
        String result = substitute(pattern, variables, whitespaceReplace);
        if (!result.toLowerCase(Locale.ROOT).endsWith(".abc")) {
            result += ".abc";
        }
        return result;
    }

    public static String formatPartName(
            String pattern,
            String filePath,
            int index,
            String title,
            String composers,
            String transcriber,
            Integer durationSeconds,
            int partCount,
            String partInstrument,
            String partName,
            String partTitle,
            String partNumberDisplay,
            String playerAssignment,
            String numeration,
            String whitespaceReplace,
            boolean partCountZeroPadded) {
        Map<String, String> variables = buildSongVariableMap(
                filePath,
                index,
                title,
                composers,
                transcriber,
                durationSeconds,
                partCount,
                partCountZeroPadded,
                ":",
                SongLengthPad.SECONDS);
        variables.put("$PartInstrument", partInstrument == null ? "" : partInstrument);
        variables.put("$PartName", partName == null ? "" : partName);
        variables.put("$PartTitle", partTitle == null ? "" : partTitle);
        variables.put("$PartNumber", partNumberDisplay == null ? "" : partNumberDisplay);
        variables.put("$PlayerAssignment", playerAssignment == null ? "" : playerAssignment);
        variables.put("$Numeration", numeration == null ? "" : numeration);
        return substitute(pattern, variables, whitespaceReplace);
    }

    private static String substitute(
            String pattern,
            Map<String, String> variables,
            String whitespaceReplace) {
        String name = pattern == null ? "" : pattern;
        String replace = whitespaceReplace == null ? " " : whitespaceReplace;
        Matcher matcher = VARIABLE.matcher(name);
        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[] {matcher.start(), matcher.end()});
        }
        for (int i = matches.size() - 1; i >= 0; i--) {
            int start = matches.get(i)[0];
            int end = matches.get(i)[1];
            String token = name.substring(start, end);
            String value = variables.getOrDefault(token, token);
            value = applyWhitespaceReplace(value, replace);
            name = name.substring(0, start) + value + name.substring(end);
        }
        return name;
    }

    static String applyWhitespaceReplace(String value, String replaceWith) {
        if (" ".equals(replaceWith)) {
            return value;
        }
        if ("".equals(replaceWith)) {
            return value.replace(" ", "").replace("\t", "");
        }
        if ("_".equals(replaceWith)) {
            return value.replaceAll("\\s+", "_");
        }
        if ("-".equals(replaceWith)) {
            return value.replaceAll("\\s+", "-");
        }
        if (PartNameFormatter.SPACE_REPLACE_REMOVE_AND_CAPS.equals(replaceWith)) {
            String[] words = value.strip().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) {
                    continue;
                }
                if (w.length() == 1) {
                    sb.append(w.toUpperCase(Locale.ROOT));
                } else {
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
            }
            return sb.toString();
        }
        return value;
    }
}

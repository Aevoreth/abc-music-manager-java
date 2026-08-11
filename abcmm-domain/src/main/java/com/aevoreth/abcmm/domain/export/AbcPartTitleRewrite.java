package com.aevoreth.abcmm.domain.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrite first {@code T:} line per {@code X:} block for set export part renaming.
 */
public final class AbcPartTitleRewrite {

    private static final Pattern X_LINE = Pattern.compile("^X:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private AbcPartTitleRewrite() {
    }

    public static String sanitizeTTitleValue(String s) {
        if (s == null) {
            return "";
        }
        return String.join(" ", s.replace('\r', ' ').replace('\n', ' ').split("\\s+")).strip();
    }

    public static String rewriteAbcPartTLines(String content, Map<Integer, String> partNumToTitle) {
        if (content == null || partNumToTitle == null || partNumToTitle.isEmpty()) {
            return content == null ? "" : content;
        }

        List<String> lines = splitKeepEnds(content);
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = lines.size();

        while (i < n) {
            String raw = lines.get(i);
            String sl = stripLineEnding(raw).strip();
            Matcher m = X_LINE.matcher(sl);
            if (!m.find()) {
                out.add(raw);
                i++;
                continue;
            }

            int partNum = Integer.parseInt(m.group(1));
            out.add(raw);
            i++;

            int blockStart = i;
            Integer firstTIdx = null;
            while (i < n) {
                String sl2 = stripLineEnding(lines.get(i)).strip();
                if (X_LINE.matcher(sl2).find()) {
                    break;
                }
                if (firstTIdx == null && sl2.length() >= 2 && sl2.regionMatches(true, 0, "T:", 0, 2)) {
                    firstTIdx = i;
                }
                i++;
            }

            String newTitle = partNumToTitle.get(partNum);
            if (newTitle == null) {
                out.addAll(lines.subList(blockStart, i));
                continue;
            }

            String safe = sanitizeTTitleValue(newTitle);
            String tBody = safe.isEmpty() ? "T:" : "T: " + safe;

            if (firstTIdx != null) {
                for (int j = blockStart; j < i; j++) {
                    if (j == firstTIdx) {
                        out.add(tBody + lineEnding(lines.get(j)));
                    } else {
                        out.add(lines.get(j));
                    }
                }
            } else {
                String ending = blockStart < i ? lineEnding(lines.get(blockStart)) : "\n";
                out.add(tBody + ending);
                out.addAll(lines.subList(blockStart, i));
            }
        }

        return String.join("", out);
    }

    private static List<String> splitKeepEnds(String content) {
        List<String> lines = new ArrayList<>();
        int i = 0;
        int n = content.length();
        while (i < n) {
            int start = i;
            while (i < n && content.charAt(i) != '\n' && content.charAt(i) != '\r') {
                i++;
            }
            if (i < n) {
                if (content.charAt(i) == '\r' && i + 1 < n && content.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
            }
            lines.add(content.substring(start, i));
        }
        return lines;
    }

    private static String stripLineEnding(String line) {
        if (line.endsWith("\r\n")) {
            return line.substring(0, line.length() - 2);
        }
        if (line.endsWith("\n") || line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private static String lineEnding(String line) {
        if (line.endsWith("\r\n")) {
            return "\r\n";
        }
        if (line.endsWith("\r")) {
            return "\r";
        }
        return "\n";
    }
}

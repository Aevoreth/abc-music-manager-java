package com.aevoreth.abcmm.domain.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-oriented ABC text comparison for side-by-side duplicate review.
 */
public final class AbcDiff {

    public enum Kind {
        EQUAL,
        LEFT_ONLY,
        RIGHT_ONLY,
        CHANGE
    }

    public record DiffLine(Kind kind, String left, String right) {
    }

    private AbcDiff() {
    }

    public static List<DiffLine> diff(String leftText, String rightText) {
        String[] left = splitLines(leftText);
        String[] right = splitLines(rightText);
        int[][] lcs = new int[left.length + 1][right.length + 1];
        for (int i = left.length - 1; i >= 0; i--) {
            for (int j = right.length - 1; j >= 0; j--) {
                if (left[i].equals(right[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        List<DiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.length && j < right.length) {
            if (left[i].equals(right[j])) {
                lines.add(new DiffLine(Kind.EQUAL, left[i], right[j]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                lines.add(new DiffLine(Kind.LEFT_ONLY, left[i], ""));
                i++;
            } else {
                lines.add(new DiffLine(Kind.RIGHT_ONLY, "", right[j]));
                j++;
            }
        }
        while (i < left.length) {
            lines.add(new DiffLine(Kind.LEFT_ONLY, left[i++], ""));
        }
        while (j < right.length) {
            lines.add(new DiffLine(Kind.RIGHT_ONLY, "", right[j++]));
        }
        return lines;
    }

    public static String toHtml(String leftPath, String rightPath, String leftText, String rightText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:monospace;font-size:11pt;'>");
        sb.append("<table width='100%' cellspacing='0' cellpadding='2'><tr>");
        sb.append("<th align='left'>").append(escape(leftPath)).append("</th>");
        sb.append("<th align='left'>").append(escape(rightPath)).append("</th></tr>");
        for (DiffLine line : diff(leftText, rightText)) {
            String leftBg = switch (line.kind()) {
                case LEFT_ONLY, CHANGE -> "#4a2020";
                default -> "transparent";
            };
            String rightBg = switch (line.kind()) {
                case RIGHT_ONLY, CHANGE -> "#204a20";
                default -> "transparent";
            };
            sb.append("<tr>");
            sb.append("<td style='background:").append(leftBg).append(";white-space:pre;'>")
                    .append(escape(line.left())).append("</td>");
            sb.append("<td style='background:").append(rightBg).append(";white-space:pre;'>")
                    .append(escape(line.right())).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "&nbsp;";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

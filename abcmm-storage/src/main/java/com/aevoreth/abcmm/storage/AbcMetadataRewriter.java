package com.aevoreth.abcmm.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites ABC Maestro tags and header lines when title/composer are edited in Song detail.
 *
 * <ul>
 *   <li>Title: set {@code %%song-title}; replace {@code oldTitle} with {@code newTitle} in every {@code T:} line
 *   <li>Composer: set {@code %%song-composer}; set every {@code C:} body to the new value; replace
 *       {@code oldComposers} with {@code newComposers} in every {@code T:} line
 * </ul>
 */
public final class AbcMetadataRewriter {

    private static final Pattern MAESTRO_TAG = Pattern.compile("^(%%([a-z]+(?:-[a-z]+)*)\\s*)(.*)$");
    /** 1-based column where Maestro tag values begin (e.g. {@code %%song-title} + spaces). */
    private static final int MAESTRO_VALUE_COLUMN = 20;

    private AbcMetadataRewriter() {
    }

    /**
     * Applies title and/or composer edits. Pass null for a side that should not change, or equal
     * old/new values to skip that side.
     */
    public static String applyTitleAndComposer(
            String content,
            String oldTitle,
            String newTitle,
            String oldComposers,
            String newComposers) {
        Objects.requireNonNull(content, "content");
        boolean updateTitle = newTitle != null && !Objects.equals(nullToEmpty(oldTitle), newTitle);
        boolean updateComposer =
                newComposers != null && !Objects.equals(nullToEmpty(oldComposers), newComposers);
        if (!updateTitle && !updateComposer) {
            return content;
        }

        String ending = detectLineEnding(content);
        String[] rawLines = content.split("\\R", -1);
        List<String> out = new ArrayList<>(rawLines.length + 2);
        boolean sawSongTitle = false;
        boolean sawSongComposer = false;

        for (String line : rawLines) {
            String stripped = line.strip();
            Matcher tag = MAESTRO_TAG.matcher(stripped);
            if (tag.matches()) {
                String tagName = tag.group(2).toLowerCase(Locale.ROOT);
                if (updateTitle && "song-title".equals(tagName)) {
                    out.add(preserveLeading(line, formatMaestroTag("song-title", newTitle)));
                    sawSongTitle = true;
                    continue;
                }
                if (updateComposer && "song-composer".equals(tagName)) {
                    out.add(preserveLeading(line, formatMaestroTag("song-composer", newComposers)));
                    sawSongComposer = true;
                    continue;
                }
            }

            if (stripped.startsWith("T:")) {
                String body = stripped.substring(2);
                String leadingSpace = body.startsWith(" ") || body.startsWith("\t") ? body.substring(0, 1) : "";
                String value = leadingSpace.isEmpty() ? body : body.substring(1);
                if (updateTitle && oldTitle != null && !oldTitle.isEmpty()) {
                    value = value.replace(oldTitle, newTitle);
                }
                if (updateComposer && oldComposers != null && !oldComposers.isEmpty()) {
                    value = value.replace(oldComposers, newComposers);
                }
                out.add(preserveLeading(line, "T:" + leadingSpace + value));
                continue;
            }

            if (updateComposer && stripped.startsWith("C:")) {
                String body = stripped.substring(2);
                String leadingSpace = body.startsWith(" ") || body.startsWith("\t") ? body.substring(0, 1) : "";
                out.add(preserveLeading(line, "C:" + leadingSpace + newComposers));
                continue;
            }

            out.add(line);
        }

        List<String> prefix = new ArrayList<>(2);
        if (updateTitle && !sawSongTitle) {
            prefix.add(formatMaestroTag("song-title", newTitle));
        }
        if (updateComposer && !sawSongComposer) {
            prefix.add(formatMaestroTag("song-composer", newComposers));
        }
        if (!prefix.isEmpty()) {
            out.addAll(0, prefix);
        }

        return String.join(ending, out);
    }

    /**
     * Formats {@code %%tag-name} so the value starts at column {@link #MAESTRO_VALUE_COLUMN}.
     * Example: {@code %%song-title       My Song} / {@code %%song-composer    Ada}.
     */
    static String formatMaestroTag(String tagName, String value) {
        String tag = "%%" + tagName;
        int pad = Math.max(1, MAESTRO_VALUE_COLUMN - 1 - tag.length());
        return tag + " ".repeat(pad) + (value == null ? "" : value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String detectLineEnding(String content) {
        if (content.contains("\r\n")) {
            return "\r\n";
        }
        if (content.contains("\r")) {
            return "\r";
        }
        return "\n";
    }

    /** Keep original indentation; replace the stripped content. */
    private static String preserveLeading(String originalLine, String newStrippedContent) {
        int i = 0;
        while (i < originalLine.length() && Character.isWhitespace(originalLine.charAt(i))) {
            i++;
        }
        return originalLine.substring(0, i) + newStrippedContent;
    }
}

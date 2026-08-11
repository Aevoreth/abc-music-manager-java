package com.aevoreth.abcmm.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites ABC Maestro tags and header lines when title/composer are edited in Song detail.
 *
 * <ul>
 *   <li>Title: set {@code %%song-title}; replace {@code oldTitle} with {@code newTitle} in every {@code T:} line
 *   <li>Composer: set {@code %%song-composer}; set every {@code C:} body to the new value; replace
 *       {@code oldComposers} with {@code newComposers} in every {@code T:} line
 *   <li>Parts: reorder full {@code X:} blocks and update {@code X:}, {@code %%part-name}, {@code %%made-for}
 * </ul>
 */
public final class AbcMetadataRewriter {

    private static final Pattern MAESTRO_TAG = Pattern.compile("^(%%([a-z]+(?:-[a-z]+)*)\\s*)(.*)$");
    private static final Pattern X_HEADER = Pattern.compile("^X:\\s*(\\d*)(.*)$", Pattern.CASE_INSENSITIVE);
    /** 1-based column where Maestro tag values begin (e.g. {@code %%song-title} + spaces). */
    private static final int MAESTRO_VALUE_COLUMN = 20;

    private AbcMetadataRewriter() {
    }

    /**
     * One part edit: which original {@code X:} block to use, and the metadata to write into it.
     *
     * @param sourceBlockIndex 0-based index among {@code X:} blocks in the original file
     * @param newPartNumber value written to the {@code X:} line
     * @param partName value for {@code %%part-name} (null/blank clears to empty)
     * @param madeFor value for {@code %%made-for} (null/blank clears to empty)
     */
    public record PartRewrite(int sourceBlockIndex, int newPartNumber, String partName, String madeFor) {
        public PartRewrite {
            if (sourceBlockIndex < 0) {
                throw new IllegalArgumentException("sourceBlockIndex must be >= 0");
            }
        }
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
     * Reorders {@code X:} blocks and updates part number / {@code %%part-name} / {@code %%made-for}.
     * Each block is everything from an {@code X:} line up to (but not including) the next {@code X:}.
     * Preamble before the first {@code X:} is preserved.
     */
    public static String applyParts(String content, List<PartRewrite> edits) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(edits, "edits");
        if (edits.isEmpty()) {
            return content;
        }

        String ending = detectLineEnding(content);
        boolean endsWithLineEnding = content.endsWith("\n") || content.endsWith("\r");
        String[] rawLines = content.split("\\R", -1);
        List<String> preamble = new ArrayList<>();
        List<List<String>> blocks = new ArrayList<>();
        List<String> currentBlock = null;

        for (String line : rawLines) {
            if (X_HEADER.matcher(line.strip()).matches()) {
                currentBlock = new ArrayList<>();
                currentBlock.add(line);
                blocks.add(currentBlock);
            } else if (currentBlock != null) {
                currentBlock.add(line);
            } else {
                preamble.add(line);
            }
        }

        if (blocks.isEmpty()) {
            return content;
        }

        // Trailing empty lines from a final newline belong to EOF, not to the last block body —
        // otherwise reordering that block into the middle inserts a blank separator.
        for (List<String> block : blocks) {
            while (block.size() > 1 && block.get(block.size() - 1).isEmpty()) {
                block.remove(block.size() - 1);
            }
        }

        Set<Integer> seen = new HashSet<>();
        for (PartRewrite edit : edits) {
            if (edit.sourceBlockIndex() >= blocks.size()) {
                throw new IllegalArgumentException(
                        "sourceBlockIndex " + edit.sourceBlockIndex() + " out of range (" + blocks.size() + " blocks)");
            }
            if (!seen.add(edit.sourceBlockIndex())) {
                throw new IllegalArgumentException("Duplicate sourceBlockIndex " + edit.sourceBlockIndex());
            }
        }

        List<String> out = new ArrayList<>(rawLines.length + edits.size() * 2);
        out.addAll(preamble);
        for (PartRewrite edit : edits) {
            List<String> patched = patchBlock(
                    blocks.get(edit.sourceBlockIndex()),
                    edit.newPartNumber(),
                    nullToEmpty(edit.partName()),
                    nullToEmpty(edit.madeFor()));
            out.addAll(patched);
        }

        for (int i = 0; i < blocks.size(); i++) {
            if (!seen.contains(i)) {
                out.addAll(blocks.get(i));
            }
        }

        if (endsWithLineEnding && (out.isEmpty() || !out.get(out.size() - 1).isEmpty())) {
            out.add("");
        }

        return String.join(ending, out);
    }

    private static List<String> patchBlock(
            List<String> block, int newPartNumber, String partName, String madeFor) {
        if (block.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(block.size() + 2);
        boolean sawPartName = false;
        boolean sawMadeFor = false;
        int partNameIndex = -1;

        String first = block.get(0);
        Matcher xMatch = X_HEADER.matcher(first.strip());
        if (xMatch.matches()) {
            String trailing = xMatch.group(2) == null ? "" : xMatch.group(2);
            out.add(preserveLeading(first, "X:" + newPartNumber + trailing));
        } else {
            out.add(first);
        }

        for (int i = 1; i < block.size(); i++) {
            String line = block.get(i);
            String stripped = line.strip();
            Matcher tag = MAESTRO_TAG.matcher(stripped);
            if (tag.matches()) {
                String tagName = tag.group(2).toLowerCase(Locale.ROOT);
                if ("part-name".equals(tagName)) {
                    out.add(preserveLeading(line, formatMaestroTag("part-name", partName)));
                    sawPartName = true;
                    partNameIndex = out.size() - 1;
                    continue;
                }
                if ("made-for".equals(tagName)) {
                    out.add(preserveLeading(line, formatMaestroTag("made-for", madeFor)));
                    sawMadeFor = true;
                    continue;
                }
            }
            out.add(line);
        }

        if (!sawPartName) {
            out.add(1, formatMaestroTag("part-name", partName));
            partNameIndex = 1;
            if (!sawMadeFor) {
                out.add(2, formatMaestroTag("made-for", madeFor));
            }
        } else if (!sawMadeFor) {
            out.add(partNameIndex + 1, formatMaestroTag("made-for", madeFor));
        }
        return out;
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

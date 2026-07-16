package com.aevoreth.abcmm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;

/**
 * Parses ABC Maestro tags and header fallbacks (metadata only; no note bodies).
 * Field priority matches Python {@code abc_parser.py}.
 */
public final class AbcMetadataParser {

    private static final Pattern MAESTRO_TAG = Pattern.compile("^%%([a-z]+(?:-[a-z]+)*)\\s*(.*)$");
    private static final Pattern X_HEADER = Pattern.compile("^X:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public AbcFileMetadata parse(Path path) throws IOException {
        return parse(path, null);
    }

    public AbcFileMetadata parse(Path path, Function<String, Long> instrumentResolver) throws IOException {
        Objects.requireNonNull(path, "path");
        // Match Python errors="replace" so corrupt bytes do not abort the scan.
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return parse(content, path.getFileName().toString(), instrumentResolver);
    }

    public AbcFileMetadata parse(String content) {
        return parse(content, null, null);
    }

    public AbcFileMetadata parse(String content, String filename) {
        return parse(content, filename, null);
    }

    /**
     * @param instrumentResolver maps {@code %%made-for} name to instrument id; null leaves instrumentId null
     */
    public AbcFileMetadata parse(String content, String filename, Function<String, Long> instrumentResolver) {
        Objects.requireNonNull(content, "content");
        Headers headers = parseHeaders(content);
        List<AbcPartMetadata> parts = parseParts(content, instrumentResolver);

        String title = getMaestro(headers.maestro, "song-title");
        if (title == null || title.isBlank()) {
            title = headers.firstT;
            if (title == null || title.isBlank()) {
                title = filename != null && !filename.isBlank() ? filename : "Unknown";
            }
        }
        title = title.strip();
        if (title.isEmpty()) {
            title = "Unknown";
        }

        String composers = getMaestro(headers.maestro, "song-composer");
        if (composers == null || composers.isBlank()) {
            composers = headers.firstC;
            if (composers == null || composers.isBlank()) {
                composers = "Unknown";
            }
        }
        composers = composers.strip();
        if (composers.isEmpty()) {
            composers = "Unknown";
        }

        String transcriber = getMaestro(headers.maestro, "song-transcriber");
        if (transcriber == null || transcriber.isEmpty()) {
            transcriber = headers.firstZ;
        }
        if (transcriber != null) {
            transcriber = transcriber.strip();
            if (transcriber.isEmpty()) {
                transcriber = null;
            }
        }

        Integer durationSeconds = null;
        String durStr = getMaestro(headers.maestro, "song-duration");
        if (durStr != null) {
            durationSeconds = parseMmSs(durStr);
        }

        String exportTimestamp = getMaestro(headers.maestro, "export-timestamp");

        return new AbcFileMetadata(title, composers, transcriber, durationSeconds, exportTimestamp, parts);
    }

    private static Headers parseHeaders(String content) {
        Map<String, String> maestro = new HashMap<>();
        String firstT = null;
        String firstC = null;
        String firstZ = null;

        for (String line : content.split("\\R", -1)) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            Matcher tag = MAESTRO_TAG.matcher(stripped);
            if (tag.matches()) {
                String tagName = tag.group(1).strip().toLowerCase(Locale.ROOT);
                String tagValue = tag.group(2) != null ? tag.group(2).strip() : "";
                maestro.put(tagName, tagValue);
                continue;
            }
            if (stripped.startsWith("T:") && firstT == null) {
                firstT = stripped.substring(2).strip();
            } else if (stripped.startsWith("C:") && firstC == null) {
                firstC = stripped.substring(2).strip();
            } else if (stripped.startsWith("Z:") && firstZ == null) {
                firstZ = stripped.substring(2).strip();
            }
        }
        return new Headers(maestro, firstT == null ? "" : firstT, firstC == null ? "" : firstC, firstZ == null ? "" : firstZ);
    }

    private static List<AbcPartMetadata> parseParts(String content, Function<String, Long> instrumentResolver) {
        List<AbcPartMetadata> parts = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        int i = 0;
        while (i < lines.length) {
            Matcher xMatch = X_HEADER.matcher(lines[i].strip());
            if (xMatch.find()) {
                int partNum = Integer.parseInt(xMatch.group(1));
                String partName = null;
                String madeFor = null;
                String titleFromT = null;
                i++;
                while (i < lines.length) {
                    String stripped = lines[i].strip();
                    if (X_HEADER.matcher(stripped).find()) {
                        break;
                    }
                    if (stripped.startsWith("T:") && titleFromT == null) {
                        String t = stripped.substring(2).strip();
                        titleFromT = t.isEmpty() ? null : t;
                    } else {
                        Matcher tag = MAESTRO_TAG.matcher(stripped);
                        if (tag.matches()) {
                            String name = tag.group(1).strip().toLowerCase(Locale.ROOT);
                            String val = tag.group(2) != null ? tag.group(2).strip() : "";
                            if ("part-name".equals(name)) {
                                partName = val.isEmpty() ? null : val;
                            } else if ("made-for".equals(name)) {
                                madeFor = val.isEmpty() ? null : val;
                            }
                        }
                    }
                    i++;
                }
                Long instrumentId = null;
                if (madeFor != null && instrumentResolver != null) {
                    instrumentId = instrumentResolver.apply(madeFor);
                }
                parts.add(new AbcPartMetadata(partNum, partName, instrumentId, titleFromT));
                continue;
            }
            i++;
        }
        return parts;
    }

    private static String getMaestro(Map<String, String> tags, String key) {
        String v = tags.get(key);
        if (v == null) {
            return null;
        }
        String stripped = v.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    static Integer parseMmSs(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int m = Integer.parseInt(parts[0].strip());
            int s = Integer.parseInt(parts[1].strip());
            if (m >= 0 && s >= 0 && s < 60) {
                return m * 60 + s;
            }
        } catch (NumberFormatException ignored) {
            // unparseable
        }
        return null;
    }

    private record Headers(Map<String, String> maestro, String firstT, String firstC, String firstZ) {
    }
}

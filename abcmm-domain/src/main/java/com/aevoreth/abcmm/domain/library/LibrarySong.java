package com.aevoreth.abcmm.domain.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row in the main library table (indexed song, not an on-disk ABC load).
 * Fields mirror Python {@code LibrarySongRow}.
 */
public record LibrarySong(
        long id,
        String title,
        String composers,
        String transcriber,
        Integer durationSeconds,
        int partCount,
        String partsJson,
        String lastPlayedAt,
        int totalPlays,
        Integer rating,
        Long statusId,
        String statusName,
        String statusColor,
        String notes,
        String lyrics,
        boolean inUpcomingSet) {

    public LibrarySong {
        title = title == null ? "" : title;
        composers = composers == null ? "" : composers;
        totalPlays = Math.max(0, totalPlays);
        partCount = Math.max(0, partCount);
    }

    /**
     * Parts-column tooltip lines: {@code 51: Basic Flute 1 (Basic Flute)}.
     * Uses {@code part_number}, {@code part_name}, and {@code made_for} from parts JSON.
     * When {@code made_for} is missing, {@code instrumentNames} may resolve {@code instrument_id}.
     */
    public List<String> partNames() {
        return partNames(Map.of());
    }

    /**
     * @param instrumentNames catalog names by instrument id (for songs scanned before made_for was stored)
     */
    public List<String> partNames(Map<Long, String> instrumentNames) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        String trimmed = partsJson.trim();
        if (!trimmed.startsWith("[")) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        // Lightweight parse — avoids a JSON dependency in domain.
        int index = 0;
        while (index < trimmed.length()) {
            int objStart = trimmed.indexOf('{', index);
            if (objStart < 0) {
                break;
            }
            int objEnd = trimmed.indexOf('}', objStart);
            if (objEnd < 0) {
                break;
            }
            String object = trimmed.substring(objStart, objEnd + 1);
            Integer partNumber = extractJsonInt(object, "part_number");
            int displayNumber = partNumber == null ? lines.size() + 1 : partNumber;
            String partName = extractJsonString(object, "part_name");
            if (partName == null || partName.isBlank()) {
                partName = "Part " + displayNumber;
            } else {
                partName = partName.trim();
            }
            String madeFor = extractJsonString(object, "made_for");
            if (madeFor != null) {
                madeFor = madeFor.trim();
            }
            if (madeFor == null || madeFor.isBlank()) {
                Long instrumentId = extractJsonLong(object, "instrument_id");
                if (instrumentId != null && instrumentNames != null) {
                    String resolved = instrumentNames.get(instrumentId);
                    if (resolved != null && !resolved.isBlank()) {
                        madeFor = resolved.trim();
                    }
                }
            }
            if (madeFor == null || madeFor.isBlank()) {
                lines.add(displayNumber + ": " + partName);
            } else {
                lines.add(displayNumber + ": " + partName + " (" + madeFor + ")");
            }
            index = objEnd + 1;
        }
        return List.copyOf(lines);
    }

    private static String extractJsonString(String object, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = object.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = object.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < object.length() && Character.isWhitespace(object.charAt(i))) {
            i++;
        }
        if (i < object.length() && object.regionMatches(true, i, "null", 0, 4)) {
            return null;
        }
        int firstQuote = object.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = object.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return object.substring(firstQuote + 1, secondQuote);
    }

    private static Integer extractJsonInt(String object, String key) {
        Long value = extractJsonLong(object, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private static Long extractJsonLong(String object, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = object.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = object.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < object.length() && Character.isWhitespace(object.charAt(i))) {
            i++;
        }
        if (i < object.length() && object.regionMatches(true, i, "null", 0, 4)) {
            return null;
        }
        int start = i;
        while (i < object.length() && (Character.isDigit(object.charAt(i)) || object.charAt(i) == '-')) {
            i++;
        }
        if (start == i) {
            return null;
        }
        try {
            return Long.parseLong(object.substring(start, i));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

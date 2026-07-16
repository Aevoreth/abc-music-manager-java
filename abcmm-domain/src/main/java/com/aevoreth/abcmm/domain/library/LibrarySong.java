package com.aevoreth.abcmm.domain.library;

import java.util.ArrayList;
import java.util.List;

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
     * Part display names for the Parts-column tooltip (part_name or "Part N").
     */
    public List<String> partNames() {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        String trimmed = partsJson.trim();
        if (!trimmed.startsWith("[")) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        // Lightweight parse of objects with optional part_name / part_number — avoids a JSON dependency in domain.
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
            String partName = extractJsonString(object, "part_name");
            if (partName != null && !partName.isBlank()) {
                names.add(partName.trim());
            } else {
                Integer partNumber = extractJsonInt(object, "part_number");
                names.add("Part " + (partNumber == null ? names.size() + 1 : partNumber));
            }
            index = objEnd + 1;
        }
        return List.copyOf(names);
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
        int start = i;
        while (i < object.length() && (Character.isDigit(object.charAt(i)) || object.charAt(i) == '-')) {
            i++;
        }
        if (start == i) {
            return null;
        }
        try {
            return Integer.parseInt(object.substring(start, i));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

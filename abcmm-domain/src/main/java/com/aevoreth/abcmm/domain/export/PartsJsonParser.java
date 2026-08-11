package com.aevoreth.abcmm.domain.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight parts-JSON parse for set export (no Jackson in domain).
 */
public final class PartsJsonParser {

    private PartsJsonParser() {
    }

    public static List<ExportPartMeta> parse(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        String trimmed = partsJson.trim();
        if (!trimmed.startsWith("[")) {
            return List.of();
        }
        List<ExportPartMeta> parts = new ArrayList<>();
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
            if (partNumber != null && partNumber > 0) {
                parts.add(new ExportPartMeta(
                        partNumber,
                        extractJsonString(object, "part_name"),
                        extractJsonString(object, "title_from_t"),
                        extractJsonLong(object, "instrument_id")));
            }
            index = objEnd + 1;
        }
        return List.copyOf(parts);
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

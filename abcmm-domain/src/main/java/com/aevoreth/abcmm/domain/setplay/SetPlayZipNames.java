package com.aevoreth.abcmm.domain.setplay;

/**
 * Suggested download filename for a Set Play zip. R2 may store {@code zips/CODE.zip};
 * the file the user saves should use the set name when available.
 */
public final class SetPlayZipNames {

    private SetPlayZipNames() {
    }

    public static String downloadFileName(String setName, String fallbackCode) {
        return suggestedBaseName(setName, fallbackCode) + ".zip";
    }

    /**
     * Folder created under the extract path. Prefers the session name; falls back to the session code.
     */
    public static String extractFolderName(String preferred, String fallbackCode) {
        return suggestedBaseName(preferred, fallbackCode);
    }

    private static String suggestedBaseName(String preferred, String fallbackCode) {
        String base = sanitizeBaseName(preferred);
        if (base.isEmpty()) {
            base = sanitizeBaseName(fallbackCode);
        }
        if (base.isEmpty()) {
            base = "set";
        }
        return base;
    }

    public static String sanitizeBaseName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.strip()
                .replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", " ")
                .replaceAll("\\s+", " ")
                .strip()
                .replaceAll("^[.]+", "")
                .replaceAll("[.]+$", "");
        if (s.length() > 120) {
            s = s.substring(0, 120).strip();
        }
        return s;
    }
}

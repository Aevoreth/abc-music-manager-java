package com.aevoreth.abcmm.domain.setplay.relay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse Wrangler CLI output used by the Set Play deploy wizard.
 */
public final class SetPlayWranglerParse {

    public static final String PLACEHOLDER_D1_ID = "REPLACE_WITH_D1_ID";

    private static final Pattern DATABASE_ID_RE =
            Pattern.compile("database_id\\s*=\\s*\"([^\"]+)\"");
    private static final String UUID =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern R2_KEY_JSON_RE =
            Pattern.compile("\"r2_key\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ANSI_RE = Pattern.compile("\\u001B\\[[;\\d]*m");

    private SetPlayWranglerParse() {
    }

    public static String stripAnsi(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ANSI_RE.matcher(text).replaceAll("");
    }

    /**
     * Last {@code database_id = "..."} that is not the template placeholder.
     */
    public static String extractTomlDatabaseId(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher m = DATABASE_ID_RE.matcher(stripAnsi(text));
        String last = null;
        while (m.find()) {
            String id = m.group(1);
            if (id != null && !id.isBlank() && !PLACEHOLDER_D1_ID.equals(id)) {
                last = id;
            }
        }
        return last;
    }

    public static String replaceTomlDatabaseId(String tomlText, String databaseId) {
        if (tomlText == null) {
            tomlText = "";
        }
        Matcher m = DATABASE_ID_RE.matcher(tomlText);
        if (m.find()) {
            return m.replaceFirst("database_id = \"" + Matcher.quoteReplacement(databaseId) + "\"");
        }
        return tomlText + "\ndatabase_id = \"" + databaseId + "\"\n";
    }

    /**
     * Find the UUID for {@code databaseName} in {@code wrangler d1 list} (JSON or table).
     */
    public static String findD1IdByName(String output, String databaseName) {
        if (output == null || databaseName == null || databaseName.isBlank()) {
            return null;
        }
        String text = stripAnsi(output);
        Pattern jsonUuidThenName = Pattern.compile(
                "\\{[^{}]*?\"(?:uuid|database_id)\"\\s*:\\s*\"(" + UUID + ")\"[^{}]*?\"name\"\\s*:\\s*\""
                        + Pattern.quote(databaseName) + "\"",
                Pattern.CASE_INSENSITIVE);
        Matcher json1 = jsonUuidThenName.matcher(text);
        if (json1.find()) {
            return json1.group(1);
        }
        Pattern jsonNameThenUuid = Pattern.compile(
                "\\{[^{}]*?\"name\"\\s*:\\s*\"" + Pattern.quote(databaseName)
                        + "\"[^{}]*?\"(?:uuid|database_id)\"\\s*:\\s*\"(" + UUID + ")\"",
                Pattern.CASE_INSENSITIVE);
        Matcher json2 = jsonNameThenUuid.matcher(text);
        if (json2.find()) {
            return json2.group(1);
        }
        Pattern tableUuidThenName = Pattern.compile(
                "(" + UUID + ")[^\\n]{0,160}" + Pattern.quote(databaseName));
        Matcher table1 = tableUuidThenName.matcher(text);
        if (table1.find()) {
            return table1.group(1);
        }
        Pattern tableNameThenUuid = Pattern.compile(
                Pattern.quote(databaseName) + "[^\\n]{0,160}(" + UUID + ")");
        Matcher table2 = tableNameThenUuid.matcher(text);
        if (table2.find()) {
            return table2.group(1);
        }
        return null;
    }

    public record SessionZip(String code, String r2Key) {
    }

    /**
     * Session code + R2 key pairs from {@code wrangler d1 execute --json}
     * ({@code SELECT code, r2_key FROM session WHERE r2_key IS NOT NULL}).
     */
    public static List<SessionZip> extractSessionZips(String d1ExecuteOutput) {
        List<SessionZip> zips = new ArrayList<>();
        if (d1ExecuteOutput == null || d1ExecuteOutput.isEmpty()) {
            return zips;
        }
        String text = stripAnsi(d1ExecuteOutput);
        Matcher codeThenKey = Pattern.compile(
                "\"code\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"r2_key\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        while (codeThenKey.find()) {
            addSessionZip(zips, codeThenKey.group(1), codeThenKey.group(2));
        }
        Matcher keyThenCode = Pattern.compile(
                "\"r2_key\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"code\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        while (keyThenCode.find()) {
            addSessionZip(zips, keyThenCode.group(2), keyThenCode.group(1));
        }
        for (String key : extractR2Keys(text)) {
            addSessionZip(zips, codeFromR2Key(key), key);
        }
        return zips;
    }

    private static void addSessionZip(List<SessionZip> zips, String code, String key) {
        if (!isSafeR2Key(key) || zips.stream().anyMatch(z -> key.equals(z.r2Key()))) {
            return;
        }
        zips.add(new SessionZip(code, key));
    }

    public static List<String> extractR2Keys(String d1ExecuteOutput) {
        List<String> keys = new ArrayList<>();
        if (d1ExecuteOutput == null || d1ExecuteOutput.isEmpty()) {
            return keys;
        }
        Matcher m = R2_KEY_JSON_RE.matcher(stripAnsi(d1ExecuteOutput));
        while (m.find()) {
            String key = m.group(1);
            if (isSafeR2Key(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String codeFromR2Key(String key) {
        if (key == null || !key.startsWith("zips/") || !key.endsWith(".zip")) {
            return null;
        }
        return key.substring("zips/".length(), key.length() - ".zip".length());
    }

    public static boolean isSafeR2Key(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (key.contains("..") || key.contains("\\") || key.startsWith("/")) {
            return false;
        }
        return key.matches("zips/[A-Za-z0-9._-]+");
    }

    public static boolean looksLikeMissingResource(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        String lower = stripAnsi(output).toLowerCase(Locale.ROOT);
        return lower.contains("not found")
                || lower.contains("does not exist")
                || lower.contains("couldn't find")
                || lower.contains("could not find")
                || lower.contains("no such");
    }

    public static boolean looksLikeBucketNotEmpty(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        String lower = stripAnsi(output).toLowerCase(Locale.ROOT);
        return lower.contains("is not empty") || lower.contains("code: 10008");
    }
}

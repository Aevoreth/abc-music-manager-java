package com.aevoreth.abcmm.domain.export;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV part-sheet find/replace helpers and column lists.
 */
public final class CsvPartSheet {

    private CsvPartSheet() {
    }

    public static List<SetExportSettings.FindReplaceRule> normalizeRenameRules(Object raw) {
        List<SetExportSettings.FindReplaceRule> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof SetExportSettings.FindReplaceRule rule) {
                if (!rule.find().isBlank()) {
                    out.add(rule);
                }
                continue;
            }
            if (item instanceof List<?> pair && pair.size() >= 2) {
                String find = String.valueOf(pair.get(0));
                String replace = pair.get(1) == null ? "" : String.valueOf(pair.get(1));
                if (!find.isBlank()) {
                    out.add(new SetExportSettings.FindReplaceRule(find, replace));
                }
                continue;
            }
            if (item instanceof java.util.Map<?, ?> map) {
                Object f = map.get("find");
                if (f == null) {
                    continue;
                }
                Object r = map.get("replace");
                String find = String.valueOf(f);
                if (find.isBlank()) {
                    continue;
                }
                out.add(new SetExportSettings.FindReplaceRule(find, r == null ? "" : String.valueOf(r)));
            }
        }
        return List.copyOf(out);
    }

    public static String applyDisplayRenames(String text, List<SetExportSettings.FindReplaceRule> rules) {
        String s = text == null ? "" : text;
        if (rules == null) {
            return s;
        }
        for (SetExportSettings.FindReplaceRule rule : rules) {
            if (rule.find().isEmpty()) {
                continue;
            }
            s = s.replace(rule.find(), rule.replace());
        }
        return s;
    }

    public static List<String> metadataColumns(SetExportSettings settings) {
        if (settings.csvUseVisibleColumns()) {
            List<String> cols = new ArrayList<>();
            cols.add("Title");
            cols.add("Parts");
            if (settings.includeComposerInCsv()) {
                cols.add("Composers");
            }
            cols.add("Duration");
            cols.add("Artist");
            return cols;
        }
        List<String> cols = new ArrayList<>();
        for (String name : SetExportSettings.CSV_AVAILABLE_COLUMNS) {
            if (Boolean.TRUE.equals(settings.csvColumnsEnabled().get(name))) {
                cols.add(name);
            }
        }
        return cols;
    }

    public static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return "";
        }
        int m = seconds / 60;
        int s = seconds % 60;
        return m + ":" + String.format("%02d", s);
    }
}

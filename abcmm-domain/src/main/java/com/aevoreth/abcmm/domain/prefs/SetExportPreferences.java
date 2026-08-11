package com.aevoreth.abcmm.domain.prefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.aevoreth.abcmm.domain.export.CsvPartSheet;
import com.aevoreth.abcmm.domain.export.SetExportSettings;

/**
 * Shared {@code set_export} preferences blob (Python {@code get_set_export_prefs}).
 */
public final class SetExportPreferences {

    private String outputDirectory = "";
    private boolean renameAbcFiles = true;
    private boolean exportAsFolder = true;
    private boolean exportAsZip;
    private String filenamePattern = "$SongIndex_$FileName";
    private String whitespaceReplace = " ";
    private boolean exportCsvPartSheet;
    private boolean exportAbcpPlaylist = true;
    private boolean includeComposerInCsv = true;
    private boolean csvUseVisibleColumns = true;
    private Map<String, Boolean> csvColumnsEnabled = new LinkedHashMap<>(SetExportSettings.CSV_DEFAULT_ENABLED);
    private String csvPartColumns = "part";
    private boolean renameParts;
    private String partNamePattern = "$PartTitle";
    private List<SetExportSettings.FindReplaceRule> csvPartRenameRules = List.of();

    public static SetExportPreferences defaults() {
        return new SetExportPreferences();
    }

    public SetExportPreferences copy() {
        SetExportPreferences copy = new SetExportPreferences();
        copy.outputDirectory = outputDirectory;
        copy.renameAbcFiles = renameAbcFiles;
        copy.exportAsFolder = exportAsFolder;
        copy.exportAsZip = exportAsZip;
        copy.filenamePattern = filenamePattern;
        copy.whitespaceReplace = whitespaceReplace;
        copy.exportCsvPartSheet = exportCsvPartSheet;
        copy.exportAbcpPlaylist = exportAbcpPlaylist;
        copy.includeComposerInCsv = includeComposerInCsv;
        copy.csvUseVisibleColumns = csvUseVisibleColumns;
        copy.csvColumnsEnabled = new LinkedHashMap<>(csvColumnsEnabled);
        copy.csvPartColumns = csvPartColumns;
        copy.renameParts = renameParts;
        copy.partNamePattern = partNamePattern;
        copy.csvPartRenameRules = List.copyOf(csvPartRenameRules);
        return copy;
    }

    public void applyTo(SetExportSettings settings) {
        Objects.requireNonNull(settings, "settings");
        settings.setRenameAbcFiles(renameAbcFiles);
        settings.setExportAsFolder(exportAsFolder);
        settings.setExportAsZip(exportAsZip);
        settings.setFilenamePattern(filenamePattern);
        settings.setWhitespaceReplace(whitespaceReplace);
        settings.setExportCsvPartSheet(exportCsvPartSheet);
        settings.setExportAbcpPlaylist(exportAbcpPlaylist);
        settings.setIncludeComposerInCsv(includeComposerInCsv);
        settings.setCsvUseVisibleColumns(csvUseVisibleColumns);
        settings.setCsvColumnsEnabled(csvColumnsEnabled);
        settings.setCsvPartColumns(csvPartColumns);
        settings.setRenameParts(renameParts);
        settings.setPartNamePattern(partNamePattern);
        settings.setCsvPartRenameRules(csvPartRenameRules);
    }

    public void captureFrom(SetExportSettings settings, String outputDirectoryPath) {
        Objects.requireNonNull(settings, "settings");
        this.outputDirectory = outputDirectoryPath == null ? "" : outputDirectoryPath;
        this.renameAbcFiles = settings.renameAbcFiles();
        this.exportAsFolder = settings.exportAsFolder();
        this.exportAsZip = settings.exportAsZip();
        this.filenamePattern = settings.filenamePattern();
        this.whitespaceReplace = settings.whitespaceReplace();
        this.exportCsvPartSheet = settings.exportCsvPartSheet();
        this.exportAbcpPlaylist = settings.exportAbcpPlaylist();
        this.includeComposerInCsv = settings.includeComposerInCsv();
        this.csvUseVisibleColumns = settings.csvUseVisibleColumns();
        this.csvColumnsEnabled = new LinkedHashMap<>(settings.csvColumnsEnabled());
        this.csvPartColumns = settings.csvPartColumns();
        this.renameParts = settings.renameParts();
        this.partNamePattern = settings.partNamePattern();
        this.csvPartRenameRules = List.copyOf(settings.csvPartRenameRules());
    }

    public String outputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory == null ? "" : outputDirectory;
    }

    public boolean renameAbcFiles() {
        return renameAbcFiles;
    }

    public void setRenameAbcFiles(boolean renameAbcFiles) {
        this.renameAbcFiles = renameAbcFiles;
    }

    public boolean exportAsFolder() {
        return exportAsFolder;
    }

    public void setExportAsFolder(boolean exportAsFolder) {
        this.exportAsFolder = exportAsFolder;
    }

    public boolean exportAsZip() {
        return exportAsZip;
    }

    public void setExportAsZip(boolean exportAsZip) {
        this.exportAsZip = exportAsZip;
    }

    public String filenamePattern() {
        return filenamePattern;
    }

    public void setFilenamePattern(String filenamePattern) {
        this.filenamePattern = filenamePattern == null || filenamePattern.isBlank()
                ? "$SongIndex_$FileName"
                : filenamePattern;
    }

    public String whitespaceReplace() {
        return whitespaceReplace;
    }

    public void setWhitespaceReplace(String whitespaceReplace) {
        this.whitespaceReplace = whitespaceReplace == null ? " " : whitespaceReplace;
    }

    public boolean exportCsvPartSheet() {
        return exportCsvPartSheet;
    }

    public void setExportCsvPartSheet(boolean exportCsvPartSheet) {
        this.exportCsvPartSheet = exportCsvPartSheet;
    }

    public boolean exportAbcpPlaylist() {
        return exportAbcpPlaylist;
    }

    public void setExportAbcpPlaylist(boolean exportAbcpPlaylist) {
        this.exportAbcpPlaylist = exportAbcpPlaylist;
    }

    public boolean includeComposerInCsv() {
        return includeComposerInCsv;
    }

    public void setIncludeComposerInCsv(boolean includeComposerInCsv) {
        this.includeComposerInCsv = includeComposerInCsv;
    }

    public boolean csvUseVisibleColumns() {
        return csvUseVisibleColumns;
    }

    public void setCsvUseVisibleColumns(boolean csvUseVisibleColumns) {
        this.csvUseVisibleColumns = csvUseVisibleColumns;
    }

    public Map<String, Boolean> csvColumnsEnabled() {
        return csvColumnsEnabled;
    }

    public void setCsvColumnsEnabled(Map<String, Boolean> csvColumnsEnabled) {
        this.csvColumnsEnabled = csvColumnsEnabled == null
                ? new LinkedHashMap<>(SetExportSettings.CSV_DEFAULT_ENABLED)
                : new LinkedHashMap<>(csvColumnsEnabled);
    }

    public String csvPartColumns() {
        return csvPartColumns;
    }

    public void setCsvPartColumns(String csvPartColumns) {
        this.csvPartColumns = "instrument".equals(csvPartColumns) ? "instrument" : "part";
    }

    public boolean renameParts() {
        return renameParts;
    }

    public void setRenameParts(boolean renameParts) {
        this.renameParts = renameParts;
    }

    public String partNamePattern() {
        return partNamePattern;
    }

    public void setPartNamePattern(String partNamePattern) {
        this.partNamePattern = partNamePattern == null || partNamePattern.isBlank()
                ? "$PartTitle"
                : partNamePattern;
    }

    public List<SetExportSettings.FindReplaceRule> csvPartRenameRules() {
        return csvPartRenameRules;
    }

    public void setCsvPartRenameRules(List<SetExportSettings.FindReplaceRule> csvPartRenameRules) {
        this.csvPartRenameRules = csvPartRenameRules == null
                ? List.of()
                : List.copyOf(csvPartRenameRules);
    }

    public static SetExportPreferences fromMap(Object raw) {
        SetExportPreferences prefs = defaults();
        if (!(raw instanceof Map<?, ?> map)) {
            return prefs;
        }
        prefs.setOutputDirectory(asString(map.get("output_directory")));
        Boolean renameAbc = asBoolean(map.get("rename_abc_files"));
        if (renameAbc != null) {
            prefs.setRenameAbcFiles(renameAbc);
        }
        Boolean asFolder = asBoolean(map.get("export_as_folder"));
        if (asFolder != null) {
            prefs.setExportAsFolder(asFolder);
        }
        Boolean asZip = asBoolean(map.get("export_as_zip"));
        if (asZip != null) {
            prefs.setExportAsZip(asZip);
        }
        String pattern = asString(map.get("filename_pattern"));
        if (pattern != null && !pattern.isBlank()) {
            prefs.setFilenamePattern(pattern);
        }
        String ws = asString(map.get("whitespace_replace"));
        if (ws != null) {
            prefs.setWhitespaceReplace(ws);
        }
        Boolean csv = asBoolean(map.get("export_csv_part_sheet"));
        if (csv != null) {
            prefs.setExportCsvPartSheet(csv);
        }
        Boolean abcp = asBoolean(map.get("export_abcp_playlist"));
        if (abcp != null) {
            prefs.setExportAbcpPlaylist(abcp);
        }
        Boolean includeComposer = asBoolean(map.get("include_composer_in_csv"));
        if (includeComposer != null) {
            prefs.setIncludeComposerInCsv(includeComposer);
        }
        Boolean visible = asBoolean(map.get("csv_use_visible_columns"));
        if (visible != null) {
            prefs.setCsvUseVisibleColumns(visible);
        }
        Object cols = map.get("csv_columns_enabled");
        if (cols instanceof Map<?, ?> colMap) {
            Map<String, Boolean> enabled = new LinkedHashMap<>(SetExportSettings.CSV_DEFAULT_ENABLED);
            for (Map.Entry<?, ?> e : colMap.entrySet()) {
                Boolean v = asBoolean(e.getValue());
                if (e.getKey() != null && v != null) {
                    enabled.put(String.valueOf(e.getKey()), v);
                }
            }
            prefs.setCsvColumnsEnabled(enabled);
        }
        String partCols = asString(map.get("csv_part_columns"));
        if (partCols != null) {
            prefs.setCsvPartColumns(partCols);
        }
        Boolean renameParts = asBoolean(map.get("rename_parts"));
        if (renameParts != null) {
            prefs.setRenameParts(renameParts);
        }
        String partPattern = asString(map.get("part_name_pattern"));
        if (partPattern != null && !partPattern.isBlank()) {
            prefs.setPartNamePattern(partPattern);
        }
        prefs.setCsvPartRenameRules(CsvPartSheet.normalizeRenameRules(map.get("csv_part_rename_rules")));
        return prefs;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("output_directory", outputDirectory);
        map.put("rename_abc_files", renameAbcFiles);
        map.put("export_as_folder", exportAsFolder);
        map.put("export_as_zip", exportAsZip);
        map.put("filename_pattern", filenamePattern);
        map.put("whitespace_replace", whitespaceReplace);
        map.put("export_csv_part_sheet", exportCsvPartSheet);
        map.put("export_abcp_playlist", exportAbcpPlaylist);
        map.put("include_composer_in_csv", includeComposerInCsv);
        map.put("csv_use_visible_columns", csvUseVisibleColumns);
        map.put("csv_columns_enabled", new LinkedHashMap<>(csvColumnsEnabled));
        map.put("csv_part_columns", csvPartColumns);
        map.put("rename_parts", renameParts);
        map.put("part_name_pattern", partNamePattern);
        List<List<String>> rules = new ArrayList<>();
        for (SetExportSettings.FindReplaceRule rule : csvPartRenameRules) {
            rules.add(List.of(rule.find(), rule.replace()));
        }
        map.put("csv_part_rename_rules", rules);
        return map;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }
}

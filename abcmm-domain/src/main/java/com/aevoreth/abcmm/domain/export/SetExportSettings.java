package com.aevoreth.abcmm.domain.export;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime settings for one set export run (mirrors Python {@code SetExportSettings}).
 */
public final class SetExportSettings {

    public static final List<String> CSV_AVAILABLE_COLUMNS = List.of(
            "Title",
            "Composers",
            "Transcriber",
            "Duration",
            "Part Count",
            "File Name",
            "Notes",
            "Status");

    public static final Map<String, Boolean> CSV_DEFAULT_ENABLED = Map.of(
            "Title", true,
            "Part Count", true,
            "Duration", true,
            "Composers", true,
            "Transcriber", true);

    private Path outputDirectory;
    private String setName = "";
    private boolean exportAsFolder = true;
    private boolean exportAsZip;
    private boolean renameAbcFiles = true;
    private String filenamePattern = "$SongIndex_$FileName";
    private String whitespaceReplace = " ";
    private boolean partCountZeroPadded = true;
    private boolean exportCsvPartSheet;
    private boolean exportAbcpPlaylist = true;
    private boolean includeComposerInCsv = true;
    private boolean csvUseVisibleColumns = true;
    private Map<String, Boolean> csvColumnsEnabled = new LinkedHashMap<>(CSV_DEFAULT_ENABLED);
    private String csvPartColumns = "part";
    private boolean renameParts;
    private String partNamePattern = "$PartTitle";
    private List<FindReplaceRule> csvPartRenameRules = List.of();

    public Path outputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
    }

    public String setName() {
        return setName;
    }

    public void setSetName(String setName) {
        this.setName = setName == null ? "" : setName;
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

    public boolean renameAbcFiles() {
        return renameAbcFiles;
    }

    public void setRenameAbcFiles(boolean renameAbcFiles) {
        this.renameAbcFiles = renameAbcFiles;
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

    public boolean partCountZeroPadded() {
        return partCountZeroPadded;
    }

    public void setPartCountZeroPadded(boolean partCountZeroPadded) {
        this.partCountZeroPadded = partCountZeroPadded;
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
                ? new LinkedHashMap<>(CSV_DEFAULT_ENABLED)
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

    public List<FindReplaceRule> csvPartRenameRules() {
        return csvPartRenameRules;
    }

    public void setCsvPartRenameRules(List<FindReplaceRule> csvPartRenameRules) {
        this.csvPartRenameRules = csvPartRenameRules == null ? List.of() : List.copyOf(csvPartRenameRules);
    }

    public record FindReplaceRule(String find, String replace) {
        public FindReplaceRule {
            find = find == null ? "" : find;
            replace = replace == null ? "" : replace;
        }
    }
}

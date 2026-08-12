package com.aevoreth.abcmm.domain.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds UTF-8 Lua source for {@code SongbookData.plugindata}.
 *
 * <p>Format matches Python {@code build_plugindata_lua} (Songbook-compatible table with
 * {@code Directories}/{@code Songs}, plus {@code Transcriber}/{@code Artist}). Structure
 * aligns with stock Songbook HTA output for core fields; do not invoke the HTA.
 */
public final class PluginDataLuaBuilder {

    private PluginDataLuaBuilder() {
    }

    /**
     * Result of building PluginData Lua.
     *
     * @param lua             full file contents
     * @param songCount       number of songs written
     * @param directoryCount  number of directory entries written
     */
    public record BuildResult(String lua, int songCount, int directoryCount) {
        public BuildResult {
            Objects.requireNonNull(lua, "lua");
        }
    }

    /**
     * Build Lua from song entries. Directories are derived from song filepaths (plus {@code /}
     * and ancestors). Songs and directories are sorted with {@link PluginDataPathRules#dirSortKey}.
     */
    public static BuildResult build(List<PluginDataSongEntry> songs) {
        List<PluginDataSongEntry> input = songs == null ? List.of() : songs;
        Set<String> dirsSet = new LinkedHashSet<>();
        dirsSet.add("/");
        for (PluginDataSongEntry song : input) {
            String dirPart = ensureDirPath(song.filepath());
            dirsSet.add(dirPart);
            addAncestors(dirsSet, dirPart);
        }

        List<String> dirsSorted = new ArrayList<>(dirsSet);
        dirsSorted.sort(Comparator.comparing(PluginDataPathRules::dirSortKey));

        List<PluginDataSongEntry> songsSorted = new ArrayList<>(input);
        songsSorted.sort(Comparator.comparing(
                s -> PluginDataPathRules.dirSortKey(s.filepath() + s.filename())));

        StringBuilder lines = new StringBuilder();
        lines.append("return\n");
        lines.append("{\n");
        lines.append("\t[\"Directories\"] =\n");
        lines.append("\t{\n");
        for (int i = 0; i < dirsSorted.size(); i++) {
            lines.append("\t\t[")
                    .append(i + 1)
                    .append("] = \"")
                    .append(PluginDataPathRules.luaEscape(dirsSorted.get(i)))
                    .append("\",\n");
        }
        lines.append("\t},\n");
        lines.append("\t[\"Songs\"] =\n");
        lines.append("\t{\n");
        for (int si = 0; si < songsSorted.size(); si++) {
            PluginDataSongEntry song = songsSorted.get(si);
            lines.append("\t\t[").append(si + 1).append("] =\n");
            lines.append("\t\t{\n");
            lines.append("\t\t\t[\"Filepath\"] = \"")
                    .append(PluginDataPathRules.luaEscape(song.filepath()))
                    .append("\",\n");
            lines.append("\t\t\t[\"Filename\"] = \"")
                    .append(PluginDataPathRules.luaEscape(song.filename()))
                    .append("\",\n");
            lines.append("\t\t\t[\"Tracks\"] =\n");
            lines.append("\t\t\t{\n");
            List<PluginDataSongEntry.Track> tracks = song.tracks();
            if (tracks.isEmpty()) {
                tracks = List.of(new PluginDataSongEntry.Track("1", "Part 1"));
            }
            for (int ti = 0; ti < tracks.size(); ti++) {
                PluginDataSongEntry.Track track = tracks.get(ti);
                lines.append("\t\t\t\t[").append(ti + 1).append("] =\n");
                lines.append("\t\t\t\t{\n");
                lines.append("\t\t\t\t\t[\"Id\"] =\"")
                        .append(PluginDataPathRules.luaEscape(track.id()))
                        .append("\",\n");
                lines.append("\t\t\t\t\t[\"Name\"] =\"")
                        .append(PluginDataPathRules.luaEscape(track.name()))
                        .append("\"\n");
                lines.append("\t\t\t\t},\n");
            }
            lines.append("\t\t\t},\n");
            lines.append("\t\t\t[\"Transcriber\"] = \"")
                    .append(PluginDataPathRules.luaEscape(song.transcriber()))
                    .append("\",\n");
            lines.append("\t\t\t[\"Artist\"] = \"")
                    .append(PluginDataPathRules.luaEscape(song.artist()))
                    .append("\"\n");
            lines.append("\t\t},\n");
        }
        lines.append("\t}\n");
        lines.append("}");
        return new BuildResult(lines.toString(), songsSorted.size(), dirsSorted.size());
    }

    /**
     * Build track list from parts metadata using Python naming rules.
     */
    public static List<PluginDataSongEntry.Track> tracksFromParts(
            List<ExportPartMeta> parts, String songTitle) {
        List<PluginDataSongEntry.Track> tracks = new ArrayList<>();
        if (parts != null) {
            for (ExportPartMeta part : parts) {
                int partNumber = part.partNumber() > 0 ? part.partNumber() : tracks.size() + 1;
                String titleFromT = part.titleFromT() == null ? "" : part.titleFromT().strip();
                String partName = part.partName() == null ? "" : part.partName().strip();
                String title = songTitle == null ? "" : songTitle.strip();
                String trackName;
                if (!titleFromT.isEmpty()) {
                    trackName = titleFromT;
                } else if (!title.isEmpty()) {
                    trackName = title;
                } else if (!partName.isEmpty()) {
                    trackName = partName;
                } else {
                    trackName = "Part " + partNumber;
                }
                tracks.add(new PluginDataSongEntry.Track(String.valueOf(partNumber), trackName));
            }
        }
        if (tracks.isEmpty()) {
            tracks.add(new PluginDataSongEntry.Track("1", "Part 1"));
        }
        return List.copyOf(tracks);
    }

    /**
     * Normalize a directory path to Songbook form: leading {@code /}, trailing {@code /},
     * forward slashes.
     */
    public static String ensureDirPath(String filepath) {
        if (filepath == null || filepath.isBlank()) {
            return "/";
        }
        String dir = filepath.replace('\\', '/');
        if (!dir.startsWith("/")) {
            dir = "/" + dir;
        }
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        return dir;
    }

    private static void addAncestors(Set<String> dirsSet, String dirPart) {
        String trimmed = dirPart;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return;
        }
        String[] parts = trimmed.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            current.append('/').append(part);
            dirsSet.add(current + "/");
        }
    }
}

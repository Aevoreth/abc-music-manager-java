package com.aevoreth.abcmm.domain.prefs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * LOTRO Documents / Music path helpers shared with the Python edition conventions.
 *
 * <p>The LOTRO root contains {@code Music} (library + set export) and {@code PluginData}
 * (account songbook targets). The default root is looked up under the system Documents
 * library folder (on Windows, wherever that Known Folder currently points — typically
 * {@code Documents} or {@code OneDrive\Documents}).
 */
public final class LotroPaths {

    public static final String LOTRO_FOLDER_NAME = "The Lord of the Rings Online";
    public static final String LOTRO_FOLDER_NAME_ALT = "Lord of the Rings Online";
    public static final String MUSIC_FOLDER_NAME = "Music";
    public static final String PLUGIN_DATA_FOLDER_NAME = "PluginData";
    public static final String ALL_SERVERS_FOLDER_NAME = "AllServers";

    private static Supplier<Optional<Path>> documentsPathOverride;

    private LotroPaths() {
    }

    /**
     * Test hook to replace Documents-library discovery. Pass {@code null} to restore default.
     */
    public static void setDocumentsPathOverride(Supplier<Optional<Path>> override) {
        documentsPathOverride = override;
    }

    /**
     * System Documents library path when available. Empty if it cannot be determined.
     */
    public static Optional<Path> documentsLibraryPath() {
        if (documentsPathOverride != null) {
            return documentsPathOverride.get();
        }
        if (isWindows()) {
            Optional<Path> fromRegistry = documentsPathFromWindowsRegistry();
            if (fromRegistry.isPresent()) {
                return fromRegistry;
            }
        }
        Path homeDocs = Paths.get(System.getProperty("user.home"), "Documents");
        if (Files.isDirectory(homeDocs)) {
            return Optional.of(homeDocs.toAbsolutePath().normalize());
        }
        return Optional.empty();
    }

    /**
     * Default LOTRO root under Documents if that folder exists; otherwise empty.
     * Tries {@link #LOTRO_FOLDER_NAME} then {@link #LOTRO_FOLDER_NAME_ALT}.
     */
    public static Optional<Path> defaultLotroRoot() {
        Optional<Path> docs = documentsLibraryPath();
        if (docs.isPresent()) {
            Optional<Path> underDocs = findLotroUnder(docs.get());
            if (underDocs.isPresent()) {
                return underDocs;
            }
        }
        // Extra fallbacks when Known Folder lookup fails but common locations exist.
        String home = System.getProperty("user.home");
        Optional<Path> underHomeDocs = findLotroUnder(Paths.get(home, "Documents"));
        if (underHomeDocs.isPresent()) {
            return underHomeDocs;
        }
        return findLotroUnder(Paths.get(home, "OneDrive", "Documents"));
    }

    /**
     * LOTRO root from preferences, or the detected default when unset (Python
     * {@code get_lotro_root()} parity). Does not write preferences.
     */
    public static Optional<Path> effectiveLotroRoot(Preferences preferences) {
        if (preferences != null) {
            String stored = preferences.lotroRoot();
            if (stored != null && !stored.isBlank()) {
                return Optional.of(Paths.get(stored.trim()).toAbsolutePath().normalize());
            }
        }
        return defaultLotroRoot();
    }

    /**
     * Same as {@link #effectiveLotroRoot(Preferences)} as a string; empty when none.
     */
    public static String effectiveLotroRootString(Preferences preferences) {
        return effectiveLotroRoot(preferences).map(Path::toString).orElse("");
    }

    /**
     * If preferences have no LOTRO root and a default exists, set it. Returns {@code true}
     * when preferences were updated.
     */
    public static boolean ensureDefaultLotroRoot(Preferences preferences) {
        if (preferences == null) {
            return false;
        }
        if (preferences.lotroRoot() != null && !preferences.lotroRoot().isBlank()) {
            return false;
        }
        Optional<Path> defaultRoot = defaultLotroRoot();
        if (defaultRoot.isEmpty()) {
            return false;
        }
        preferences.setLotroRoot(defaultRoot.get().toString());
        return true;
    }

    public static Optional<Path> musicRoot(String lotroRoot) {
        if (lotroRoot == null || lotroRoot.isBlank()) {
            return Optional.empty();
        }
        return musicRoot(Paths.get(lotroRoot.trim()));
    }

    public static Optional<Path> musicRoot(Path lotroRoot) {
        if (lotroRoot == null) {
            return Optional.empty();
        }
        return Optional.of(lotroRoot.resolve(MUSIC_FOLDER_NAME).toAbsolutePath().normalize());
    }

    public static Optional<Path> pluginDataRoot(String lotroRoot) {
        if (lotroRoot == null || lotroRoot.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(lotroRoot.trim()).resolve(PLUGIN_DATA_FOLDER_NAME)
                .toAbsolutePath().normalize());
    }

    /**
     * Store set-export / exclude paths relative to Music when under Music.
     *
     * <p>Non-absolute input is treated as already Music-relative (never resolved against the
     * process working directory — that previously mangled a relative Music path into
     * {@code {cwd}/...} on Save). Absolute paths under Music are converted using real
     * paths when the folders exist (Python {@code Path.resolve()} parity). Relative form uses
     * forward slashes.
     */
    public static String toMusicRelative(String path, String lotroRoot) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String trimmed = path.trim();
        Path input = Paths.get(trimmed);
        if (!input.isAbsolute()) {
            return trimmed.replace('\\', '/');
        }
        Optional<Path> music = musicRoot(lotroRoot);
        if (music.isEmpty()) {
            return trimmed;
        }
        try {
            Path musicCanon = canonicalize(music.get());
            Path fullCanon = canonicalize(input);
            if (!isUnder(fullCanon, musicCanon)) {
                return trimmed;
            }
            return musicCanon.relativize(fullCanon).toString().replace('\\', '/');
        } catch (RuntimeException ex) {
            return trimmed;
        }
    }

    /**
     * True when {@code path} is under {@code lotroRoot/Music} (absolute or Music-relative).
     */
    public static boolean isUnderMusic(String path, String lotroRoot) {
        if (path == null || path.isBlank() || lotroRoot == null || lotroRoot.isBlank()) {
            return false;
        }
        String relative = toMusicRelative(path, lotroRoot);
        if (relative.isBlank()) {
            return false;
        }
        Path asPath = Paths.get(relative);
        return !asPath.isAbsolute() && !relative.startsWith("..");
    }

    /**
     * Resolve a stored Music-relative or absolute path to an absolute path for browsing/ops.
     * Relative values are always joined under Music (never under the process CWD).
     */
    public static String resolveMusicPath(String relativeOrAbsolute, String lotroRoot) {
        if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
            return "";
        }
        Path path = Paths.get(relativeOrAbsolute.trim());
        if (path.isAbsolute()) {
            return canonicalize(path).toString();
        }
        Optional<Path> music = musicRoot(lotroRoot);
        if (music.isEmpty()) {
            return relativeOrAbsolute.trim();
        }
        return canonicalize(music.get().resolve(path)).toString();
    }

    /**
     * Prefer the filesystem real path when the location exists (junctions / OneDrive),
     * otherwise absolute + normalize.
     */
    static Path canonicalize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(absolute)) {
                return absolute.toRealPath();
            }
        } catch (IOException ignored) {
            // fall through
        }
        return absolute;
    }

    private static boolean isUnder(Path path, Path parent) {
        Path p = path.toAbsolutePath().normalize();
        Path pre = parent.toAbsolutePath().normalize();
        return p.startsWith(pre);
    }

    /**
     * Discover account folders under {@code lotroRoot/PluginData}/{account}/AllServers}.
     */
    public static List<DiscoveredAccount> discoverAccountTargets(String lotroRoot) {
        List<DiscoveredAccount> found = new ArrayList<>();
        Optional<Path> pluginData = pluginDataRoot(lotroRoot);
        if (pluginData.isEmpty() || !Files.isDirectory(pluginData.get())) {
            return found;
        }
        try (var stream = Files.list(pluginData.get())) {
            stream.filter(Files::isDirectory)
                    .sorted()
                    .forEach(child -> {
                        String accountName = child.getFileName().toString();
                        Path allServers = child.resolve(ALL_SERVERS_FOLDER_NAME);
                        found.add(new DiscoveredAccount(
                                accountName,
                                allServers.toAbsolutePath().normalize().toString()));
                    });
        } catch (IOException ignored) {
            // return whatever was collected
        }
        return found;
    }

    public record DiscoveredAccount(String accountName, String pluginDataPath) {
    }

    private static Optional<Path> findLotroUnder(Path documents) {
        if (documents == null || !Files.isDirectory(documents)) {
            return Optional.empty();
        }
        for (String folderName : List.of(LOTRO_FOLDER_NAME, LOTRO_FOLDER_NAME_ALT)) {
            Path lotro = documents.resolve(folderName);
            if (Files.isDirectory(lotro)) {
                return Optional.of(lotro.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Reads the Windows Documents Known Folder via the User Shell Folders registry value
     * {@code Personal}, expanding {@code %USERPROFILE%} and similar env vars.
     */
    private static Optional<Path> documentsPathFromWindowsRegistry() {
        ProcessBuilder builder = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                "/v",
                "Personal");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.toLowerCase(Locale.ROOT).startsWith("personal")) {
                    continue;
                }
                // REG_EXPAND_SZ / REG_SZ    <path>
                String[] parts = trimmed.split("\\s{2,}");
                if (parts.length < 3) {
                    continue;
                }
                String raw = parts[parts.length - 1].trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String expanded = expandWindowsEnv(raw);
                Path path = Paths.get(expanded);
                if (Files.isDirectory(path)) {
                    return Optional.of(path.toAbsolutePath().normalize());
                }
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private static String expandWindowsEnv(String raw) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (raw.charAt(i) == '%') {
                int end = raw.indexOf('%', i + 1);
                if (end > i + 1) {
                    String name = raw.substring(i + 1, end);
                    String value = System.getenv(name);
                    if (value != null) {
                        result.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(raw.charAt(i));
            i++;
        }
        return result.toString();
    }
}

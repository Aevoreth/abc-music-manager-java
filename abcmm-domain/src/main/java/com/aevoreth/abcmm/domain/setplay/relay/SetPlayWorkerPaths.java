package com.aevoreth.abcmm.domain.setplay.relay;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Locate bundled Set Play worker template and writable deploy directory.
 * Mirrors Python {@code set_play_worker_paths}.
 */
public final class SetPlayWorkerPaths {

    private SetPlayWorkerPaths() {
    }

    /**
     * Directory containing {@code package.json} and {@code wrangler.toml}.
     * Dev: {@code <repo>/workers/set-play-relay}.
     * Packaged (jpackage): {@code <app-image>/app/workers/set-play-relay} next to the main jar,
     * or {@code <app-image>/workers/set-play-relay} beside the launcher.
     */
    public static Optional<Path> workerTemplateBundlePath() {
        Path fromProp = pathFromSystemProperty();
        if (fromProp != null) {
            return Optional.of(fromProp);
        }
        Path repo = findRepoRoot();
        if (repo != null) {
            Optional<Path> fromRepo = asWorkerTemplate(repo.resolve("workers").resolve("set-play-relay"));
            if (fromRepo.isPresent()) {
                return fromRepo;
            }
        }
        Path jarDir = jarParentDirectory();
        if (jarDir != null) {
            Optional<Path> besideJar = asWorkerTemplate(jarDir.resolve("workers").resolve("set-play-relay"));
            if (besideJar.isPresent()) {
                return besideJar;
            }
            // jpackage: jar lives under app/; also accept workers next to the .exe
            Path appImageRoot = jarDir.getParent();
            if (appImageRoot != null) {
                Optional<Path> besideExe = asWorkerTemplate(
                        appImageRoot.resolve("workers").resolve("set-play-relay"));
                if (besideExe.isPresent()) {
                    return besideExe;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> asWorkerTemplate(Path candidate) {
        if (candidate != null
                && Files.isDirectory(candidate)
                && Files.isRegularFile(candidate.resolve("package.json"))) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Development: {@code REPO_ROOT/set-play-relay-deploy}.
     * Otherwise: {@code LOCALAPPDATA/ABC-Music-Manager/set-play-relay-deploy} (Windows)
     * or {@code ~/.cache/abc-music-manager/set-play-relay-deploy}.
     */
    public static Path resolveSetPlayDeployDirectory() throws IOException {
        Path repo = findRepoRoot();
        if (repo != null) {
            Path d = repo.resolve("set-play-relay-deploy");
            Files.createDirectories(d);
            return d;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null || local.isBlank()) {
                local = Path.of(System.getProperty("user.home"), "AppData", "Local").toString();
            }
            Path fallback = Path.of(local, "ABC-Music-Manager", "set-play-relay-deploy");
            Files.createDirectories(fallback);
            return fallback;
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = (xdg == null || xdg.isBlank())
                ? Path.of(System.getProperty("user.home"), ".cache")
                : Path.of(xdg);
        Path fallback = base.resolve("abc-music-manager").resolve("set-play-relay-deploy");
        Files.createDirectories(fallback);
        return fallback;
    }

    /**
     * Copy worker sources into deploy, skipping {@code node_modules} and {@code .wrangler};
     * preserves existing {@code node_modules} in the deploy dir.
     */
    public static void syncTemplateToDeploy(Path bundle, Path deploy, Consumer<String> logLine)
            throws IOException {
        if (bundle == null || !Files.isDirectory(bundle)) {
            throw new IOException("Worker bundle not found: " + bundle);
        }
        Files.createDirectories(deploy);
        try (var stream = Files.list(bundle)) {
            for (Path item : stream.toList()) {
                String name = item.getFileName().toString();
                if ("node_modules".equals(name) || ".wrangler".equals(name)) {
                    continue;
                }
                Path dest = deploy.resolve(name);
                try {
                    if (Files.isRegularFile(item)) {
                        Files.copy(item, dest, StandardCopyOption.REPLACE_EXISTING);
                    } else if (Files.isDirectory(item)) {
                        copyDirectory(item, dest);
                    }
                } catch (IOException e) {
                    if (logLine != null) {
                        logLine.accept("Copy warning (" + name + "): " + e.getMessage());
                    }
                    throw e;
                }
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                Path destDir = target.resolve(rel.toString());
                Files.createDirectories(destDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(file);
                Path destFile = target.resolve(rel.toString());
                Files.createDirectories(destFile.getParent());
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path pathFromSystemProperty() {
        String prop = System.getProperty("abcmm.setPlayWorkerTemplate");
        if (prop == null || prop.isBlank()) {
            return null;
        }
        Path p = Path.of(prop);
        return Files.isDirectory(p) ? p : null;
    }

    private static Path findRepoRoot() {
        Path here = Path.of("").toAbsolutePath().normalize();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("workers").resolve("set-play-relay"))
                    && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        // When tests run from abcmm-domain module cwd
        Path parent = here.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("workers").resolve("set-play-relay"))
                && Files.isRegularFile(parent.resolve("pom.xml"))) {
            return parent;
        }
        return null;
    }

    private static Path jarParentDirectory() {
        try {
            var loc = SetPlayWorkerPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            Path p = Path.of(loc.toURI());
            if (Files.isRegularFile(p)) {
                return p.getParent();
            }
            return p;
        } catch (Exception ex) {
            return null;
        }
    }
}

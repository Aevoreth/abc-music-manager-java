package com.aevoreth.abcmm.domain.setplay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates and extracts set zips (zip-slip and size checks).
 */
public final class SetPlayZipExtract {

    public static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    public static final int MAX_ENTRIES = 5000;

    private SetPlayZipExtract() {
    }

    public static void validate(byte[] zipBytes) throws IOException {
        inspect(zipBytes, null);
    }

    public static void extractTo(byte[] zipBytes, Path destinationDir) throws IOException {
        inspect(zipBytes, destinationDir);
    }

    private static void inspect(byte[] zipBytes, Path destinationDir) throws IOException {
        Path dest = destinationDir == null ? null : destinationDir.toAbsolutePath().normalize();
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IOException("Zip has too many entries.");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    throw new IOException("Unsafe zip path: " + entry.getName());
                }
                if (dest != null) {
                    Path target = dest.resolve(name).normalize();
                    if (!target.startsWith(dest)) {
                        throw new IOException("Unsafe zip path: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        copyLimited(in, target);
                    }
                } else if (!entry.isDirectory()) {
                    drainLimited(in);
                }
            }
        }
        if (entries == 0) {
            throw new IOException("Zip is empty.");
        }
    }

    private static void copyLimited(InputStream in, Path target) throws IOException {
        try (var out = Files.newOutputStream(target)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_ENTRY_BYTES) {
                    throw new IOException("Zip entry is too large.");
                }
                out.write(buf, 0, n);
            }
        }
    }

    private static void drainLimited(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("Zip entry is too large.");
            }
        }
    }

    public static String folderNameFromZipFile(String zipFileName) {
        String name = zipFileName == null ? "set" : zipFileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isBlank() ? "set" : name;
    }

    public static List<String> listEntryNames(byte[] zipBytes) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}

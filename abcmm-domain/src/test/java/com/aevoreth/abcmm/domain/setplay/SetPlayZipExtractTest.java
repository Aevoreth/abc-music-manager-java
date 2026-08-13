package com.aevoreth.abcmm.domain.setplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetPlayZipExtractTest {

    @TempDir
    Path tempDir;

    @Test
    void folderNameStripsZipSuffix() {
        assertEquals("Friday-set", SetPlayZipExtract.folderNameFromZipFile("Friday-set.zip"));
        assertEquals("set", SetPlayZipExtract.folderNameFromZipFile(""));
    }

    @Test
    void validateRejectsZipSlip() throws Exception {
        byte[] zip = zipWithEntry("../evil.txt", "nope");
        assertThrows(IOException.class, () -> SetPlayZipExtract.validate(zip));
    }

    @Test
    void validateRejectsAbsolutePath() throws Exception {
        byte[] zip = zipWithEntry("/tmp/evil.txt", "nope");
        assertThrows(IOException.class, () -> SetPlayZipExtract.validate(zip));
    }

    @Test
    void extractWritesSafeEntry() throws Exception {
        byte[] zip = zipWithEntry("song.abc", "X:1\n");
        Path dest = tempDir.resolve("out");
        Files.createDirectories(dest);
        SetPlayZipExtract.extractTo(zip, dest);
        assertTrue(Files.isRegularFile(dest.resolve("song.abc")));
        assertEquals("X:1\n", Files.readString(dest.resolve("song.abc")));
    }

    private static byte[] zipWithEntry(String name, String content) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buf)) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return buf.toByteArray();
    }
}

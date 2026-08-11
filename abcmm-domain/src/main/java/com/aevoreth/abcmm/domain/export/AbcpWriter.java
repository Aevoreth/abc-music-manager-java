package com.aevoreth.abcmm.domain.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * ABCP playlist writer compatible with ABC Player ({@code fileVersion} 3.4.0.300).
 */
public final class AbcpWriter {

    private AbcpWriter() {
    }

    public static void write(Path path, List<String> trackPaths) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(trackPaths, "trackPaths");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.1\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        xml.append("<playlist fileVersion=\"3.4.0.300\">\n");
        xml.append("    <trackList>\n");
        for (String filePath : trackPaths) {
            xml.append("        <track>\n");
            xml.append("            <location>");
            xml.append(escapeXml(filePath == null ? "" : filePath));
            xml.append("</location>\n");
            xml.append("        </track>\n");
        }
        xml.append("    </trackList>\n");
        xml.append("</playlist>\n");

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, xml.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

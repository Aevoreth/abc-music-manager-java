package com.aevoreth.abcmm.domain.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbcpReaderTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsWithAbcpWriter() throws Exception {
        Path path = dir.resolve("set.abcp");
        List<String> tracks = List.of(
                "C:\\Music\\alpha.abc",
                "C:\\Music\\beta.abc");
        AbcpWriter.write(path, tracks);
        assertEquals(tracks, AbcpReader.read(path));
    }

    @Test
    void returnsEmptyWhenTrackListMissingOrEmpty() throws Exception {
        Path missingList = dir.resolve("no-tracks.abcp");
        Files.writeString(
                missingList,
                """
                        <?xml version="1.1" encoding="UTF-8" standalone="no"?>
                        <playlist fileVersion="3.4.0.300">
                        </playlist>
                        """,
                StandardCharsets.UTF_8);
        assertEquals(List.of(), AbcpReader.read(missingList));

        Path emptyList = dir.resolve("empty-list.abcp");
        Files.writeString(
                emptyList,
                """
                        <?xml version="1.1" encoding="UTF-8" standalone="no"?>
                        <playlist fileVersion="3.4.0.300">
                            <trackList>
                            </trackList>
                        </playlist>
                        """,
                StandardCharsets.UTF_8);
        assertEquals(List.of(), AbcpReader.read(emptyList));
    }

    @Test
    void rejectsWrongRootAndMalformedXml() throws Exception {
        Path wrongRoot = dir.resolve("wrong.abcp");
        Files.writeString(
                wrongRoot,
                """
                        <?xml version="1.1" encoding="UTF-8"?>
                        <notPlaylist>
                            <trackList/>
                        </notPlaylist>
                        """,
                StandardCharsets.UTF_8);
        AbcpException wrong = assertThrows(AbcpException.class, () -> AbcpReader.read(wrongRoot));
        assertTrue(wrong.getMessage().contains("playlist"));

        Path malformed = dir.resolve("bad.abcp");
        Files.writeString(malformed, "<playlist><trackList>", StandardCharsets.UTF_8);
        AbcpException bad = assertThrows(AbcpException.class, () -> AbcpReader.read(malformed));
        assertTrue(bad.getMessage().contains("Invalid ABCP XML"));
    }

    @Test
    void skipsBlankLocationsAndUnescapesXml() throws Exception {
        Path path = dir.resolve("mixed.abcp");
        Files.writeString(
                path,
                """
                        <?xml version="1.1" encoding="UTF-8" standalone="no"?>
                        <playlist fileVersion="3.4.0.300">
                            <trackList>
                                <track>
                                    <location>  C:\\Music\\a &amp; b.abc  </location>
                                </track>
                                <track>
                                    <location>   </location>
                                </track>
                                <track>
                                    <location>C:\\Music\\c.abc</location>
                                </track>
                            </trackList>
                        </playlist>
                        """,
                StandardCharsets.UTF_8);
        assertEquals(
                List.of("C:\\Music\\a & b.abc", "C:\\Music\\c.abc"),
                AbcpReader.read(path));
    }
}

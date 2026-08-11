package com.aevoreth.abcmm.domain.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * ABCP playlist reader compatible with ABC Player playlists written by {@link AbcpWriter}.
 */
public final class AbcpReader {

    private AbcpReader() {
    }

    /**
     * Parse an ABCP file and return ordered track paths.
     *
     * @throws AbcpException if the file is missing, malformed, or not a playlist document
     */
    public static List<String> read(Path path) throws AbcpException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new AbcpException("ABCP file not found: " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);
            Element root = document.getDocumentElement();
            if (root == null) {
                throw new AbcpException("Invalid ABCP XML: missing root element");
            }
            if (!"playlist".equals(root.getTagName())) {
                throw new AbcpException(
                        "Expected root element 'playlist', got '" + root.getTagName() + "'");
            }
            Element trackList = firstChildElement(root, "trackList");
            if (trackList == null) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (Element track : childElements(trackList, "track")) {
                Element location = firstChildElement(track, "location");
                if (location == null) {
                    continue;
                }
                String text = location.getTextContent();
                if (text == null) {
                    continue;
                }
                String trimmed = text.strip();
                if (!trimmed.isEmpty()) {
                    paths.add(trimmed);
                }
            }
            return List.copyOf(paths);
        } catch (ParserConfigurationException | SAXException ex) {
            throw new AbcpException("Invalid ABCP XML: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new AbcpException("Could not read ABCP file: " + ex.getMessage(), ex);
        }
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && tagName.equals(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && tagName.equals(((Element) node).getTagName())) {
                result.add((Element) node);
            }
        }
        return result;
    }
}

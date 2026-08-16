package io.runtimerocket.agent.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Independent {@code runtimerocket.xml} parser. Not a JRebel {@code rebel.xml} clone. */
public final class RocketXmlParser {

    public static final String NAMESPACE = "https://runtimerocket.io/ns/config";
    public static final String FILE_NAME = "runtimerocket.xml";
    public static final String VERSION_1 = "1";

    private RocketXmlParser() {}

    public static RocketXml parse(Path file) {
        if (file == null) {
            throw new RocketXmlException("config path is null");
        }
        Path abs = file.toAbsolutePath().normalize();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(abs);
        } catch (IOException e) {
            throw new RocketXmlException("cannot read " + abs, e);
        }
        return parse(new ByteArrayInputStream(bytes), abs, userDir());
    }

    public static RocketXml parse(String xml, Path source, Path relativeBase) {
        if (xml == null) {
            throw new RocketXmlException("xml is null");
        }
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), source, relativeBase);
    }

    public static RocketXml parse(InputStream in, Path source, Path relativeBase) {
        if (in == null) {
            throw new RocketXmlException("xml stream is null");
        }
        Path base = relativeBase == null ? userDir() : relativeBase.toAbsolutePath().normalize();
        Document doc;
        try {
            DocumentBuilderFactory factory = secureFactory();
            doc = factory.newDocumentBuilder().parse(in);
        } catch (RocketXmlException e) {
            throw e;
        } catch (Exception e) {
            throw new RocketXmlException("cannot parse " + label(source), e);
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"runtimerocket".equals(local(root))) {
            throw new RocketXmlException("root element must be runtimerocket in " + label(source));
        }
        String version = root.getAttribute("version");
        if (version != null && !version.isBlank() && !VERSION_1.equals(version.trim())) {
            throw new RocketXmlException("unsupported runtimerocket.xml version '" + version + "'");
        }
        String id = textChild(root, "id");
        if (id != null) {
            id = id.trim();
            if (id.isEmpty()) {
                id = null;
            }
        }
        List<Path> classpath = dirNames(child(root, "classpath"), base, source);
        List<Path> resources = dirNames(child(root, "resources"), base, source);
        Element packages = child(root, "packages");
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        if (packages != null) {
            collectTexts(packages, "include", includes);
            collectTexts(packages, "exclude", excludes);
        }
        return new RocketXml(id, source, classpath, resources, includes, excludes);
    }

    static Path userDir() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    static Path resolveDir(String raw, Path relativeBase) {
        if (raw == null || raw.isBlank()) {
            throw new RocketXmlException("dir name is empty");
        }
        Path path = Path.of(raw.trim());
        if (!path.isAbsolute()) {
            path = relativeBase.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    private static List<Path> dirNames(Element parent, Path relativeBase, Path source) {
        if (parent == null) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            if (!"dir".equals(local(el))) {
                continue;
            }
            String name = el.getAttribute("name");
            if (name == null || name.isBlank()) {
                throw new RocketXmlException("dir missing name in " + label(source));
            }
            out.add(resolveDir(name, relativeBase));
        }
        return out;
    }

    private static void collectTexts(Element parent, String tag, List<String> out) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            if (!tag.equals(local(el))) {
                continue;
            }
            String text = el.getTextContent();
            if (text != null && !text.isBlank()) {
                out.add(text.trim());
            }
        }
    }

    private static String textChild(Element parent, String tag) {
        Element child = child(parent, tag);
        if (child == null) {
            return null;
        }
        String text = child.getTextContent();
        return text == null ? null : text;
    }

    private static Element child(Element parent, String tag) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            if (tag.equals(local(el))) {
                return el;
            }
        }
        return null;
    }

    private static String local(Element el) {
        String local = el.getLocalName();
        return local == null ? el.getTagName() : local;
    }

    private static String label(Path source) {
        return source == null ? FILE_NAME : source.toString();
    }
}

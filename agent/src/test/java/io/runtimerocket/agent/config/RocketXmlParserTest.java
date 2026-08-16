package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocketXmlParserTest {

    @TempDir
    Path temp;

    @Test
    void parsesNamespacedDocument() throws Exception {
        Path classes = temp.resolve("classes");
        Path resources = temp.resolve("res");
        Files.createDirectories(classes);
        Files.createDirectories(resources);
        Path xml = write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <runtimerocket xmlns="https://runtimerocket.io/ns/config" version="1">
                  <id>com.example:app</id>
                  <classpath>
                    <dir name="%s"/>
                  </classpath>
                  <resources>
                    <dir name="%s"/>
                  </resources>
                  <packages>
                    <include>com.example.**</include>
                    <exclude>com.example.generated.**</exclude>
                  </packages>
                </runtimerocket>
                """
                        .formatted(classes.toAbsolutePath(), resources.toAbsolutePath()));

        RocketXml parsed = RocketXmlParser.parse(xml);
        assertEquals("com.example:app", parsed.id);
        assertEquals(classes.toAbsolutePath().normalize(), parsed.classpathDirs.get(0));
        assertEquals(resources.toAbsolutePath().normalize(), parsed.resourceDirs.get(0));
        assertEquals("com.example.**", parsed.includes.get(0));
        assertEquals("com.example.generated.**", parsed.excludes.get(0));
        assertTrue(parsed.packageFilter().accepts("com.example.Foo"));
        assertTrue(!parsed.packageFilter().accepts("com.example.generated.Bar"));
    }

    @Test
    void resolvesRelativePathsAgainstUserDir() throws Exception {
        Path xml = write(
                """
                <runtimerocket version="1">
                  <classpath>
                    <dir name="build/classes/java/main"/>
                  </classpath>
                </runtimerocket>
                """);
        RocketXml parsed = RocketXmlParser.parse(xml);
        Path expected = Path.of(System.getProperty("user.dir")).resolve("build/classes/java/main").toAbsolutePath().normalize();
        assertEquals(expected, parsed.classpathDirs.get(0));
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        Path xml = write("<runtimerocket version=\"2\"/>");
        assertThrows(RocketXmlException.class, () -> RocketXmlParser.parse(xml));
    }

    private Path write(String body) throws Exception {
        Path xml = temp.resolve("runtimerocket.xml");
        Files.writeString(xml, body);
        return xml;
    }
}

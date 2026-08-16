package io.runtimerocket.agent.config;

import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.AgentStartException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocketXmlDiscoveryTest {

    @TempDir
    Path temp;

    @Test
    void unionsClasspathAndPackagesFromTwoFiles() throws Exception {
        Path libDir = Files.createDirectories(temp.resolve("lib"));
        Path appDir = Files.createDirectories(temp.resolve("app"));
        Path libXml = write(libDir, "runtimerocket:lib", libDir, "demo.twomodule.lib.**", null);
        Path appXml = write(appDir, "runtimerocket:app", appDir, "demo.twomodule.app.**", "demo.twomodule.app.generated.**");

        RocketXmlDocuments docs = RocketXmlDiscovery.fromFiles(List.of(libXml, appXml));
        assertEquals(2, docs.documents().size());
        assertTrue(docs.classpathDirs().contains(libDir.toAbsolutePath().normalize()));
        assertTrue(docs.classpathDirs().contains(appDir.toAbsolutePath().normalize()));
        assertTrue(docs.packageFilter().accepts("demo.twomodule.lib.LibGreeter"));
        assertTrue(docs.packageFilter().accepts("demo.twomodule.app.App"));
        assertTrue(!docs.packageFilter().accepts("demo.twomodule.app.generated.Skip"));
    }

    @Test
    void configOptionIsRequiredWhenSet() {
        Path missing = temp.resolve("missing.xml");
        AgentOptions options = AgentOptions.parse("config=" + missing, new Properties());
        assertThrows(AgentStartException.class, () -> RocketXmlDiscovery.discover(options));
    }

    @Test
    void discoversXmlSittingInWatchDir() throws Exception {
        Path watch = Files.createDirectories(temp.resolve("out"));
        write(watch, "from-watch", watch, "demo.**", null);
        AgentOptions options = AgentOptions.parse("watchDir=" + watch.toAbsolutePath(), new Properties());
        RocketXmlDocuments docs = RocketXmlDiscovery.discover(options);
        assertEquals(1, docs.documents().size());
        assertEquals("from-watch", docs.documents().get(0).id);
        assertTrue(docs.classpathDirs().contains(watch.toAbsolutePath().normalize()));
    }

    private static Path write(Path dir, String id, Path classpath, String include, String exclude) throws Exception {
        String packages = "    <packages>\n      <include>" + include + "</include>\n";
        if (exclude != null) {
            packages += "      <exclude>" + exclude + "</exclude>\n";
        }
        packages += "    </packages>\n";
        String body =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <runtimerocket xmlns="https://runtimerocket.io/ns/config" version="1">
                  <id>%s</id>
                  <classpath>
                    <dir name="%s"/>
                  </classpath>
                %s</runtimerocket>
                """
                        .formatted(id, classpath.toAbsolutePath().toString().replace('\\', '/'), packages);
        Path xml = dir.resolve("runtimerocket.xml");
        Files.writeString(xml, body);
        return xml;
    }
}

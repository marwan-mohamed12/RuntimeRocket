package io.runtimerocket.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowManifestTest {

    @Test
    void fatJarManifestEnablesRedefineWithoutNativePrefix() throws IOException {
        Path jar = findShadowJar();
        assertNotNull(jar, "shadow jar missing under build/libs");
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            Attributes attrs = manifest.getMainAttributes();
            assertEquals("io.runtimerocket.agent.AgentMain", attrs.getValue("Premain-Class"));
            assertEquals("io.runtimerocket.agent.AgentMain", attrs.getValue("Agent-Class"));
            assertEquals("true", attrs.getValue("Can-Redefine-Classes"));
            assertEquals("true", attrs.getValue("Can-Retransform-Classes"));
            assertEquals("RuntimeRocket Agent", attrs.getValue("Implementation-Title"));
            assertNull(attrs.getValue("Can-Set-Native-Method-Prefix"));
            assertNotNull(jarFile.getJarEntry("io/runtimerocket/frameworks/spring/SpringAdapter.class"));
            assertNotNull(jarFile.getJarEntry("io/runtimerocket/frameworks/spring/fw7/Fw7SpringRefreshHelper.class"));
            JarEntry services = jarFile.getJarEntry("META-INF/services/io.runtimerocket.agent.spi.FrameworkAdapter");
            assertNotNull(services);
            String providers = new String(jarFile.getInputStream(services).readAllBytes());
            assertTrue(providers.contains("io.runtimerocket.frameworks.spring.SpringAdapter"), providers);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("net/bytebuddy/"), name);
            }
        }
    }

    private static Path findShadowJar() throws IOException {
        Path libs = Path.of("build", "libs");
        if (!Files.isDirectory(libs)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libs, "runtimerocket-agent-*-all.jar")) {
            for (Path path : stream) {
                return path;
            }
        }
        return null;
    }
}

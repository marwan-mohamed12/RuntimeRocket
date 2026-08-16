package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringVersion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringClasspathIsolationTest {

    @Test
    void mainTestClasspathIsFramework62Not7() {
        String version = SpringVersion.getVersion();
        assertNotNull(version);
        assertTrue(version.startsWith("6.2"), version);
        assertNotNull(
                SpringAdapter.class
                        .getClassLoader()
                        .getResource("io/runtimerocket/frameworks/spring/fw7/Fw7SpringRefreshHelper.class"),
                "fw7 helper must be packaged next to main classes");
    }
}

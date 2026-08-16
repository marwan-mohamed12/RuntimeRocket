package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringVersionGateTest {

    @Test
    void mainTestClasspathSelectsFramework6Helper() {
        String version = SpringVersion.getVersion();
        assertTrue(version != null && version.startsWith("6."), version);
        ClassLoader loader = SpringVersionGateTest.class.getClassLoader();
        assertEquals(6, SpringVersionGate.frameworkMajor(loader));
        assertEquals(SpringVersionGate.MAIN_HELPER, SpringVersionGate.helperClassName(loader));
        assertEquals(version, SpringVersionGate.frameworkVersion(loader));
    }
}

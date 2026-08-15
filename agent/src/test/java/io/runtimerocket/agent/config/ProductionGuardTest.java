package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionGuardTest {

    @Test
    void abortsOnKubernetesWithoutOverride() {
        assertTrue(ProductionGuard.shouldAbort(Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1"), new Properties()));
    }

    @Test
    void abortsOnAzureAndHerokuAndFlag() {
        assertTrue(ProductionGuard.shouldAbort(Map.of("WEBSITE_SITE_NAME", "app"), new Properties()));
        assertTrue(ProductionGuard.shouldAbort(Map.of("DYNO", "web.1"), new Properties()));
        Properties props = new Properties();
        props.setProperty("rr.production", "true");
        assertTrue(ProductionGuard.shouldAbort(Map.of(), props));
    }

    @Test
    void allowNonDevBypasses() {
        Properties props = new Properties();
        props.setProperty("rr.allowNonDev", "true");
        assertFalse(ProductionGuard.shouldAbort(Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1"), props));
        assertFalse(ProductionGuard.shouldAbort(
                Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1", "RUNTIMEROCKET_ALLOW_NONDEV", "true"),
                new Properties()));
    }

    @Test
    void prodAndPortAreWarningOnly() {
        Properties props = new Properties();
        props.setProperty("spring.profiles.active", "prod");
        Map<String, String> env = Map.of("PORT", "8080");
        assertFalse(ProductionGuard.shouldAbort(env, props));
        assertTrue(ProductionGuard.shouldWarnProdPort(env, props));
    }

    @Test
    void cleanDevEnvIsAllowed() {
        assertFalse(ProductionGuard.shouldAbort(Map.of(), new Properties()));
        assertFalse(ProductionGuard.shouldWarnProdPort(Map.of(), new Properties()));
    }
}

package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringResourcePolicyTest {

    @Test
    void bootConfigNamesRequireRestart() {
        assertTrue(SpringResourcePolicy.isBootConfigName("application.properties"));
        assertTrue(SpringResourcePolicy.isBootConfigName("application.yml"));
        assertTrue(SpringResourcePolicy.isBootConfigName("application.yaml"));
        assertTrue(SpringResourcePolicy.isBootConfigName("application-dev.properties"));
        assertFalse(SpringResourcePolicy.isBootConfigName("banner.txt"));

        SpringAdapter adapter = new SpringAdapter();
        AdapterOutcome outcome = adapter.onResourcesChanged(event("application.properties", "a".repeat(64)));
        assertEquals(AdapterOutcome.RESTART_REQUIRED, outcome.status);
        assertEquals(SpringAdapter.CONFIG_CHANGED_DETAIL, outcome.detail);
    }

    @Test
    void staticFilesAreOkOnDisk() {
        SpringAdapter adapter = new SpringAdapter();
        AdapterOutcome outcome = adapter.onResourcesChanged(event("static/app.js", "b".repeat(64)));
        assertEquals(AdapterOutcome.SUCCESS, outcome.status);
        assertEquals(SpringAdapter.STATIC_DETAIL, outcome.detail);
    }

    private static ResourceChangeEvent event(String classpathName, String sha) {
        return new ResourceChangeEvent(
                SpringTestSupport.context(false),
                List.of(new ResourceChangeEvent.ChangedResource(classpathName, "/tmp/" + classpathName, sha)));
    }
}

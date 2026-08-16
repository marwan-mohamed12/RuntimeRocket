package io.runtimerocket.frameworks.spring;

import io.runtimerocket.frameworks.spring.testapp.TestConfig;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-spring", mode = ResourceAccessMode.READ_WRITE)
class SpringLateAttachTest {

    private final SpringAdapter adapter = new SpringAdapter();
    private ConfigurableApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        adapter.onAgentShutdown();
        SpringContextTracker.reset();
    }

    @Test
    void idleBootLateAttachReturnsPartialImmediately() {
        SpringApplication application = new SpringApplication(TestConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        context = application.run();
        assertTrue(SpringContextTracker.isEmpty(), "finishRefresh already ran without the hook");

        long started = System.nanoTime();
        AdapterOutcome outcome = adapter.onLateAttach(SpringTestSupport.context(true));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertEquals(AdapterOutcome.PARTIAL, outcome.status);
        assertEquals(SpringAdapter.INACTIVE_DETAIL, outcome.detail);
        assertTrue(SpringContextTracker.isEmpty(), "must not wait for a later request");
        assertTrue(elapsedMs < 500L, "late attach must not wait 2s: " + elapsedMs + "ms");

        assertTrue(context.isActive());
        assertFalse(SpringContextTracker.isEmpty(), "isActive hook must register on the next call");
    }
}

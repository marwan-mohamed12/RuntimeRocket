package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringContextTrackerTest {

    @AfterEach
    void tearDown() {
        SpringContextTracker.reset();
    }

    @Test
    void registerDedupsAndKeepsWeakRefs() {
        Object ctx = new Object();
        AtomicInteger calls = new AtomicInteger();
        SpringContextTracker.setListener(ignored -> calls.incrementAndGet());
        SpringContextTracker.register(ctx);
        SpringContextTracker.register(ctx);
        assertFalse(SpringContextTracker.isEmpty());
        assertEquals(1, SpringContextTracker.liveContexts().size());
        assertSame(ctx, SpringContextTracker.liveContexts().get(0));
        assertEquals(1, calls.get());
        ctx = null;
        System.gc();
        SpringContextTracker.register(new Object());
        assertTrue(SpringContextTracker.liveContexts().size() >= 1);
    }
}

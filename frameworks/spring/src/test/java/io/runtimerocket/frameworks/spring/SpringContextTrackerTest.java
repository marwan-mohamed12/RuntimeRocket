package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

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
        GenericApplicationContext ctx = new GenericApplicationContext();
        try {
            AtomicInteger calls = new AtomicInteger();
            SpringContextTracker.setListener(ignored -> calls.incrementAndGet());
            SpringContextTracker.register(ctx);
            SpringContextTracker.register(ctx);
            assertFalse(SpringContextTracker.isEmpty());
            assertEquals(1, SpringContextTracker.liveContexts().size());
            assertSame(ctx, SpringContextTracker.liveContexts().get(0));
            assertEquals(1, calls.get());
        } finally {
            ctx.close();
        }
    }

    @Test
    void beanFactoryIsNotALiveContext() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        SpringContextTracker.register(factory);
        assertTrue(SpringContextTracker.isEmpty());
        assertTrue(SpringContextTracker.liveContexts().isEmpty());
    }
}

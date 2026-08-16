package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.spi.AdapterContext;
import io.runtimerocket.agent.spi.Capabilities;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.FrameworkAdapter;
import io.runtimerocket.agent.spi.NoOpFrameworkAdapter;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterHostTest {

    @Test
    void serviceLoaderDiscoversNoOpAdapter() {
        AdapterHost host = AdapterHost.load(AdapterHostTest.class.getClassLoader(), List.of(), null);
        assertTrue(
                host.registered().stream().anyMatch(adapter -> NoOpFrameworkAdapter.ID.equals(adapter.id())),
                host.registered().toString());
    }

    @Test
    void exceptionInOneAdapterDoesNotPreventTheNext() {
        AdapterHost host = new AdapterHost(List.of(), null);
        RecordingAdapter first = new RecordingAdapter("boom", 10, true);
        RecordingAdapter second = new RecordingAdapter("witness", 20, false);
        host.register(first);
        host.register(second);

        List<AdapterOutcome> outcomes = host.onClassesReloaded(event());
        assertEquals(2, outcomes.size(), outcomes.toString());
        assertEquals("boom", outcomes.get(0).adapterId);
        assertEquals(AdapterOutcome.FAILED, outcomes.get(0).status);
        assertEquals("forced-fail", outcomes.get(0).detail);
        assertEquals("witness", outcomes.get(1).adapterId);
        assertEquals(AdapterOutcome.SUCCESS, outcomes.get(1).status);
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void skipsDisabledAndUnavailableAdapters() {
        AdapterHost host = new AdapterHost(List.of("off"), null);
        RecordingAdapter disabled = new RecordingAdapter("off", 1, false);
        RecordingAdapter unavailable = new RecordingAdapter("hidden", 2, false);
        unavailable.available = false;
        RecordingAdapter active = new RecordingAdapter("on", 3, false);
        host.register(disabled);
        host.register(unavailable);
        host.register(active);

        List<AdapterOutcome> outcomes = host.onClassesReloaded(event());
        assertEquals(1, outcomes.size());
        assertEquals("on", outcomes.get(0).adapterId);
        assertEquals(0, disabled.calls.get());
        assertEquals(0, unavailable.calls.get());
        assertEquals(1, active.calls.get());

        host.onNewClass(String.class);
        assertEquals(0, disabled.newClasses.get());
        assertEquals(0, unavailable.newClasses.get());
        assertEquals(1, active.newClasses.get());

        host.onAgentShutdown();
        assertEquals(0, disabled.shutdowns.get());
        assertEquals(1, unavailable.shutdowns.get());
        assertEquals(1, active.shutdowns.get());
    }

    @Test
    void isAvailableExceptionDoesNotAbortDispatch() {
        AdapterHost host = new AdapterHost(List.of(), null);
        host.register(new FrameworkAdapter() {
            @Override
            public String id() {
                return "probe-fail";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public boolean isAvailable(ClassLoader appLoader) {
                throw new IllegalStateException("probe exploded");
            }

            @Override
            public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
                throw new AssertionError("should not run");
            }
        });
        RecordingAdapter witness = new RecordingAdapter("witness", 2, false);
        host.register(witness);

        List<AdapterOutcome> outcomes = host.onClassesReloaded(event());
        assertEquals(1, outcomes.size());
        assertEquals("witness", outcomes.get(0).adapterId);
        assertEquals(1, witness.calls.get());
    }

    @Test
    void softFailedDetectsFailedAndPartial() {
        assertFalse(AdapterHost.softFailed(List.of(AdapterOutcome.ok("a"))));
        assertTrue(AdapterHost.softFailed(List.of(
                AdapterOutcome.ok("a"),
                new AdapterOutcome("b", AdapterOutcome.FAILED, 1L, "x"))));
        assertTrue(AdapterHost.softFailed(
                List.of(new AdapterOutcome("c", AdapterOutcome.PARTIAL, 1L, "soft"))));
    }

    private static ClassReloadEvent event() {
        return new ClassReloadEvent(
                new StubContext(),
                List.of(new ReloadedClass("demo.Foo", String.class, List.of("METHOD_BODY"))),
                "standard",
                Capabilities.fromNames(List.of("METHOD_BODY")));
    }

    private static final class RecordingAdapter implements FrameworkAdapter {
        private final String id;
        private final int order;
        private final boolean throwOnReload;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger newClasses = new AtomicInteger();
        private final AtomicInteger shutdowns = new AtomicInteger();
        private boolean available = true;

        RecordingAdapter(String id, int order, boolean throwOnReload) {
            this.id = id;
            this.order = order;
            this.throwOnReload = throwOnReload;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean isAvailable(ClassLoader appLoader) {
            return available;
        }

        @Override
        public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
            calls.incrementAndGet();
            if (throwOnReload) {
                throw new IllegalStateException("forced-fail");
            }
            return AdapterOutcome.ok(id);
        }

        @Override
        public AdapterOutcome onResourcesChanged(ResourceChangeEvent event) {
            return AdapterOutcome.ok(id);
        }

        @Override
        public void onNewClass(Class<?> type) {
            newClasses.incrementAndGet();
        }

        @Override
        public void onAgentShutdown() {
            shutdowns.incrementAndGet();
        }
    }

    private static final class StubContext implements AdapterContext {
        @Override
        public ClassLoader[] applicationLoaders() {
            return new ClassLoader[] {AdapterHostTest.class.getClassLoader()};
        }

        @Override
        public boolean isLateAttach() {
            return false;
        }

        @Override
        public void log(String level, String msg) {}

        @Override
        public <T> T peekService(Class<T> type) {
            return null;
        }
    }
}

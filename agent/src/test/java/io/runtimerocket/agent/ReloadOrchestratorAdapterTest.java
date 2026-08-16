package io.runtimerocket.agent;

import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.reload.AdapterHost;
import io.runtimerocket.agent.reload.ClassIndex;
import io.runtimerocket.agent.reload.ReloadOrchestrator;
import io.runtimerocket.agent.reload.StandardHotSwapBackend;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.FrameworkAdapter;
import io.runtimerocket.protocol.AdapterOutcome;
import io.runtimerocket.protocol.ClassOutcome;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;
import io.runtimerocket.protocol.ResourcePayload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class ReloadOrchestratorAdapterTest {

    @TempDir
    Path temp;

    @Test
    void thrownAdapterDoesNotRollbackRedefineAndYieldsPartial() throws Exception {
        String name = TestClasses.uniqueBinary("PartialBody");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();

        AdapterHost host = new AdapterHost(List.of(), null);
        host.register(new BoomAdapter());
        ClassIndex index = primedIndex(type, before);
        ReloadOrchestrator orchestrator = orchestrator(host, index, WatchDirs.of(List.of()));

        ReloadResult result = orchestrator.reload(inline(name, after));
        assertEquals(ReloadResult.PARTIAL, result.status, result.message);
        assertEquals(ClassOutcome.REDEFINED, result.classes.get(0).status);
        assertEquals(1, result.adapters.size());
        assertEquals("boom", result.adapters.get(0).adapterId);
        assertEquals(AdapterOutcome.FAILED, result.adapters.get(0).status);
        assertEquals("adapter-boom", result.adapters.get(0).detail);
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    @Test
    void adaptersAreNotNotifiedBeforeUnsupportedAbort() {
        String name = TestClasses.uniqueBinary("NoNotify");
        byte[] before = TestClasses.optionalExtraMethod(name, false);
        byte[] after = TestClasses.optionalExtraMethod(name, true);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);

        AdapterHost host = new AdapterHost(List.of(), null);
        CountingAdapter adapter = new CountingAdapter();
        host.register(adapter);
        ReloadOrchestrator orchestrator = orchestrator(host, primedIndex(type, before), WatchDirs.of(List.of()));

        ReloadResult result = orchestrator.reload(inline(name, after));
        assertEquals(ReloadResult.RESTART_REQUIRED, result.status, result.message);
        assertEquals(0, adapter.reloads.get());
        assertTrue(result.adapters == null || result.adapters.isEmpty());
    }

    @Test
    void resourceAdapterFailureIsPartial() throws Exception {
        Path file = temp.resolve("application.properties");
        Files.writeString(file, "a=1");

        AdapterHost host = new AdapterHost(List.of(), null);
        host.register(new FrameworkAdapter() {
            @Override
            public String id() {
                return "res";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public boolean isAvailable(ClassLoader appLoader) {
                return true;
            }

            @Override
            public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
                return AdapterOutcome.ok(id());
            }

            @Override
            public AdapterOutcome onResourcesChanged(io.runtimerocket.agent.spi.ResourceChangeEvent event) {
                return new AdapterOutcome(id(), AdapterOutcome.FAILED, 0L, "config-stale");
            }
        });

        ReloadOrchestrator orchestrator = orchestrator(
                host, new ClassIndex(AgentTestSupport.instrumentation()), WatchDirs.of(List.of(temp)));

        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.resources = List.of(new ResourcePayload("application.properties", file.toString(), null));
        ReloadResult result = orchestrator.reload(request);
        assertEquals(ReloadResult.PARTIAL, result.status, result.message);
        assertEquals("config-stale", result.message);
        assertEquals(AdapterOutcome.FAILED, result.adapters.get(0).status);
    }

    @Test
    void adapterRestartRequiredPromotesReloadResult() throws Exception {
        Path file = temp.resolve("application.properties");
        Files.writeString(file, "a=1");

        AdapterHost host = new AdapterHost(List.of(), null);
        host.register(new FrameworkAdapter() {
            @Override
            public String id() {
                return "spring";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public boolean isAvailable(ClassLoader appLoader) {
                return true;
            }

            @Override
            public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
                return AdapterOutcome.ok(id());
            }

            @Override
            public AdapterOutcome onResourcesChanged(io.runtimerocket.agent.spi.ResourceChangeEvent event) {
                return new AdapterOutcome(id(), AdapterOutcome.RESTART_REQUIRED, 0L, "config changed — restart to apply");
            }
        });

        ReloadOrchestrator orchestrator = orchestrator(
                host, new ClassIndex(AgentTestSupport.instrumentation()), WatchDirs.of(List.of(temp)));

        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.resources = List.of(new ResourcePayload("application.properties", file.toString(), null));
        ReloadResult result = orchestrator.reload(request);
        assertEquals(ReloadResult.RESTART_REQUIRED, result.status, result.message);
        assertEquals("config changed — restart to apply", result.message);
    }

    private static ReloadOrchestrator orchestrator(AdapterHost host, ClassIndex index, WatchDirs watchDirs) {
        return new ReloadOrchestrator(
                AgentTestSupport.instrumentation(),
                new StandardHotSwapBackend(),
                index,
                watchDirs,
                null,
                null,
                host,
                false);
    }

    private static ClassIndex primedIndex(Class<?> type, byte[] before) {
        ClassIndex index = new ClassIndex(AgentTestSupport.instrumentation());
        index.recordClass(type);
        index.storeBytes(type.getClassLoader(), type.getName(), before);
        return index;
    }

    private static ReloadRequest inline(String binaryName, byte[] bytes) {
        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.classes = List.of(new ClassPayload(binaryName, null, null, TestClasses.b64(bytes)));
        return request;
    }

    private static final class BoomAdapter implements FrameworkAdapter {
        @Override
        public String id() {
            return "boom";
        }

        @Override
        public int order() {
            return 1;
        }

        @Override
        public boolean isAvailable(ClassLoader appLoader) {
            return true;
        }

        @Override
        public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
            throw new IllegalStateException("adapter-boom");
        }
    }

    private static final class CountingAdapter implements FrameworkAdapter {
        private final AtomicInteger reloads = new AtomicInteger();

        @Override
        public String id() {
            return "count";
        }

        @Override
        public int order() {
            return 1;
        }

        @Override
        public boolean isAvailable(ClassLoader appLoader) {
            return true;
        }

        @Override
        public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
            reloads.incrementAndGet();
            return AdapterOutcome.ok(id());
        }
    }
}

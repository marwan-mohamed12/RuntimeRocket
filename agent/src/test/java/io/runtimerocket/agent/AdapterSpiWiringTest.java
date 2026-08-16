package io.runtimerocket.agent;

import io.runtimerocket.agent.reload.AdapterHost;
import io.runtimerocket.agent.spi.NoOpFrameworkAdapter;
import io.runtimerocket.protocol.AdapterOutcome;
import io.runtimerocket.protocol.ClassOutcome;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class AdapterSpiWiringTest {

    @TempDir
    Path temp;

    @AfterEach
    void tearDown() {
        AgentTestSupport.stop();
    }

    @Test
    void serviceLoaderNoOpRunsAfterRedefineAndKeepsSuccess() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        AdapterHost host = AgentRuntime.get().adapterHost();
        assertNotNull(host);
        assertTrue(
                host.registered().stream().anyMatch(adapter -> NoOpFrameworkAdapter.ID.equals(adapter.id())),
                host.registered().toString());

        String name = TestClasses.uniqueBinary("SpiBody");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(inline(name, after));
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
            assertEquals(ClassOutcome.REDEFINED, result.classes.get(0).status);
            assertTrue(
                    result.adapters.stream()
                            .anyMatch(outcome ->
                                    NoOpFrameworkAdapter.ID.equals(outcome.adapterId)
                                            && AdapterOutcome.SUCCESS.equals(outcome.status)),
                    String.valueOf(result.adapters));
            assertNotNull(client.ping());
        }
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    @Test
    void disabledAdaptersAreSkipped() throws Exception {
        String token = AgentTestSupport.token();
        String args = "port=0,watch=false,debounceMs=150,token="
                + token
                + ",backend=standard,log=debug,disabledAdapters=noop,watchDir="
                + temp.toAbsolutePath();
        AgentMain.premain(args, AgentTestSupport.instrumentation());

        String name = TestClasses.uniqueBinary("Disabled");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        TestClasses.define(getClass().getClassLoader(), name, before);

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(inline(name, after));
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
            assertTrue(
                    result.adapters == null
                            || result.adapters.stream()
                                    .noneMatch(outcome -> NoOpFrameworkAdapter.ID.equals(outcome.adapterId)),
                    String.valueOf(result.adapters));
        }
    }

    @Test
    void disabledAdaptersOptionIsParsed() {
        AgentOptions opt = AgentOptions.parse("disabledAdapters=noop;spring", new Properties());
        assertEquals(List.of("noop", "spring"), opt.disabledAdapters);
    }

    private static ReloadRequest inline(String binaryName, byte[] bytes) {
        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.classes = List.of(new ClassPayload(binaryName, null, null, TestClasses.b64(bytes)));
        return request;
    }
}

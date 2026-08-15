package io.runtimerocket.agent;

import io.runtimerocket.protocol.ClassOutcome;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;
import io.runtimerocket.protocol.ReloadResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class AgentHotSwapTest {

    @TempDir
    Path temp;

    @AfterEach
    void tearDown() {
        AgentTestSupport.stop();
    }

    @Test
    void methodBodyReloadChangesLiveInstance() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String name = TestClasses.uniqueBinary("Body");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();
        assertEquals(1, TestClasses.invokeValue(type, instance));

        ReloadRequest request = inline(name, after);
        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(request);
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
            assertEquals(ClassOutcome.REDEFINED, result.classes.get(0).status);
            assertTrue(result.classes.get(0).changeKinds.contains("METHOD_BODY"));
        }
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    @Test
    void duplicateSha256WithinOneSecondIsSkipped() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String name = TestClasses.uniqueBinary("Dup");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();

        ReloadRequest request = inline(name, after);
        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            assertEquals(ReloadResult.SUCCESS, client.reload(request).status);
            ReloadResult second = client.reload(inline(name, after));
            assertEquals(ReloadResult.SUCCESS, second.status, second.message);
            assertEquals(ClassOutcome.SKIPPED, second.classes.get(0).status);
            assertEquals("duplicate", second.classes.get(0).reason);
        }
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    @Test
    void addMethodIsRestartRequiredAndNotApplied() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String name = TestClasses.uniqueBinary("Methods");
        byte[] before = TestClasses.optionalExtraMethod(name, false);
        byte[] after = TestClasses.optionalExtraMethod(name, true);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        assertFalse(TestClasses.hasMethod(type, "extra"));

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(inline(name, after));
            assertEquals(ReloadResult.RESTART_REQUIRED, result.status, result.message);
            assertTrue(result.message.contains("ADD_METHOD"), result.message);
            assertEquals(ClassOutcome.FAILED, result.classes.get(0).status);
        }
        assertFalse(TestClasses.hasMethod(type, "extra"));
    }

    @Test
    void mixedBatchAbortsBeforeAnyRedefine() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String bodyName = TestClasses.uniqueBinary("MixBody");
        String addName = TestClasses.uniqueBinary("MixAdd");
        byte[] bodyBefore = TestClasses.bodyClass(bodyName, 1);
        byte[] bodyAfter = TestClasses.bodyClass(bodyName, 2);
        byte[] addBefore = TestClasses.optionalExtraMethod(addName, false);
        byte[] addAfter = TestClasses.optionalExtraMethod(addName, true);

        Class<?> bodyType = TestClasses.define(getClass().getClassLoader(), bodyName, bodyBefore);
        TestClasses.define(getClass().getClassLoader(), addName, addBefore);
        Object instance = bodyType.getDeclaredConstructor().newInstance();
        assertEquals(1, TestClasses.invokeValue(bodyType, instance));

        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.classes = List.of(
                new ClassPayload(bodyName, null, null, TestClasses.b64(bodyAfter)),
                new ClassPayload(addName, null, null, TestClasses.b64(addAfter)));

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(request);
            assertEquals(ReloadResult.RESTART_REQUIRED, result.status, result.message);
        }
        assertEquals(1, TestClasses.invokeValue(bodyType, instance));
    }

    @Test
    void byReferenceOutsideWatchDirIsRejected() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String name = TestClasses.uniqueBinary("Jail");
        byte[] bytes = TestClasses.bodyClass(name, 1);
        Path outside = Files.createTempFile("rr-outside-", ".class");
        Files.write(outside, bytes);
        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadRequest request = new ReloadRequest();
            request.byReference = true;
            request.trigger = ReloadRequest.TRIGGER_MANUAL;
            request.classes = List.of(new ClassPayload(name, outside.toAbsolutePath().toString(), null, null));
            ReloadResult result = client.reload(request);
            assertEquals(ReloadResult.FAILED, result.status, result.message);
            assertEquals("path-not-watched", result.message);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void byReferenceInsideWatchDirReloads() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String name = TestClasses.uniqueBinary("Ref");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();

        Path classFile = temp.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, after);

        ReloadRequest request = new ReloadRequest();
        request.byReference = true;
        request.trigger = ReloadRequest.TRIGGER_COMPILE;
        request.classes = List.of(new ClassPayload(name, classFile.toAbsolutePath().toString(), null, null));

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(request);
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
        }
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    private static ReloadRequest inline(String binaryName, byte[] bytes) {
        ReloadRequest request = new ReloadRequest();
        request.byReference = false;
        request.trigger = ReloadRequest.TRIGGER_MANUAL;
        request.classes = List.of(new ClassPayload(binaryName, null, null, TestClasses.b64(bytes)));
        return request;
    }
}

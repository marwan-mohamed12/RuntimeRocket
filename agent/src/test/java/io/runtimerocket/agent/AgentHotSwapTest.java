package io.runtimerocket.agent;

import io.runtimerocket.agent.config.WatchDirs;
import io.runtimerocket.agent.reload.ChangeKind;
import io.runtimerocket.agent.reload.ClassDelta;
import io.runtimerocket.agent.reload.ClassDeltaClassifier;
import io.runtimerocket.agent.reload.ClassIndex;
import io.runtimerocket.agent.reload.EnhancedHotSwapBackend;
import io.runtimerocket.agent.reload.ReloadOrchestrator;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void enhancedEnumRejectReportsEnumConstantsNotAddField() {
        String name = TestClasses.uniqueBinary("Color");
        byte[] before = TestClasses.enumClass(name, "RED");
        byte[] after = TestClasses.enumClass(name, "RED", "BLUE");
        ClassDelta delta = new ClassDeltaClassifier().classify(before, after);
        assertTrue(delta.kinds.contains(ChangeKind.ENUM_CONSTANTS), delta.kinds.toString());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_FIELD), delta.kinds.toString());

        EnhancedHotSwapBackend backend = new EnhancedHotSwapBackend();
        ClassIndex index = new ClassIndex(AgentTestSupport.instrumentation());
        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        index.recordClass(type);
        index.storeBytes(type.getClassLoader(), name, before);

        ReloadOrchestrator orchestrator = new ReloadOrchestrator(
                AgentTestSupport.instrumentation(),
                backend,
                index,
                WatchDirs.of(List.of()),
                null,
                null);
        ReloadResult result = orchestrator.reload(inline(name, after));
        assertEquals(ReloadResult.RESTART_REQUIRED, result.status, result.message);
        assertTrue(result.message.contains("ENUM_CONSTANTS"), result.message);
        assertFalse(result.message.contains("ADD_FIELD"), result.message);
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
    void mixedAbortThenBodyOnlyRetryRedefines() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String bodyName = TestClasses.uniqueBinary("RetryBody");
        String addName = TestClasses.uniqueBinary("RetryAdd");
        byte[] bodyBefore = TestClasses.bodyClass(bodyName, 1);
        byte[] bodyAfter = TestClasses.bodyClass(bodyName, 2);
        byte[] addBefore = TestClasses.optionalExtraMethod(addName, false);
        byte[] addAfter = TestClasses.optionalExtraMethod(addName, true);

        Class<?> bodyType = TestClasses.define(getClass().getClassLoader(), bodyName, bodyBefore);
        TestClasses.define(getClass().getClassLoader(), addName, addBefore);
        Object instance = bodyType.getDeclaredConstructor().newInstance();

        ReloadRequest mixed = new ReloadRequest();
        mixed.byReference = false;
        mixed.trigger = ReloadRequest.TRIGGER_MANUAL;
        mixed.classes = List.of(
                new ClassPayload(bodyName, null, null, TestClasses.b64(bodyAfter)),
                new ClassPayload(addName, null, null, TestClasses.b64(addAfter)));

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult rejected = client.reload(mixed);
            assertEquals(ReloadResult.RESTART_REQUIRED, rejected.status, rejected.message);
            assertEquals(1, TestClasses.invokeValue(bodyType, instance));

            ReloadResult retried = client.reload(inline(bodyName, bodyAfter));
            assertEquals(ReloadResult.SUCCESS, retried.status, retried.message);
            assertEquals(ClassOutcome.REDEFINED, retried.classes.get(0).status);
        }
        assertEquals(2, TestClasses.invokeValue(bodyType, instance));
    }

    @Test
    void newTypeUsesIsolatedLoaderOfSamePackage() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        String pkg = TestClasses.uniquePackage();
        String existingName = pkg + ".Host";
        String newName = pkg + ".Sibling";
        TestClasses.IsolatedLoader isolated = new TestClasses.IsolatedLoader(getClass().getClassLoader());
        Class<?> existing = isolated.define(existingName, TestClasses.bodyClass(existingName, 1));

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(inline(newName, TestClasses.bodyClass(newName, 7)));
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
            assertEquals(ClassOutcome.DEFINED, result.classes.get(0).status);
        }
        Class<?> created = Class.forName(newName, false, isolated);
        assertSame(existing.getClassLoader(), created.getClassLoader());
        assertEquals(7, TestClasses.invokeValue(created, created.getDeclaredConstructor().newInstance()));
    }

    @Test
    void corruptClassBytesReturnFailed() throws Exception {
        String token = AgentTestSupport.token();
        AgentTestSupport.start(temp, token);

        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(inline("demo.rr.Corrupt", "not-a-class-file".getBytes()));
            assertEquals(ReloadResult.FAILED, result.status, result.message);
            assertTrue(result.message != null && !result.message.isBlank());
        }
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

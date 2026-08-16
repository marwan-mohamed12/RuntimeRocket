package io.runtimerocket.agent;

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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class ClassPathWatchReloadTest {

    @TempDir
    Path temp;

    @AfterEach
    void tearDown() {
        AgentTestSupport.stop();
    }

    @Test
    void watchReloadRedefinesLiveInstance() throws Exception {
        String name = TestClasses.uniqueBinary("WatchBody");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] after = TestClasses.bodyClass(name, 2);
        Path classFile = temp.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, before);

        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();
        assertEquals(1, TestClasses.invokeValue(type, instance));

        String token = AgentTestSupport.token();
        AgentTestSupport.startWatching(temp, token);

        Files.write(classFile, after);
        await(() -> {
            try {
                return TestClasses.invokeValue(type, instance) == 2;
            } catch (Exception e) {
                return false;
            }
        }, 3_000);
        assertEquals(2, TestClasses.invokeValue(type, instance));
    }

    @Test
    void compileTriggerSuppressesWatcherForOneSecond() throws Exception {
        String name = TestClasses.uniqueBinary("WatchSuppress");
        byte[] before = TestClasses.bodyClass(name, 1);
        byte[] compiled = TestClasses.bodyClass(name, 2);
        byte[] later = TestClasses.bodyClass(name, 3);
        Path classFile = temp.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, before);

        Class<?> type = TestClasses.define(getClass().getClassLoader(), name, before);
        Object instance = type.getDeclaredConstructor().newInstance();

        String token = AgentTestSupport.token();
        AgentTestSupport.startWatching(temp, token);

        Files.write(classFile, compiled);
        ReloadRequest compile = new ReloadRequest();
        compile.byReference = true;
        compile.trigger = ReloadRequest.TRIGGER_COMPILE;
        compile.classes = List.of(new ClassPayload(name, classFile.toAbsolutePath().toString(), null, null));
        try (AgentClient client = new AgentClient(AgentRuntime.get().port())) {
            client.handshake(token);
            ReloadResult result = client.reload(compile);
            assertEquals(ReloadResult.SUCCESS, result.status, result.message);
        }
        assertEquals(2, TestClasses.invokeValue(type, instance));

        Files.write(classFile, later);
        AgentRuntime.get().watcher().notifyChanged(classFile);
        Thread.sleep(300);
        assertEquals(2, TestClasses.invokeValue(type, instance));
        assertTrue(AgentRuntime.get().watcher().isSuppressed(classFile));

        await(() -> !AgentRuntime.get().watcher().isSuppressed(classFile), 1_500);
        byte[] afterWindow = TestClasses.bodyClass(name, 4);
        Files.write(classFile, afterWindow);
        AgentRuntime.get().watcher().notifyChanged(classFile);
        await(() -> {
            try {
                return TestClasses.invokeValue(type, instance) == 4;
            } catch (Exception e) {
                return false;
            }
        }, 3_000);
        assertEquals(4, TestClasses.invokeValue(type, instance));
    }

    private static void await(BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out after " + timeoutMs + "ms");
    }
}

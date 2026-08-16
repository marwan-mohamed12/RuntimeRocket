package io.runtimerocket.agent.watch;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.config.PackageFilter;
import io.runtimerocket.protocol.ClassPayload;
import io.runtimerocket.protocol.ReloadRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassPathWatcherTest {

    @TempDir
    Path temp;

    private ClassPathWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.close();
        }
    }

    @Test
    void twoModifyEventsCollapseToOneReload() throws Exception {
        Path classFile = writeClass("demo.rr.Debounce", 1);
        List<ReloadRequest> seen = startWatcher(temp, PackageFilter.allowAll());

        Files.write(classFile, bodyClass("demo.rr.Debounce", 2));
        watcher.notifyChanged(classFile);
        Thread.sleep(20);
        Files.write(classFile, bodyClass("demo.rr.Debounce", 3));
        watcher.notifyChanged(classFile);

        await(() -> seen.size() == 1, 2_000);
        assertEquals(1, seen.size(), seen.toString());
        assertEquals(ReloadRequest.TRIGGER_WATCH, seen.get(0).trigger);
        assertEquals("demo.rr.Debounce", seen.get(0).classes.get(0).binaryName);
        assertEquals(1, seen.get(0).classes.size());
    }

    @Test
    void identicalRewriteDoesNotReload() throws Exception {
        Path classFile = writeClass("demo.rr.Hash", 1);
        List<ReloadRequest> seen = startWatcher(temp, PackageFilter.allowAll());

        byte[] same = Files.readAllBytes(classFile);
        Files.write(classFile, same);
        watcher.notifyChanged(classFile);
        Thread.sleep(300);
        assertEquals(0, seen.size(), seen.toString());

        Files.write(classFile, bodyClass("demo.rr.Hash", 2));
        watcher.notifyChanged(classFile);
        await(() -> !seen.isEmpty(), 2_000);
        assertEquals(1, seen.size());
        Files.write(classFile, bodyClass("demo.rr.Hash", 2));
        watcher.notifyChanged(classFile);
        Thread.sleep(300);
        assertEquals(1, seen.size(), seen.toString());
    }

    @Test
    void suppressWindowDropsEventsForThatPath() throws Exception {
        Path classFile = writeClass("demo.rr.Suppress", 1);
        List<ReloadRequest> seen = startWatcher(temp, PackageFilter.allowAll());

        watcher.suppress(classFile, 1_000);
        assertTrue(watcher.isSuppressed(classFile));
        Files.write(classFile, bodyClass("demo.rr.Suppress", 2));
        watcher.notifyChanged(classFile);
        Thread.sleep(300);
        assertEquals(0, seen.size(), seen.toString());
    }

    @Test
    void packageExcludeSkipsClass() throws Exception {
        Path excluded = writeClass("demo.rr.generated.Skip", 1);
        Path included = writeClass("demo.rr.Keep", 1);
        PackageFilter filter = PackageFilter.of(List.of("demo.rr.**"), List.of("demo.rr.generated.**"));
        List<ReloadRequest> seen = startWatcher(temp, filter);

        Files.write(excluded, bodyClass("demo.rr.generated.Skip", 2));
        watcher.notifyChanged(excluded);
        Files.write(included, bodyClass("demo.rr.Keep", 2));
        watcher.notifyChanged(included);
        await(() -> !seen.isEmpty(), 2_000);
        Thread.sleep(200);
        assertEquals(1, seen.size(), seen.toString());
        assertEquals("demo.rr.Keep", seen.get(0).classes.get(0).binaryName);
    }

    @Test
    void ignoredNamesNeverReload() throws Exception {
        List<ReloadRequest> seen = startWatcher(temp, PackageFilter.allowAll());
        Path hidden = temp.resolve(".hidden.class");
        Path tmp = temp.resolve("Foo.tmp");
        Path jb = temp.resolve("Foo.class.__jb_old__");
        Path swp = temp.resolve("Foo.swp");
        Files.write(hidden, bodyClass("demo.rr.Hidden", 1));
        Files.write(tmp, new byte[] {1});
        Files.write(jb, bodyClass("demo.rr.Jb", 1));
        Files.write(swp, new byte[] {2});
        watcher.notifyChanged(hidden);
        watcher.notifyChanged(tmp);
        watcher.notifyChanged(jb);
        watcher.notifyChanged(swp);
        Thread.sleep(300);
        assertEquals(0, seen.size(), seen.toString());
    }

    @Test
    void watchFalseIsNoOp() throws Exception {
        AgentOptions options = AgentOptions.parse("watch=false,debounceMs=20", new Properties());
        watcher = new ClassPathWatcher(
                options, new AgentLog("error", null), List.of(temp), List.of(), PackageFilter.allowAll(), 10, 0);
        List<ReloadRequest> seen = new CopyOnWriteArrayList<>();
        watcher.setHandler(seen::add);
        watcher.start();
        assertFalse(watcher.isRunning());
        Path classFile = writeClass("demo.rr.Off", 1);
        Files.write(classFile, bodyClass("demo.rr.Off", 2));
        watcher.notifyChanged(classFile);
        Thread.sleep(200);
        assertEquals(0, seen.size());
    }

    @Test
    void watchServicePicksUpClassFileWrite() throws Exception {
        Path classFile = temp.resolve("demo/rr/Fs.class");
        Files.createDirectories(classFile.getParent());
        List<ReloadRequest> seen = startWatcher(temp, PackageFilter.allowAll());
        Files.write(classFile, bodyClass("demo.rr.Fs", 1));
        await(() -> seen.stream().anyMatch(r -> contains(r, "demo.rr.Fs")), 3_000);
        assertTrue(seen.stream().anyMatch(r -> contains(r, "demo.rr.Fs")), seen.toString());
        assertEquals(ReloadRequest.TRIGGER_WATCH, seen.get(0).trigger);
        assertTrue(seen.get(0).byReference);
    }

    @Test
    void resourceChangeIsNotifyOnly() throws Exception {
        Path resDir = Files.createDirectories(temp.resolve("res"));
        AgentOptions options = AgentOptions.parse("watch=true,debounceMs=20", new Properties());
        watcher = new ClassPathWatcher(
                options,
                new AgentLog("error", null),
                List.of(),
                List.of(resDir),
                PackageFilter.allowAll(),
                10,
                0);
        List<ReloadRequest> seen = new CopyOnWriteArrayList<>();
        watcher.setHandler(seen::add);
        watcher.start();
        Path props = resDir.resolve("application.properties");
        Files.writeString(props, "a=1");
        watcher.notifyChanged(props);
        await(() -> !seen.isEmpty(), 2_000);
        assertTrue(seen.get(0).classes == null || seen.get(0).classes.isEmpty());
        assertEquals(1, seen.get(0).resources.size());
        assertEquals("application.properties", seen.get(0).resources.get(0).classpathName);
    }

    private List<ReloadRequest> startWatcher(Path dir, PackageFilter filter) {
        AgentOptions options = AgentOptions.parse("watch=true,debounceMs=40", new Properties());
        watcher = new ClassPathWatcher(
                options, new AgentLog("error", null), List.of(dir), List.of(), filter, 20, 0);
        List<ReloadRequest> seen = new CopyOnWriteArrayList<>();
        watcher.setHandler(seen::add);
        watcher.start();
        return seen;
    }

    private Path writeClass(String binaryName, int value) throws Exception {
        Path file = temp.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bodyClass(binaryName, value));
        return file;
    }

    private static boolean contains(ReloadRequest request, String binaryName) {
        if (request.classes == null) {
            return false;
        }
        for (ClassPayload payload : request.classes) {
            if (binaryName.equals(payload.binaryName)) {
                return true;
            }
        }
        return false;
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

    private static byte[] bodyClass(String binaryName, int value) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.SIPUSH, value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}

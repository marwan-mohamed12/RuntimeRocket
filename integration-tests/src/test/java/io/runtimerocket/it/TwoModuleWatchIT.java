package io.runtimerocket.it;

import demo.twomodule.app.App;
import demo.twomodule.lib.LibGreeter;
import io.runtimerocket.agent.AgentMain;
import io.runtimerocket.agent.AgentRuntime;
import io.runtimerocket.agent.config.RocketXmlDiscovery;
import io.runtimerocket.agent.config.RocketXmlDocuments;
import io.runtimerocket.agent.config.RocketXmlParser;

import net.bytebuddy.agent.ByteBuddyAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code :fixtures:two-module} emits two {@code runtimerocket.xml} files. The agent unions both;
 * a lib class change reloads while the app instance is live.
 */
@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class TwoModuleWatchIT {

    @AfterEach
    void tearDown() {
        AgentRuntime.get().stop();
    }

    @Test
    void fixtureEmitsTwoXmlFilesAndAgentUnionsThem() {
        Path libOut = fixtureDir("rr.fixture.lib.classes");
        Path appOut = fixtureDir("rr.fixture.app.classes");
        Path libXml = libOut.resolve(RocketXmlParser.FILE_NAME);
        Path appXml = appOut.resolve(RocketXmlParser.FILE_NAME);
        assertTrue(Files.isRegularFile(libXml), libXml.toString());
        assertTrue(Files.isRegularFile(appXml), appXml.toString());

        RocketXmlDocuments docs = RocketXmlDiscovery.fromFiles(List.of(libXml, appXml));
        assertEquals(2, docs.documents().size());
        assertTrue(docs.classpathDirs().stream().anyMatch(p -> sameDir(p, libOut)), docs.classpathDirs().toString());
        assertTrue(docs.classpathDirs().stream().anyMatch(p -> sameDir(p, appOut)), docs.classpathDirs().toString());
        assertTrue(docs.packageFilter().accepts(LibGreeter.class.getName()));
        assertTrue(docs.packageFilter().accepts(App.class.getName()));
    }

    @Test
    void libClassChangeReloadsWhileAppIsRunning() throws Exception {
        Path libOut = fixtureDir("rr.fixture.lib.classes");
        Path appOut = fixtureDir("rr.fixture.app.classes");
        Path libXml = libOut.resolve(RocketXmlParser.FILE_NAME);
        Path classFile = libOut.resolve("demo/twomodule/lib/LibGreeter.class");
        assertTrue(Files.isRegularFile(classFile), classFile.toString());

        App app = new App();
        assertEquals(1, app.libVersion());

        byte[] original = Files.readAllBytes(classFile);
        String args = "port=0,watch=true,debounceMs=40,backend=standard,log=debug,config="
                + libXml.toAbsolutePath()
                + ",watchDir="
                + appOut.toAbsolutePath();
        AgentMain.premain(args, ByteBuddyAgent.install());
        RocketXmlDocuments live = AgentRuntime.get().rocketXml();
        assertNotNull(live);
        assertTrue(live.documents().size() >= 2, live.documents().toString());
        assertTrue(live.classpathDirs().stream().anyMatch(p -> sameDir(p, libOut)), live.classpathDirs().toString());
        assertTrue(live.classpathDirs().stream().anyMatch(p -> sameDir(p, appOut)), live.classpathDirs().toString());
        assertTrue(live.packageFilter().accepts(LibGreeter.class.getName()));
        assertTrue(live.packageFilter().accepts(App.class.getName()));

        try {
            Files.write(classFile, rewriteVersion(original, 2));
            await(() -> app.libVersion() == 2, 4_000);
            assertEquals(2, app.libVersion());
        } finally {
            Files.write(classFile, original);
        }
    }

    private static Path fixtureDir(String property) {
        String raw = System.getProperty(property);
        assertNotNull(raw, property);
        Path dir = Path.of(raw);
        assertTrue(Files.isDirectory(dir), dir.toString());
        return dir;
    }

    private static boolean sameDir(Path a, Path b) {
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (Exception e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static void await(BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (cond.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out after " + timeoutMs + "ms", last);
    }

    private static byte[] rewriteVersion(byte[] original, int version) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(
                new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if ("version".equals(name) && "()I".equals(descriptor)) {
                            mv.visitCode();
                            mv.visitIntInsn(Opcodes.SIPUSH, version);
                            mv.visitInsn(Opcodes.IRETURN);
                            mv.visitMaxs(1, 1);
                            mv.visitEnd();
                            return null;
                        }
                        return mv;
                    }
                },
                0);
        return writer.toByteArray();
    }
}

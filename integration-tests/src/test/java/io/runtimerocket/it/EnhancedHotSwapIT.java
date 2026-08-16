package io.runtimerocket.it;

import io.runtimerocket.agent.reload.ChangeKind;
import io.runtimerocket.agent.reload.ClassDelta;
import io.runtimerocket.agent.reload.ClassDeltaClassifier;
import io.runtimerocket.agent.reload.EnhancedHotSwapBackend;
import io.runtimerocket.agent.reload.Redefinition;
import io.runtimerocket.agent.reload.Support;

import net.bytebuddy.agent.ByteBuddyAgent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enhanced HotSwap ITs. Skipped unless this JVM can trial-redefine an added method.
 *
 * Required VM flags: run on JetBrains Runtime 17/21 (or another DCEVM-derived JDK) with
 * {@code -XX:+AllowEnhancedClassRedefinition}. {@code -XX:+IgnoreUnrecognizedVMOptions} lets the
 * same Gradle jvmArgs run on stock HotSpot, where these tests skip instead of failing. Also pass
 * {@code --add-opens=java.base/java.lang=ALL-UNNAMED} and {@code -Djdk.attach.allowAttachSelf=true}.
 */
class EnhancedHotSwapIT {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private static Instrumentation inst;
    private static EnhancedHotSwapBackend backend;

    @BeforeAll
    static void installAgent() {
        inst = ByteBuddyAgent.install();
        backend = new EnhancedHotSwapBackend();
        boolean probed = backend.probe(inst);
        if (EnhancedHotSwapBackend.jetbrainsVmHint() && EnhancedHotSwapBackend.enhancedFlagHint()) {
            assertTrue(probed, "JBR with -XX:+AllowEnhancedClassRedefinition must trial-redefine");
        } else {
            Assumptions.assumeTrue(probed, "enhanced redefine is not available on this JVM");
        }
    }

    @Test
    void addMethodRedefinesLiveInstance() throws Exception {
        String name = unique("AddMethod");
        byte[] before = methodClass(name, false);
        byte[] after = methodClass(name, true);
        Class<?> type = IsolatedLoader.define(name, before);
        Object instance = type.getDeclaredConstructor().newInstance();
        assertEquals(1, invokeInt(type, instance, "value"));

        ClassDelta delta = new ClassDeltaClassifier().classify(before, after);
        assertTrue(delta.kinds.contains(ChangeKind.ADD_METHOD), delta.kinds.toString());
        assertEquals(Support.FULL, backend.assess(delta));
        backend.apply(inst, List.of(new Redefinition(type, after, delta)));

        assertEquals(1, invokeInt(type, instance, "value"));
        assertEquals("added", type.getMethod("extra").invoke(instance));
    }

    @Test
    void addFieldRedefinesLiveInstance() throws Exception {
        String name = unique("AddField");
        byte[] before = fieldClass(name, false);
        byte[] after = fieldClass(name, true);
        Class<?> type = IsolatedLoader.define(name, before);
        Object instance = type.getDeclaredConstructor().newInstance();
        assertEquals(7, invokeInt(type, instance, "value"));

        ClassDelta delta = new ClassDeltaClassifier().classify(before, after);
        assertTrue(delta.kinds.contains(ChangeKind.ADD_FIELD), delta.kinds.toString());
        assertEquals(Support.FULL, backend.assess(delta));
        backend.apply(inst, List.of(new Redefinition(type, after, delta)));

        assertEquals(7, invokeInt(type, instance, "value"));
        Field extra = type.getDeclaredField("extra");
        extra.setAccessible(true);
        assertNotNull(extra);
        assertNull(extra.get(instance));
    }

    private static String unique(String prefix) {
        return "demo.rr.it." + prefix + NEXT.getAndIncrement();
    }

    private static int invokeInt(Class<?> type, Object instance, String method) throws Exception {
        return (Integer) type.getMethod(method).invoke(instance);
    }

    private static byte[] methodClass(String binaryName, boolean extra) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        ctor(writer);
        MethodVisitor value = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        value.visitCode();
        value.visitInsn(Opcodes.ICONST_1);
        value.visitInsn(Opcodes.IRETURN);
        value.visitMaxs(1, 1);
        value.visitEnd();
        if (extra) {
            MethodVisitor added = writer.visitMethod(Opcodes.ACC_PUBLIC, "extra", "()Ljava/lang/String;", null, null);
            added.visitCode();
            added.visitLdcInsn("added");
            added.visitInsn(Opcodes.ARETURN);
            added.visitMaxs(1, 1);
            added.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] fieldClass(String binaryName, boolean extra) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        ctor(writer);
        writer.visitField(Opcodes.ACC_PRIVATE, "kept", "I", null, null).visitEnd();
        if (extra) {
            writer.visitField(Opcodes.ACC_PRIVATE, "extra", "Ljava/lang/String;", null, null).visitEnd();
        }
        MethodVisitor value = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        value.visitCode();
        value.visitIntInsn(Opcodes.BIPUSH, 7);
        value.visitInsn(Opcodes.IRETURN);
        value.visitMaxs(1, 1);
        value.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void ctor(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader() {
            super(EnhancedHotSwapIT.class.getClassLoader());
        }

        static Class<?> define(String name, byte[] bytes) {
            return new IsolatedLoader().defineClass(name, bytes, 0, bytes.length);
        }
    }
}

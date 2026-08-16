package io.runtimerocket.agent.reload;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassInjectorTest {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Test
    void helperDefinedInAppLoaderCanSeeTypeFromThatLoader() throws Exception {
        IsolatedLoader appLoader = new IsolatedLoader(ClassInjector.class.getClassLoader());
        String pkg = "demo.rr.inj" + NEXT.getAndIncrement();
        String hostName = pkg + ".Host";
        String helperName = pkg + ".SeeHost";
        Class<?> host = appLoader.define(hostName, emptyClass(hostName));

        Class<?> helper = ClassInjector.into(appLoader).inject(helperName, helperSeeing(helperName, hostName));

        assertSame(appLoader, helper.getClassLoader());
        assertNotSame(ClassInjector.class.getClassLoader(), helper.getClassLoader());
        Class<?> seen = (Class<?>) helper.getMethod("hostType").invoke(null);
        assertSame(host, seen);
        assertEquals(hostName, seen.getName());
    }

    @Test
    void frameworkHelperLandsInAppLoaderNotAgentLoader() throws Exception {
        IsolatedLoader appLoader = new IsolatedLoader(ClassInjector.class.getClassLoader());
        String hostName = "demo.rr.inj" + NEXT.getAndIncrement() + ".AppType";
        String helperName = "io.runtimerocket.frameworks.test.SeeApp" + NEXT.getAndIncrement();
        Class<?> host = appLoader.define(hostName, emptyClass(hostName));

        Class<?> helper = ClassInjector.into(appLoader).inject(helperName, helperSeeing(helperName, hostName));

        assertSame(appLoader, helper.getClassLoader());
        assertNotSame(ClassInjector.class.getClassLoader(), helper.getClassLoader());
        assertSame(host, helper.getMethod("hostType").invoke(null));
        assertTrue(ClassInjector.skip(helperName));
    }

    @Test
    void injectorRefusesAgentTypes() {
        IsolatedLoader appLoader = new IsolatedLoader(ClassInjector.class.getClassLoader());
        String name = "io.runtimerocket.agent.reload.ShouldNotInject";
        assertTrue(ClassInjector.skip(name));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClassInjector.into(appLoader).inject(name, emptyClass(name)));
    }

    private static byte[] emptyClass(String binaryName) {
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
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] helperSeeing(String helperName, String hostName) {
        String helperInternal = helperName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, helperInternal, null, "java/lang/Object", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor seen = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hostType", "()Ljava/lang/Class;", null, null);
        seen.visitCode();
        seen.visitLdcInsn(Type.getObjectType(hostName.replace('.', '/')));
        seen.visitInsn(Opcodes.ARETURN);
        seen.visitMaxs(1, 0);
        seen.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}

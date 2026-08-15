package io.runtimerocket.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/** ASM-generated loadable classes for in-process HotSwap tests. */
final class TestClasses {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private TestClasses() {}

    static String uniqueBinary(String prefix) {
        return "demo.rr." + prefix + NEXT.getAndIncrement();
    }

    static byte[] bodyClass(String binaryName, int value) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        ctor(writer);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        mv.visitCode();
        pushInt(mv, value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    static byte[] optionalExtraMethod(String binaryName, boolean extra) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        ctor(writer);
        MethodVisitor existing = writer.visitMethod(Opcodes.ACC_PUBLIC, "existing", "()V", null, null);
        existing.visitCode();
        existing.visitInsn(Opcodes.RETURN);
        existing.visitMaxs(0, 1);
        existing.visitEnd();
        if (extra) {
            MethodVisitor added = writer.visitMethod(Opcodes.ACC_PUBLIC, "extra", "()V", null, null);
            added.visitCode();
            added.visitInsn(Opcodes.RETURN);
            added.visitMaxs(0, 1);
            added.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static Class<?> define(ClassLoader parent, String binaryName, byte[] bytes) {
        return new IsolatedLoader(parent).define(binaryName, bytes);
    }

    static int invokeValue(Class<?> type, Object instance) throws Exception {
        return (Integer) type.getMethod("value").invoke(instance);
    }

    static boolean hasMethod(Class<?> type, String name) {
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
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

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        }
    }

    static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}

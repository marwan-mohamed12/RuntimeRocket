package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.AdapterContext;

import net.bytebuddy.agent.ByteBuddyAgent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

final class SpringTestSupport {

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final Object INSTALL_LOCK = new Object();
    private static Instrumentation instrumentation;

    private SpringTestSupport() {}

    static Instrumentation instrumentation() {
        synchronized (INSTALL_LOCK) {
            if (instrumentation == null) {
                instrumentation = ByteBuddyAgent.install();
            }
            return instrumentation;
        }
    }

    static String unique(String prefix) {
        return "demo.rr.spring." + prefix + NEXT.getAndIncrement();
    }

    static Class<?> defineService(ClassLoader loader, String binaryName) {
        return define(loader, binaryName, serviceBytes(binaryName));
    }

    static byte[] serviceBytes(String binaryName) {
        String internal = binaryName.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internal, null, "java/lang/Object", null);
        AnnotationVisitor ann = writer.visitAnnotation("Lorg/springframework/stereotype/Service;", true);
        ann.visitEnd();
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor ping = writer.visitMethod(Opcodes.ACC_PUBLIC, "ping", "()Ljava/lang/String;", null, null);
        ping.visitCode();
        ping.visitLdcInsn("ok");
        ping.visitInsn(Opcodes.ARETURN);
        ping.visitMaxs(1, 1);
        ping.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    static Class<?> define(ClassLoader loader, String binaryName, byte[] bytes) {
        try {
            Method define =
                    ClassLoader.class.getDeclaredMethod(
                            "defineClass", String.class, byte[].class, int.class, int.class);
            define.setAccessible(true);
            return (Class<?>) define.invoke(loader, binaryName, bytes, 0, bytes.length);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static AdapterContext context(boolean late) {
        return new InstContext(late, instrumentation());
    }

    static final class InstContext implements AdapterContext {
        private final boolean late;
        private final Instrumentation inst;

        InstContext(boolean late, Instrumentation inst) {
            this.late = late;
            this.inst = inst;
        }

        @Override
        public ClassLoader[] applicationLoaders() {
            return new ClassLoader[] {SpringTestSupport.class.getClassLoader()};
        }

        @Override
        public boolean isLateAttach() {
            return late;
        }

        @Override
        public void log(String level, String msg) {}

        @Override
        public <T> T peekService(Class<T> type) {
            if (type == Instrumentation.class) {
                return type.cast(inst);
            }
            return null;
        }
    }
}

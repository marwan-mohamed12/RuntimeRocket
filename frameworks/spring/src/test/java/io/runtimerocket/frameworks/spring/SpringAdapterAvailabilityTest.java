package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.AdapterContext;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAdapterAvailabilityTest {

    @Test
    void availableWhenApplicationContextIsLoadable() {
        SpringAdapter adapter = new SpringAdapter();
        assertEquals(SpringAdapter.ID, adapter.id());
        assertEquals(100, adapter.order());
        assertTrue(adapter.isAvailable(SpringAdapterAvailabilityTest.class.getClassLoader()));
    }

    @Test
    void devToolsRefusalLogsFromOnAgentStart() {
        IsolatedLoader loader = new IsolatedLoader(SpringAdapterAvailabilityTest.class.getClassLoader());
        loader.define(
                "org.springframework.boot.devtools.restart.Restarter",
                emptyClass("org.springframework.boot.devtools.restart.Restarter"));
        RecordingContext ctx = new RecordingContext(loader);
        SpringAdapter adapter = new SpringAdapter();
        assertTrue(adapter.isAvailable(loader));
        adapter.onAgentStart(ctx);
        assertTrue(
                ctx.errors.stream().anyMatch(msg -> msg.contains("DevTools") || msg.contains("devtools")),
                ctx.errors.toString());
        assertFalse(adapter.isAvailable(loader));
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

    private static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final class RecordingContext implements AdapterContext {
        private final ClassLoader loader;
        private final List<String> errors = new ArrayList<>();

        RecordingContext(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public ClassLoader[] applicationLoaders() {
            return new ClassLoader[] {loader};
        }

        @Override
        public boolean isLateAttach() {
            return false;
        }

        @Override
        public void log(String level, String msg) {
            if (level != null && "error".equalsIgnoreCase(level) && msg != null) {
                errors.add(msg);
            }
        }

        @Override
        public <T> T peekService(Class<T> type) {
            if (type == Instrumentation.class) {
                return type.cast(SpringTestSupport.instrumentation());
            }
            return null;
        }
    }
}

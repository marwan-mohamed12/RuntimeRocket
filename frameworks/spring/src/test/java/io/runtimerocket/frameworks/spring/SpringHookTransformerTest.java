package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringHookTransformerTest {

    @Test
    void insertsTrackerCallsOnContextMethods() {
        SpringHookTransformer transformer = new SpringHookTransformer();
        byte[] original = contextBytes();
        byte[] ctx = transformer.transform(
                null,
                SpringHookTransformer.ABSTRACT_APP_CTX,
                null,
                null,
                original);
        assertTrue(invokes(ctx, "register", "finishRefresh"));
        assertTrue(invokes(ctx, "register", "getBean"));
        assertTrue(invokes(ctx, "register", "isActive"));
        assertNull(transformer.transform(null, SpringHookTransformer.ABSTRACT_APP_CTX, null, null, ctx));
    }

    @Test
    void skipsRuntimeRocketPackages() {
        SpringHookTransformer transformer = new SpringHookTransformer();
        byte[] payload = contextBytes();
        assertNull(transformer.transform(null, "io/runtimerocket/frameworks/spring/SpringAdapter", null, null, payload));
        assertNull(
                transformer.transform(
                        null,
                        "io/runtimerocket/frameworks/spring/internal/SpringRefreshHelper",
                        null,
                        null,
                        payload));
    }

    private static boolean invokes(byte[] bytes, String trackerMethod, String ownerMethod) {
        Set<String> hits = new HashSet<>();
        new ClassReader(bytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access, String name, String descriptor, String signature, String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String methodName,
                                            String methodDesc,
                                            boolean isInterface) {
                                        if (SpringHookTransformer.TRACKER.equals(owner)
                                                && trackerMethod.equals(methodName)
                                                && ownerMethod.equals(name)) {
                                            hits.add(name);
                                        }
                                    }
                                };
                            }
                        },
                        0);
        return !hits.isEmpty();
    }

    private static byte[] contextBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                SpringHookTransformer.ABSTRACT_APP_CTX,
                null,
                "java/lang/Object",
                null);
        emptyCtor(writer);
        voidMethod(writer, "finishRefresh");
        MethodVisitor getBean =
                writer.visitMethod(Opcodes.ACC_PUBLIC, "getBean", "(Ljava/lang/String;)Ljava/lang/Object;", null, null);
        getBean.visitCode();
        getBean.visitInsn(Opcodes.ACONST_NULL);
        getBean.visitInsn(Opcodes.ARETURN);
        getBean.visitMaxs(1, 2);
        getBean.visitEnd();
        MethodVisitor isActive = writer.visitMethod(Opcodes.ACC_PUBLIC, "isActive", "()Z", null, null);
        isActive.visitCode();
        isActive.visitInsn(Opcodes.ICONST_1);
        isActive.visitInsn(Opcodes.IRETURN);
        isActive.visitMaxs(1, 1);
        isActive.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emptyCtor(ClassWriter writer) {
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
    }

    private static void voidMethod(ClassWriter writer, String name) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
    }
}

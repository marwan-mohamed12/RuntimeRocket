package io.runtimerocket.frameworks.spring;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * DEFINE+RETRANSFORM hooks on Spring context lifecycle methods. Never touches
 * {@code io.runtimerocket.**}.
 */
public final class SpringHookTransformer implements ClassFileTransformer {

    static final String ABSTRACT_APP_CTX = "org/springframework/context/support/AbstractApplicationContext";
    static final String DLBF = "org/springframework/beans/factory/support/DefaultListableBeanFactory";
    static final String TRACKER = "io/runtimerocket/frameworks/spring/SpringContextTracker";

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || className.startsWith("io/runtimerocket/")) {
            return null;
        }
        if (ABSTRACT_APP_CTX.equals(className)) {
            return hook(classfileBuffer, true);
        }
        if (DLBF.equals(className)) {
            return hook(classfileBuffer, false);
        }
        return null;
    }

    private static byte[] hook(byte[] classfileBuffer, boolean applicationContext) {
        ClassReader reader = new ClassReader(classfileBuffer);
        if (alreadyHooked(reader)) {
            return null;
        }
        ClassWriter writer =
                new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                    @Override
                    protected String getCommonSuperClass(String type1, String type2) {
                        if (type1.equals(type2)) {
                            return type1;
                        }
                        if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                            return "java/lang/Object";
                        }
                        try {
                            return super.getCommonSuperClass(type1, type2);
                        } catch (RuntimeException e) {
                            return "java/lang/Object";
                        }
                    }
                };
        reader.accept(new HookVisitor(writer, applicationContext), ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    static boolean alreadyHooked(ClassReader reader) {
        boolean[] found = {false};
        reader.accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(
                                    int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESTATIC
                                        && TRACKER.equals(owner)
                                        && ("register".equals(methodName) || "snapshotBeanNames".equals(methodName))) {
                                    found[0] = true;
                                }
                            }
                        };
                    }
                },
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private static final class HookVisitor extends ClassVisitor {
        private final boolean applicationContext;

        HookVisitor(ClassVisitor parent, boolean applicationContext) {
            super(Opcodes.ASM9, parent);
            this.applicationContext = applicationContext;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) {
                return null;
            }
            if (applicationContext) {
                if ("finishRefresh".equals(name) && "()V".equals(descriptor)) {
                    return new InsertBeforeReturn(mv, "register");
                }
                if ("getBean".equals(name) && "(Ljava/lang/String;)Ljava/lang/Object;".equals(descriptor)) {
                    return new InsertAtStart(mv, "register");
                }
                if ("isActive".equals(name) && "()Z".equals(descriptor)) {
                    return new InsertAtStart(mv, "register");
                }
            } else if ("preInstantiateSingletons".equals(name) && "()V".equals(descriptor)) {
                return new InsertAtStart(mv, "snapshotBeanNames");
            }
            return mv;
        }
    }

    private static final class InsertAtStart extends MethodVisitor {
        private final String trackerMethod;
        private boolean inserted;

        InsertAtStart(MethodVisitor parent, String trackerMethod) {
            super(Opcodes.ASM9, parent);
            this.trackerMethod = trackerMethod;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (!inserted) {
                inserted = true;
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACKER, trackerMethod, "(Ljava/lang/Object;)V", false);
            }
        }
    }

    private static final class InsertBeforeReturn extends MethodVisitor {
        private final String trackerMethod;

        InsertBeforeReturn(MethodVisitor parent, String trackerMethod) {
            super(Opcodes.ASM9, parent);
            this.trackerMethod = trackerMethod;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACKER, trackerMethod, "(Ljava/lang/Object;)V", false);
            }
            super.visitInsn(opcode);
        }
    }
}

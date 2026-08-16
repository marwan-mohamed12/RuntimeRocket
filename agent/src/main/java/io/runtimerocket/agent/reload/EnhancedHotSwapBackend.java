package io.runtimerocket.agent.reload;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * JBR/DCEVM enhanced redefine. Fail-closed on hierarchy, enum constants, and field type changes.
 */
public final class EnhancedHotSwapBackend implements ReloadBackend {

    public static final String ID = "enhanced";

    static final String PROBE_CLASS = "io.runtimerocket.agent.reload.ProbeTarget";

    private static final EnumSet<ChangeKind> SUPPORTED = EnumSet.of(
            ChangeKind.METHOD_BODY,
            ChangeKind.CONSTANT_POOL_ONLY,
            ChangeKind.NEW_TYPE,
            ChangeKind.ADD_METHOD,
            ChangeKind.REMOVE_METHOD,
            ChangeKind.CHANGE_METHOD_DESC,
            ChangeKind.CHANGE_METHOD_MODIFIERS,
            ChangeKind.ADD_FIELD,
            ChangeKind.REMOVE_FIELD,
            ChangeKind.ADD_CONSTRUCTOR,
            ChangeKind.REMOVE_CONSTRUCTOR,
            ChangeKind.CLASS_MODIFIERS,
            ChangeKind.INNER_CLASSES_MAP);

    private static final EnumSet<ChangeKind> FAIL_CLOSED = EnumSet.of(
            ChangeKind.HIERARCHY_SUPER,
            ChangeKind.HIERARCHY_IFACES,
            ChangeKind.ENUM_CONSTANTS,
            ChangeKind.RECORD_COMPONENTS,
            ChangeKind.PERMITTED_SUBCLASSES,
            ChangeKind.CHANGE_FIELD_DESC);

    public static final List<String> CAPABILITIES = List.of(
            ChangeKind.METHOD_BODY.name(),
            ChangeKind.CONSTANT_POOL_ONLY.name(),
            ChangeKind.NEW_TYPE.name(),
            ChangeKind.ADD_METHOD.name(),
            ChangeKind.REMOVE_METHOD.name(),
            ChangeKind.CHANGE_METHOD_DESC.name(),
            ChangeKind.CHANGE_METHOD_MODIFIERS.name(),
            ChangeKind.ADD_FIELD.name(),
            ChangeKind.REMOVE_FIELD.name(),
            ChangeKind.ADD_CONSTRUCTOR.name(),
            ChangeKind.REMOVE_CONSTRUCTOR.name(),
            ChangeKind.CLASS_MODIFIERS.name(),
            ChangeKind.INNER_CLASSES_MAP.name());

    private final Object probeLock = new Object();
    private Boolean probeResult;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> capabilityNames() {
        return CAPABILITIES;
    }

    @Override
    public boolean probe(Instrumentation inst) {
        if (inst == null) {
            return false;
        }
        synchronized (probeLock) {
            if (probeResult != null) {
                return probeResult;
            }
            jetbrainsVmHint();
            enhancedFlagHint();
            if (!inst.isRedefineClassesSupported()) {
                probeResult = false;
                return false;
            }
            probeResult = trialAddMethod(inst);
            return probeResult;
        }
    }

    static boolean jetbrainsVmHint() {
        return containsJetBrains(System.getProperty("java.vm.name"))
                || containsJetBrains(System.getProperty("java.vm.vendor"));
    }

    static boolean enhancedFlagHint() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg != null && arg.contains("-XX:+AllowEnhancedClassRedefinition")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsJetBrains(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("jetbrains");
    }

    /**
     * Defines {@link #PROBE_CLASS} with {@code a()} and redefines it to add {@code b():void}.
     * Failures stay inside the probe — they are not a user-visible reload error.
     */
    private static boolean trialAddMethod(Instrumentation inst) {
        try {
            ProbeLoader loader = new ProbeLoader();
            Class<?> target = loader.define(probeBytes(false));
            inst.redefineClasses(new ClassDefinition(target, probeBytes(true)));
            return true;
        } catch (UnsupportedOperationException | InternalError e) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static byte[] probeBytes(boolean withB) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                PROBE_CLASS.replace('.', '/'),
                null,
                "java/lang/Object",
                null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor a = writer.visitMethod(Opcodes.ACC_PUBLIC, "a", "()V", null, null);
        a.visitCode();
        a.visitInsn(Opcodes.RETURN);
        a.visitMaxs(0, 1);
        a.visitEnd();
        if (withB) {
            MethodVisitor b = writer.visitMethod(Opcodes.ACC_PUBLIC, "b", "()V", null, null);
            b.visitCode();
            b.visitInsn(Opcodes.RETURN);
            b.visitMaxs(0, 1);
            b.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Override
    public Support assess(ClassDelta delta) {
        if (delta == null) {
            return Support.UNSUPPORTED;
        }
        if (delta.anonymousIndexShiftLikely) {
            return Support.UNSUPPORTED;
        }
        if (delta.kinds.isEmpty()) {
            return Support.FULL;
        }
        boolean anySupported = false;
        boolean anyUnsupported = false;
        for (ChangeKind kind : delta.kinds) {
            if (FAIL_CLOSED.contains(kind)) {
                return Support.UNSUPPORTED;
            }
            if (SUPPORTED.contains(kind)) {
                anySupported = true;
            } else {
                anyUnsupported = true;
            }
        }
        if (anyUnsupported && anySupported) {
            return Support.PARTIAL;
        }
        if (anyUnsupported) {
            return Support.UNSUPPORTED;
        }
        return Support.FULL;
    }

    @Override
    public ReloadBackendResult apply(Instrumentation inst, List<Redefinition> batch) throws Exception {
        if (batch == null || batch.isEmpty()) {
            return new ReloadBackendResult(List.of());
        }
        List<ClassDefinition> defs = new ArrayList<>(batch.size());
        List<Class<?>> redefined = new ArrayList<>(batch.size());
        for (Redefinition item : batch) {
            if (item.loaded == null) {
                continue;
            }
            defs.add(new ClassDefinition(item.loaded, item.bytes));
            redefined.add(item.loaded);
        }
        if (!defs.isEmpty()) {
            inst.redefineClasses(defs.toArray(ClassDefinition[]::new));
        }
        return new ReloadBackendResult(redefined);
    }

    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader() {
            super(EnhancedHotSwapBackend.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(PROBE_CLASS, bytes, 0, bytes.length);
        }
    }
}

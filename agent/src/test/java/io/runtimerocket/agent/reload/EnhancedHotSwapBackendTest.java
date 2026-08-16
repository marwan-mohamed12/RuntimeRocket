package io.runtimerocket.agent.reload;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancedHotSwapBackendTest {

    private final EnhancedHotSwapBackend backend = new EnhancedHotSwapBackend();
    private final ClassDeltaClassifier classifier = new ClassDeltaClassifier();

    @Test
    void addMethodIsFull() {
        ClassDelta delta = classifier.classify(DeltaFixtures.addMethod().before(), DeltaFixtures.addMethod().after());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_METHOD));
        assertEquals(Support.FULL, backend.assess(delta));
    }

    @Test
    void enumConstantsAreUnsupported() {
        ClassDelta delta =
                classifier.classify(DeltaFixtures.enumConstantAdd().before(), DeltaFixtures.enumConstantAdd().after());
        assertTrue(delta.kinds.contains(ChangeKind.ENUM_CONSTANTS));
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void hierarchySuperIsUnsupported() {
        ClassDelta delta =
                classifier.classify(DeltaFixtures.hierarchySuper().before(), DeltaFixtures.hierarchySuper().after());
        assertTrue(delta.kinds.contains(ChangeKind.HIERARCHY_SUPER));
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void hierarchyIfacesIsUnsupported() {
        ClassDelta delta =
                classifier.classify(DeltaFixtures.hierarchyIfaces().before(), DeltaFixtures.hierarchyIfaces().after());
        assertTrue(delta.kinds.contains(ChangeKind.HIERARCHY_IFACES));
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void fieldTypeChangeIsUnsupported() {
        ClassDelta delta = classifier.classify(fieldClass("I"), fieldClass("Ljava/lang/String;"));
        assertTrue(delta.kinds.contains(ChangeKind.CHANGE_FIELD_DESC), delta.kinds.toString());
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void addFieldSameDescIsFull() {
        ClassDelta delta = classifier.classify(DeltaFixtures.addField().before(), DeltaFixtures.addField().after());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_FIELD));
        assertEquals(Support.FULL, backend.assess(delta));
    }

    @Test
    void anonymousIndexShiftIsUnsupported() {
        ClassDelta delta = classifier.classify(
                DeltaFixtures.anonymousIndexShift().before(), DeltaFixtures.anonymousIndexShift().after());
        assertTrue(delta.anonymousIndexShiftLikely);
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void probeAddsMethodOnThrowawayClass() {
        AtomicInteger calls = new AtomicInteger();
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {
            calls.incrementAndGet();
            assertEquals(1, defs.length);
            Class<?> target = defs[0].getDefinitionClass();
            assertEquals(EnhancedHotSwapBackend.PROBE_CLASS, target.getName());
            assertTrue(hasMethod(target, "a"));
            assertFalse(hasMethod(target, "b"));
            ClassDelta delta = classifier.classify(EnhancedHotSwapBackend.probeBytes(false), defs[0].getDefinitionClassFile());
            assertTrue(delta.kinds.contains(ChangeKind.ADD_METHOD), delta.kinds.toString());
        });
        assertTrue(backend.probe(inst));
        assertTrue(backend.probe(inst));
        assertEquals(1, calls.get());
    }

    @Test
    void probeReturnsFalseWhenRedefineRejected() {
        Instrumentation inst =
                FakeInstrumentation.of(true, defs -> {
                    throw new UnsupportedOperationException("no enhanced");
                });
        assertFalse(backend.probe(inst));
    }

    @Test
    void probeReturnsFalseOnInternalError() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {
            throw new InternalError("no enhanced");
        });
        assertFalse(backend.probe(inst));
    }

    @Test
    void hintsParseVmIdentityAndFlag() {
        String name = System.getProperty("java.vm.name", "");
        String vendor = System.getProperty("java.vm.vendor", "");
        boolean jetbrains = containsJetBrains(name) || containsJetBrains(vendor);
        assertEquals(jetbrains, EnhancedHotSwapBackend.jetbrainsVmHint());

        boolean flag = false;
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg != null && arg.contains("-XX:+AllowEnhancedClassRedefinition")) {
                flag = true;
                break;
            }
        }
        assertEquals(flag, EnhancedHotSwapBackend.enhancedFlagHint());
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsJetBrains(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("jetbrains");
    }

    private static byte[] fieldClass(String desc) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "demo/FieldType", null, "java/lang/Object", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "value", desc, null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}

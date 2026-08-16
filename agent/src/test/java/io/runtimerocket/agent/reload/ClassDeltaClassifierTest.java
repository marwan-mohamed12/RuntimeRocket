package io.runtimerocket.agent.reload;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDeltaClassifierTest {

    private final ClassDeltaClassifier classifier = new ClassDeltaClassifier();

    @Test
    void methodBodyChange() {
        ClassDelta delta = classify(DeltaFixtures.bodyChange());
        assertTrue(delta.kinds.contains(ChangeKind.METHOD_BODY), delta.toString());
        assertEquals(List.of(new MemberRef("value", "()I")), delta.bodyChangedMethods);
        assertFalse(delta.anonymousIndexShiftLikely);
    }

    @Test
    void addMethod() {
        ClassDelta delta = classify(DeltaFixtures.addMethod());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_METHOD), delta.toString());
        assertEquals(List.of(new MemberRef("extra", "()V")), delta.addedMethods);
        assertFalse(delta.kinds.contains(ChangeKind.ADD_CONSTRUCTOR), delta.toString());
    }

    @Test
    void removeMethod() {
        ClassDelta delta = classify(DeltaFixtures.removeMethod());
        assertTrue(delta.kinds.contains(ChangeKind.REMOVE_METHOD), delta.toString());
        assertEquals(List.of(new MemberRef("extra", "()V")), delta.removedMethods);
    }

    @Test
    void addField() {
        ClassDelta delta = classify(DeltaFixtures.addField());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_FIELD), delta.toString());
        assertEquals(List.of(new MemberRef("extra", "Ljava/lang/String;")), delta.addedFields);
    }

    @Test
    void removeField() {
        ClassDelta delta = classify(DeltaFixtures.removeField());
        assertTrue(delta.kinds.contains(ChangeKind.REMOVE_FIELD), delta.toString());
        assertEquals(List.of(new MemberRef("extra", "Ljava/lang/String;")), delta.removedFields);
    }

    @Test
    void hierarchySuper() {
        ClassDelta delta = classify(DeltaFixtures.hierarchySuper());
        assertTrue(delta.kinds.contains(ChangeKind.HIERARCHY_SUPER), delta.toString());
        assertTrue(delta.superclassChanged);
        assertFalse(delta.interfacesChanged);
    }

    @Test
    void hierarchyIfaces() {
        ClassDelta delta = classify(DeltaFixtures.hierarchyIfaces());
        assertTrue(delta.kinds.contains(ChangeKind.HIERARCHY_IFACES), delta.toString());
        assertTrue(delta.interfacesChanged);
        assertFalse(delta.superclassChanged);
    }

    @Test
    void enumConstantAddIsEnumConstants() {
        ClassDelta delta = classify(DeltaFixtures.enumConstantAdd());
        assertTrue(delta.kinds.contains(ChangeKind.ENUM_CONSTANTS), delta.toString());
        assertTrue(delta.enumConstantsChanged);
        assertTrue(delta.kinds.contains(ChangeKind.ADD_FIELD), delta.toString());
        assertEquals(List.of(new MemberRef("BLUE", "Ldemo/Color;")), delta.addedFields);
    }

    @Test
    void recordComponentChange() {
        ClassDelta delta = classify(DeltaFixtures.recordComponent());
        assertTrue(delta.kinds.contains(ChangeKind.RECORD_COMPONENTS), delta.toString());
        assertTrue(delta.recordComponentsChanged);
    }

    @Test
    void anonymousIndexShiftRenamesFoo1ToFoo2() {
        ClassDelta delta = classify(DeltaFixtures.anonymousIndexShift());
        assertTrue(delta.anonymousIndexShiftLikely, delta.toString());
        assertTrue(delta.kinds.contains(ChangeKind.INNER_CLASSES_MAP), delta.toString());
    }

    @Test
    void trailingAnonymousAddIsNotIndexShift() {
        ClassDelta delta = classify(DeltaFixtures.anonymousTrailingAdd());
        assertFalse(delta.anonymousIndexShiftLikely, delta.toString());
        assertTrue(delta.kinds.contains(ChangeKind.INNER_CLASSES_MAP), delta.toString());
    }

    @Test
    void classFileVersion69BodyChange() {
        DeltaFixtures.Fixture fixture = DeltaFixtures.v69BodyChange();
        assertEquals(69, DeltaFixtures.majorVersion(fixture.before()));
        assertEquals(69, DeltaFixtures.majorVersion(fixture.after()));
        ClassDelta delta = classify(fixture);
        assertTrue(delta.kinds.contains(ChangeKind.METHOD_BODY), delta.toString());
        assertEquals("demo/Java25Body", delta.internalName);
    }

    @Test
    void lineNumberTableOnlyIsConstantPoolOnly() {
        ClassDelta delta = classify(DeltaFixtures.lineNumbersOnly());
        assertEquals(java.util.EnumSet.of(ChangeKind.CONSTANT_POOL_ONLY), delta.kinds);
        assertTrue(delta.bodyChangedMethods.isEmpty());
    }

    @Test
    void sourceFileOnlyIsConstantPoolOnly() {
        ClassDelta delta = classify(DeltaFixtures.sourceFileOnly());
        assertEquals(java.util.EnumSet.of(ChangeKind.CONSTANT_POOL_ONLY), delta.kinds);
    }

    @Test
    void identicalBytesHaveNoKinds() {
        byte[] bytes = DeltaFixtures.bodyChange().before();
        ClassDelta delta = classifier.classify(bytes, bytes);
        assertTrue(delta.kinds.isEmpty(), delta.toString());
        assertFalse(delta.anonymousIndexShiftLikely);
    }

    @Test
    void missingPreviousIsNewType() {
        byte[] current = DeltaFixtures.addMethod().after();
        ClassDelta delta = classifier.classify(null, current);
        assertTrue(delta.kinds.contains(ChangeKind.NEW_TYPE), delta.toString());
        assertFalse(delta.addedMethods.isEmpty());
    }

    @Test
    void changeMethodDescriptor() {
        byte[] before = methodWithDesc("()I");
        byte[] after = methodWithDesc("(I)I");
        ClassDelta delta = classifier.classify(before, after);
        assertTrue(delta.kinds.contains(ChangeKind.CHANGE_METHOD_DESC), delta.toString());
        assertTrue(delta.kinds.contains(ChangeKind.ADD_METHOD), delta.toString());
        assertTrue(delta.kinds.contains(ChangeKind.REMOVE_METHOD), delta.toString());
        assertEquals(List.of(new MemberRef("n", "(I)I")), delta.addedMethods);
        assertEquals(List.of(new MemberRef("n", "()I")), delta.removedMethods);
    }

    @Test
    void addConstructor() {
        byte[] before = ctorOnly("()V");
        byte[] after = twoCtors();
        ClassDelta delta = classifier.classify(before, after);
        assertTrue(delta.kinds.contains(ChangeKind.ADD_CONSTRUCTOR), delta.toString());
        assertFalse(delta.kinds.contains(ChangeKind.ADD_METHOD), delta.toString());
        assertEquals(List.of(new MemberRef("<init>", "(I)V")), delta.addedMethods);
    }

    @Test
    void emptyCurrentRejected() {
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(new byte[] {1}, new byte[0]));
    }

    private ClassDelta classify(DeltaFixtures.Fixture fixture) {
        return classifier.classify(fixture.before(), fixture.after());
    }

    private static byte[] methodWithDesc(String desc) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "demo/Desc", null, "java/lang/Object", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "n", desc, null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, desc.equals("()I") ? 1 : 2);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] ctorOnly(String desc) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "demo/Ctors", null, "java/lang/Object", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] twoCtors() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "demo/Ctors", null, "java/lang/Object", null);
        MethodVisitor zero = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        zero.visitCode();
        zero.visitVarInsn(Opcodes.ALOAD, 0);
        zero.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        zero.visitInsn(Opcodes.RETURN);
        zero.visitMaxs(1, 1);
        zero.visitEnd();
        MethodVisitor one = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        one.visitCode();
        one.visitVarInsn(Opcodes.ALOAD, 0);
        one.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        one.visitInsn(Opcodes.RETURN);
        one.visitMaxs(1, 2);
        one.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}

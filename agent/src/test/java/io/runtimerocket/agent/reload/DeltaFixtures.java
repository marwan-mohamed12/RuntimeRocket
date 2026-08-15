package io.runtimerocket.agent.reload;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/** ASM-built class-file pairs used by unit tests and written into {@code deltas/**}. */
final class DeltaFixtures {

    static final int V17 = Opcodes.V17;
    static final int V25 = Opcodes.V25;

    private DeltaFixtures() {}

    static List<Fixture> all() {
        List<Fixture> fixtures = new ArrayList<>();
        fixtures.add(bodyChange());
        fixtures.add(addMethod());
        fixtures.add(removeMethod());
        fixtures.add(addField());
        fixtures.add(removeField());
        fixtures.add(hierarchySuper());
        fixtures.add(hierarchyIfaces());
        fixtures.add(enumConstantAdd());
        fixtures.add(recordComponent());
        fixtures.add(anonymousIndexShift());
        fixtures.add(anonymousTrailingAdd());
        fixtures.add(v69BodyChange());
        fixtures.add(lineNumbersOnly());
        fixtures.add(sourceFileOnly());
        return fixtures;
    }

    static Fixture bodyChange() {
        return new Fixture(
                "body-change",
                classWithIntMethod(V17, "demo/Body", 1, -1),
                classWithIntMethod(V17, "demo/Body", 2, -1),
                "contains=METHOD_BODY",
                "shift=false");
    }

    static Fixture addMethod() {
        return new Fixture(
                "add-method",
                classWithOptionalExtraMethod(false),
                classWithOptionalExtraMethod(true),
                "contains=ADD_METHOD",
                "shift=false");
    }

    static Fixture removeMethod() {
        return new Fixture(
                "remove-method",
                classWithOptionalExtraMethod(true),
                classWithOptionalExtraMethod(false),
                "contains=REMOVE_METHOD",
                "shift=false");
    }

    static Fixture addField() {
        return new Fixture(
                "add-field",
                classWithOptionalField(false),
                classWithOptionalField(true),
                "contains=ADD_FIELD",
                "shift=false");
    }

    static Fixture removeField() {
        return new Fixture(
                "remove-field",
                classWithOptionalField(true),
                classWithOptionalField(false),
                "contains=REMOVE_FIELD",
                "shift=false");
    }

    static Fixture hierarchySuper() {
        return new Fixture(
                "hierarchy-super",
                classWithSuper("java/lang/Object"),
                classWithSuper("java/lang/Exception"),
                "contains=HIERARCHY_SUPER",
                "shift=false");
    }

    static Fixture hierarchyIfaces() {
        return new Fixture(
                "hierarchy-ifaces",
                classWithInterfaces(),
                classWithInterfaces("java/lang/Runnable"),
                "contains=HIERARCHY_IFACES",
                "shift=false");
    }

    static Fixture enumConstantAdd() {
        return new Fixture(
                "enum-constant-add",
                enumClass("RED"),
                enumClass("RED", "BLUE"),
                "contains=ENUM_CONSTANTS",
                "shift=false");
    }

    static Fixture recordComponent() {
        return new Fixture(
                "record-component",
                recordClass("x"),
                recordClass("x", "y"),
                "contains=RECORD_COMPONENTS",
                "shift=false");
    }

    static Fixture anonymousIndexShift() {
        return new Fixture(
                "anonymous-index-shift",
                classWithAnonymousInners(1),
                classWithAnonymousInners(2),
                "contains=INNER_CLASSES_MAP",
                "shift=true");
    }

    static Fixture anonymousTrailingAdd() {
        return new Fixture(
                "anonymous-trailing-add",
                classWithAnonymousInners(1),
                classWithAnonymousInners(1, 2),
                "contains=INNER_CLASSES_MAP",
                "shift=false");
    }

    static Fixture v69BodyChange() {
        return new Fixture(
                "v69-body",
                classWithIntMethod(V25, "demo/Java25Body", 1, -1),
                classWithIntMethod(V25, "demo/Java25Body", 2, -1),
                "contains=METHOD_BODY",
                "shift=false",
                "version=69");
    }

    static Fixture lineNumbersOnly() {
        return new Fixture(
                "line-numbers-only",
                classWithIntMethod(V17, "demo/Lines", 1, 10),
                classWithIntMethod(V17, "demo/Lines", 1, 20),
                "contains=CONSTANT_POOL_ONLY",
                "shift=false");
    }

    static Fixture sourceFileOnly() {
        return new Fixture(
                "source-file-only",
                classWithSource("A.java"),
                classWithSource("B.java"),
                "contains=CONSTANT_POOL_ONLY",
                "shift=false");
    }

    private static byte[] classWithIntMethod(int version, String name, int value, int lineNumber) {
        ClassWriter writer = begin(version, name, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object");
        defaultCtor(writer, "java/lang/Object");
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        mv.visitCode();
        Label start = new Label();
        mv.visitLabel(start);
        if (lineNumber >= 0) {
            mv.visitLineNumber(lineNumber, start);
        }
        pushInt(mv, value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithOptionalExtraMethod(boolean extra) {
        ClassWriter writer = begin(V17, "demo/Methods", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object");
        defaultCtor(writer, "java/lang/Object");
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

    private static byte[] classWithOptionalField(boolean extra) {
        ClassWriter writer = begin(V17, "demo/Fields", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object");
        defaultCtor(writer, "java/lang/Object");
        writer.visitField(Opcodes.ACC_PRIVATE, "kept", "I", null, null).visitEnd();
        if (extra) {
            writer.visitField(Opcodes.ACC_PRIVATE, "extra", "Ljava/lang/String;", null, null).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithSuper(String superName) {
        ClassWriter writer = begin(V17, "demo/Hierarchy", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, superName);
        defaultCtor(writer, superName);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithInterfaces(String... interfaces) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "demo/Ifaces",
                null,
                "java/lang/Object",
                interfaces.length == 0 ? null : interfaces);
        defaultCtor(writer, "java/lang/Object");
        if (interfaces.length > 0) {
            MethodVisitor run = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
            run.visitCode();
            run.visitInsn(Opcodes.RETURN);
            run.visitMaxs(0, 1);
            run.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] enumClass(String... constants) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
                "demo/Color",
                "Ljava/lang/Enum<Ldemo/Color;>;",
                "java/lang/Enum",
                null);
        for (String constant : constants) {
            writer.visitField(
                            Opcodes.ACC_PUBLIC
                                    | Opcodes.ACC_STATIC
                                    | Opcodes.ACC_FINAL
                                    | Opcodes.ACC_ENUM,
                            constant,
                            "Ldemo/Color;",
                            null,
                            null)
                    .visitEnd();
        }
        writer.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                        "$VALUES",
                        "[Ldemo/Color;",
                        null,
                        null)
                .visitEnd();
        MethodVisitor init =
                writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitVarInsn(Opcodes.ILOAD, 2);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 3);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] recordClass(String... components) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                "demo/Point",
                null,
                "java/lang/Record",
                null);
        StringBuilder ctorDesc = new StringBuilder("(");
        for (String component : components) {
            writer.visitRecordComponent(component, "I", null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, component, "I", null, null)
                    .visitEnd();
            ctorDesc.append('I');
        }
        ctorDesc.append(")V");
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc.toString(), null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1 + components.length);
        ctor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithAnonymousInners(int... indices) {
        ClassWriter writer = begin(V17, "demo/AnonHost", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object");
        defaultCtor(writer, "java/lang/Object");
        for (int index : indices) {
            writer.visitInnerClass("demo/AnonHost$" + index, "demo/AnonHost", null, 0);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithSource(String source) {
        ClassWriter writer = begin(V17, "demo/Source", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object");
        writer.visitSource(source, null);
        defaultCtor(writer, "java/lang/Object");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter begin(int version, String name, int access, String superName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, access, name, null, superName, null);
        return writer;
    }

    private static void defaultCtor(ClassWriter writer, String superName) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
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

    static int majorVersion(byte[] classFile) {
        if (classFile.length < 8) {
            throw new IllegalArgumentException("too short to be a class file");
        }
        return ((classFile[6] & 0xFF) << 8) | (classFile[7] & 0xFF);
    }

    record Fixture(String directory, byte[] before, byte[] after, String... expectedLines) {
        String expectedText() {
            return String.join("\n", expectedLines) + "\n";
        }
    }
}

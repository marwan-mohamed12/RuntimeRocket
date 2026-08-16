package io.runtimerocket.agent.reload;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Parsed view of one class file. Debug attributes are omitted so line/LVT-only diffs stay empty. */
final class ClassFileModel {

    final String internalName;
    final String superName;
    final int access;
    final List<String> interfaces;
    final String nestHost;
    final List<String> nestMembers;
    final List<String> permittedSubclasses;
    final List<RecordComponentModel> recordComponents;
    final List<InnerClassModel> innerClasses;
    final List<FieldModel> fields;
    final List<MethodModel> methods;
    final List<String> annotations;
    final List<String> bootstrapTypeRefs;

    private ClassFileModel(Builder builder) {
        this.internalName = builder.internalName;
        this.superName = builder.superName;
        this.access = builder.access;
        this.interfaces = List.copyOf(builder.interfaces);
        this.nestHost = builder.nestHost;
        this.nestMembers = List.copyOf(builder.nestMembers);
        this.permittedSubclasses = List.copyOf(builder.permittedSubclasses);
        this.recordComponents = List.copyOf(builder.recordComponents);
        this.innerClasses = List.copyOf(builder.innerClasses);
        this.fields = List.copyOf(builder.fields);
        this.methods = List.copyOf(builder.methods);
        this.annotations = List.copyOf(builder.annotations);
        this.bootstrapTypeRefs = List.copyOf(builder.bootstrapTypeRefs);
    }

    static ClassFileModel parse(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        Builder builder = new Builder();
        // SKIP_DEBUG drops SourceFile / LineNumberTable / LocalVariableTable so those
        // alone do not look like a method-body or structural change.
        reader.accept(builder, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return builder.build();
    }

    Map<MemberRef, FieldModel> fieldMap() {
        Map<MemberRef, FieldModel> map = new LinkedHashMap<>();
        for (FieldModel field : fields) {
            map.put(new MemberRef(field.name, field.descriptor), field);
        }
        return map;
    }

    Map<MemberRef, MethodModel> methodMap() {
        Map<MemberRef, MethodModel> map = new LinkedHashMap<>();
        for (MethodModel method : methods) {
            map.put(new MemberRef(method.name, method.descriptor), method);
        }
        return map;
    }

    List<String> enumConstantNames() {
        List<String> names = new ArrayList<>();
        for (FieldModel field : fields) {
            if ((field.access & Opcodes.ACC_ENUM) != 0) {
                names.add(field.name);
            }
        }
        return names;
    }

    List<Integer> anonymousInnerIndices() {
        String prefix = internalName + "$";
        List<Integer> indices = new ArrayList<>();
        for (InnerClassModel inner : innerClasses) {
            if (inner.innerName != null) {
                continue;
            }
            Integer index = numericSuffix(inner.name, prefix);
            if (index != null) {
                indices.add(index);
            }
        }
        return indices;
    }

    List<Integer> lambdaMethodIndices() {
        List<Integer> indices = new ArrayList<>();
        for (MethodModel method : methods) {
            Integer index = lambdaIndex(method.name);
            if (index != null) {
                indices.add(index);
            }
        }
        return indices;
    }

    List<Integer> bootstrapAnonymousIndices() {
        String prefix = internalName + "$";
        List<Integer> indices = new ArrayList<>();
        for (String typeRef : bootstrapTypeRefs) {
            Integer index = numericSuffix(typeRef, prefix);
            if (index != null) {
                indices.add(index);
            }
        }
        return indices;
    }

    static Integer numericSuffix(String name, String prefix) {
        if (name == null || !name.startsWith(prefix)) {
            return null;
        }
        String suffix = name.substring(prefix.length());
        if (suffix.isEmpty()) {
            return null;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return null;
            }
        }
        return Integer.parseInt(suffix);
    }

    static Integer lambdaIndex(String methodName) {
        if (methodName == null || !methodName.startsWith("lambda$")) {
            return null;
        }
        int lastDollar = methodName.lastIndexOf('$');
        if (lastDollar < "lambda$".length()) {
            return null;
        }
        String suffix = methodName.substring(lastDollar + 1);
        if (suffix.isEmpty()) {
            return null;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return null;
            }
        }
        return Integer.parseInt(suffix);
    }

    static final class FieldModel {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final Object value;
        final List<String> annotations;

        FieldModel(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value,
                List<String> annotations) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.value = value;
            this.annotations = List.copyOf(annotations);
        }

        boolean modifiersDiffer(FieldModel other) {
            return access != other.access || !Objects.equals(signature, other.signature);
        }

        boolean annotationsDiffer(FieldModel other) {
            return !annotations.equals(other.annotations);
        }
    }

    static final class MethodModel {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final List<String> exceptions;
        final List<String> annotations;
        final List<String> instructions;

        MethodModel(
                int access,
                String name,
                String descriptor,
                String signature,
                List<String> exceptions,
                List<String> annotations,
                List<String> instructions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = List.copyOf(exceptions);
            this.annotations = List.copyOf(annotations);
            this.instructions = List.copyOf(instructions);
        }

        boolean bodyDiffers(MethodModel other) {
            return !instructions.equals(other.instructions);
        }

        boolean modifiersDiffer(MethodModel other) {
            return access != other.access
                    || !Objects.equals(signature, other.signature)
                    || !exceptions.equals(other.exceptions);
        }

        boolean annotationsDiffer(MethodModel other) {
            return !annotations.equals(other.annotations);
        }

        boolean isConstructor() {
            return "<init>".equals(name);
        }
    }

    static final class RecordComponentModel {
        final String name;
        final String descriptor;
        final String signature;
        final List<String> annotations;

        RecordComponentModel(String name, String descriptor, String signature, List<String> annotations) {
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.annotations = List.copyOf(annotations);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RecordComponentModel other)) {
                return false;
            }
            return name.equals(other.name)
                    && descriptor.equals(other.descriptor)
                    && Objects.equals(signature, other.signature)
                    && annotations.equals(other.annotations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, descriptor, signature, annotations);
        }
    }

    static final class InnerClassModel {
        final String name;
        final String outerName;
        final String innerName;
        final int access;

        InnerClassModel(String name, String outerName, String innerName, int access) {
            this.name = name;
            this.outerName = outerName;
            this.innerName = innerName;
            this.access = access;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof InnerClassModel other)) {
                return false;
            }
            return access == other.access
                    && Objects.equals(name, other.name)
                    && Objects.equals(outerName, other.outerName)
                    && Objects.equals(innerName, other.innerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, outerName, innerName, access);
        }
    }

    private static final class Builder extends ClassVisitor {
        private String internalName;
        private String superName;
        private int access;
        private final List<String> interfaces = new ArrayList<>();
        private String nestHost;
        private final List<String> nestMembers = new ArrayList<>();
        private final List<String> permittedSubclasses = new ArrayList<>();
        private final List<RecordComponentModel> recordComponents = new ArrayList<>();
        private final List<InnerClassModel> innerClasses = new ArrayList<>();
        private final List<FieldModel> fields = new ArrayList<>();
        private final List<MethodModel> methods = new ArrayList<>();
        private final List<String> annotations = new ArrayList<>();
        private final List<String> bootstrapTypeRefs = new ArrayList<>();

        private Builder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {
            this.access = access;
            this.internalName = name;
            this.superName = superName;
            if (interfaces != null) {
                this.interfaces.addAll(Arrays.asList(interfaces));
            }
            if (signature != null) {
                annotations.add("class-signature:" + signature);
            }
        }

        @Override
        public void visitNestHost(String nestHost) {
            this.nestHost = nestHost;
        }

        @Override
        public void visitNestMember(String nestMember) {
            nestMembers.add(nestMember);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            permittedSubclasses.add(permittedSubclass);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            innerClasses.add(new InnerClassModel(name, outerName, innerName, access));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return AnnotationRecorder.capture(annotations, "ann:" + visible + ":" + descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef, TypePath typePath, String descriptor, boolean visible) {
            String path = typePath == null ? "" : typePath.toString();
            return AnnotationRecorder.capture(
                    annotations, "typeann:" + visible + ":" + typeRef + ":" + path + ":" + descriptor);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            List<String> recAnns = new ArrayList<>();
            return new RecordComponentVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return AnnotationRecorder.capture(recAnns, "ann:" + visible + ":" + desc);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String desc, boolean visible) {
                    String path = typePath == null ? "" : typePath.toString();
                    return AnnotationRecorder.capture(
                            recAnns, "typeann:" + visible + ":" + typeRef + ":" + path + ":" + desc);
                }

                @Override
                public void visitEnd() {
                    recordComponents.add(new RecordComponentModel(name, descriptor, signature, recAnns));
                }
            };
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            List<String> fieldAnns = new ArrayList<>();
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return AnnotationRecorder.capture(fieldAnns, "ann:" + visible + ":" + desc);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String desc, boolean visible) {
                    String path = typePath == null ? "" : typePath.toString();
                    return AnnotationRecorder.capture(
                            fieldAnns, "typeann:" + visible + ":" + typeRef + ":" + path + ":" + desc);
                }

                @Override
                public void visitEnd() {
                    fields.add(new FieldModel(access, name, descriptor, signature, value, fieldAnns));
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            List<String> methodAnns = new ArrayList<>();
            List<String> instructions = new ArrayList<>();
            List<String> thrown = exceptions == null ? List.of() : Arrays.asList(exceptions);
            return new MethodBodyVisitor(methodAnns, instructions, bootstrapTypeRefs) {
                @Override
                public void visitEnd() {
                    methods.add(
                            new MethodModel(
                                    access, name, descriptor, signature, thrown, methodAnns, instructions));
                }
            };
        }

        ClassFileModel build() {
            if (internalName == null) {
                throw new IllegalArgumentException("class file missing this_class");
            }
            return new ClassFileModel(this);
        }
    }

    private static class MethodBodyVisitor extends MethodVisitor {
        private final List<String> annotations;
        private final List<String> instructions;
        private final List<String> bootstrapTypeRefs;
        private final Map<Label, Integer> labels = new LinkedHashMap<>();

        MethodBodyVisitor(
                List<String> annotations, List<String> instructions, List<String> bootstrapTypeRefs) {
            super(Opcodes.ASM9);
            this.annotations = annotations;
            this.instructions = instructions;
            this.bootstrapTypeRefs = bootstrapTypeRefs;
        }

        private int labelId(Label label) {
            return labels.computeIfAbsent(label, ignored -> labels.size());
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return AnnotationRecorder.capture(annotations, "ann:" + visible + ":" + descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef, TypePath typePath, String descriptor, boolean visible) {
            String path = typePath == null ? "" : typePath.toString();
            return AnnotationRecorder.capture(
                    annotations, "typeann:" + visible + ":" + typeRef + ":" + path + ":" + descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return AnnotationRecorder.capture(annotations, "default");
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            return AnnotationRecorder.capture(
                    annotations, "param:" + parameter + ":" + visible + ":" + descriptor);
        }

        @Override
        public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            annotations.add("paramcount:" + visible + ":" + parameterCount);
        }

        @Override
        public void visitInsn(int opcode) {
            instructions.add("insn:" + opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            instructions.add("int:" + opcode + ":" + operand);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            instructions.add("var:" + opcode + ":" + varIndex);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            instructions.add("type:" + opcode + ":" + type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            instructions.add("field:" + opcode + ":" + owner + "." + name + descriptor);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            instructions.add(
                    "method:" + opcode + ":" + owner + "." + name + descriptor + ":" + isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(
                String name,
                String descriptor,
                Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
            instructions.add(
                    "indy:"
                            + name
                            + descriptor
                            + ":"
                            + stringify(bootstrapMethodHandle)
                            + ":"
                            + stringifyArgs(bootstrapMethodArguments));
            collectHandle(bootstrapMethodHandle);
            for (Object arg : bootstrapMethodArguments) {
                collectBootstrapArg(arg);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            instructions.add("jump:" + opcode + ":" + labelId(label));
        }

        @Override
        public void visitLabel(Label label) {
            instructions.add("label:" + labelId(label));
        }

        @Override
        public void visitLdcInsn(Object value) {
            instructions.add("ldc:" + stringify(value));
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            instructions.add("iinc:" + varIndex + ":" + increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            instructions.add(
                    "tableswitch:" + min + ":" + max + ":" + labelId(dflt) + ":" + labelIds(labels));
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            instructions.add(
                    "lookupswitch:"
                            + labelId(dflt)
                            + ":"
                            + Arrays.toString(keys)
                            + ":"
                            + labelIds(labels));
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            instructions.add("multianewarray:" + descriptor + ":" + numDimensions);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            instructions.add(
                    "try:" + labelId(start) + ":" + labelId(end) + ":" + labelId(handler) + ":" + type);
        }

        private String labelIds(Label[] labels) {
            int[] ids = new int[labels.length];
            for (int i = 0; i < labels.length; i++) {
                ids[i] = labelId(labels[i]);
            }
            return Arrays.toString(ids);
        }

        private void collectHandle(Handle handle) {
            if (handle != null) {
                bootstrapTypeRefs.add(handle.getOwner());
            }
        }

        private void collectBootstrapArg(Object arg) {
            if (arg instanceof Handle handle) {
                collectHandle(handle);
            } else if (arg instanceof Type type) {
                if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                    bootstrapTypeRefs.add(type.getInternalName());
                }
            } else if (arg instanceof ConstantDynamic constantDynamic) {
                collectHandle(constantDynamic.getBootstrapMethod());
                for (int i = 0; i < constantDynamic.getBootstrapMethodArgumentCount(); i++) {
                    collectBootstrapArg(constantDynamic.getBootstrapMethodArgument(i));
                }
            }
        }
    }

    private static String stringifyArgs(Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(stringify(args[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "s:" + string;
        }
        if (value instanceof Type type) {
            return "t:" + type.getDescriptor();
        }
        if (value instanceof Handle handle) {
            return "h:"
                    + handle.getTag()
                    + ":"
                    + handle.getOwner()
                    + "."
                    + handle.getName()
                    + handle.getDesc()
                    + ":"
                    + handle.isInterface();
        }
        if (value instanceof ConstantDynamic constantDynamic) {
            Object[] args = new Object[constantDynamic.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = constantDynamic.getBootstrapMethodArgument(i);
            }
            return "condy:"
                    + constantDynamic.getName()
                    + constantDynamic.getDescriptor()
                    + ":"
                    + stringify(constantDynamic.getBootstrapMethod())
                    + ":"
                    + stringifyArgs(args);
        }
        if (value instanceof byte[] bytes) {
            return "bytes:" + Arrays.toString(bytes);
        }
        if (value instanceof boolean[] booleans) {
            return "bools:" + Arrays.toString(booleans);
        }
        if (value instanceof short[] shorts) {
            return "shorts:" + Arrays.toString(shorts);
        }
        if (value instanceof char[] chars) {
            return "chars:" + Arrays.toString(chars);
        }
        if (value instanceof int[] ints) {
            return "ints:" + Arrays.toString(ints);
        }
        if (value instanceof long[] longs) {
            return "longs:" + Arrays.toString(longs);
        }
        if (value instanceof float[] floats) {
            return "floats:" + Arrays.toString(floats);
        }
        if (value instanceof double[] doubles) {
            return "doubles:" + Arrays.toString(doubles);
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    private static class AnnotationRecorder extends AnnotationVisitor {
        final StringBuilder body = new StringBuilder();
        private final List<String> sink;
        private final String prefix;

        private AnnotationRecorder(List<String> sink, String prefix) {
            super(Opcodes.ASM9);
            this.sink = sink;
            this.prefix = prefix;
        }

        static AnnotationVisitor capture(List<String> sink, String prefix) {
            return new AnnotationRecorder(sink, prefix);
        }

        @Override
        public void visit(String name, Object value) {
            body.append(name).append('=').append(stringify(value)).append(';');
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            body.append(name).append("=e:").append(descriptor).append('.').append(value).append(';');
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return new AnnotationRecorder(null, name + ":" + descriptor) {
                @Override
                public void visitEnd() {
                    AnnotationRecorder.this.body
                            .append(name)
                            .append("={")
                            .append(this.body)
                            .append("};");
                }
            };
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationRecorder(null, name) {
                @Override
                public void visitEnd() {
                    AnnotationRecorder.this.body
                            .append(name)
                            .append("=[")
                            .append(this.body)
                            .append("];");
                }
            };
        }

        @Override
        public void visitEnd() {
            if (sink != null) {
                sink.add(prefix + "{" + body + "}");
            }
        }
    }
}

package io.runtimerocket.agent.reload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compares two class files with ASM and emits a {@link ClassDelta}. */
public final class ClassDeltaClassifier {

    public ClassDelta classify(byte[] previous, byte[] current) {
        Objects.requireNonNull(current, "current");
        if (current.length == 0) {
            throw new IllegalArgumentException("current class file is empty");
        }
        ClassFileModel after = ClassFileModel.parse(current);
        if (previous == null || previous.length == 0) {
            return newType(after);
        }
        ClassFileModel before = ClassFileModel.parse(previous);
        boolean bytesDiffer = !Arrays.equals(previous, current);
        return diff(before, after, bytesDiffer);
    }

    private static ClassDelta newType(ClassFileModel after) {
        List<MemberRef> addedMethods = new ArrayList<>();
        for (ClassFileModel.MethodModel method : after.methods) {
            addedMethods.add(new MemberRef(method.name, method.descriptor));
        }
        List<MemberRef> addedFields = new ArrayList<>();
        for (ClassFileModel.FieldModel field : after.fields) {
            addedFields.add(new MemberRef(field.name, field.descriptor));
        }
        return new ClassDelta(
                after.internalName,
                EnumSet.of(ChangeKind.NEW_TYPE),
                addedMethods,
                List.of(),
                List.of(),
                addedFields,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    private static ClassDelta diff(ClassFileModel before, ClassFileModel after, boolean bytesDiffer) {
        EnumSet<ChangeKind> kinds = EnumSet.noneOf(ChangeKind.class);

        boolean superclassChanged = !Objects.equals(before.superName, after.superName);
        if (superclassChanged) {
            kinds.add(ChangeKind.HIERARCHY_SUPER);
        }

        boolean interfacesChanged = !setEquals(before.interfaces, after.interfaces);
        if (interfacesChanged) {
            kinds.add(ChangeKind.HIERARCHY_IFACES);
        }

        if (before.access != after.access) {
            kinds.add(ChangeKind.CLASS_MODIFIERS);
        }

        boolean nestHostChanged = !Objects.equals(before.nestHost, after.nestHost);
        if (nestHostChanged || !setEquals(before.nestMembers, after.nestMembers)) {
            kinds.add(ChangeKind.NESTMATES);
        }

        boolean permittedSubclassesChanged = !setEquals(before.permittedSubclasses, after.permittedSubclasses);
        if (permittedSubclassesChanged) {
            kinds.add(ChangeKind.PERMITTED_SUBCLASSES);
        }

        boolean recordComponentsChanged = !before.recordComponents.equals(after.recordComponents);
        if (recordComponentsChanged) {
            kinds.add(ChangeKind.RECORD_COMPONENTS);
        }

        boolean enumConstantsChanged = !before.enumConstantNames().equals(after.enumConstantNames());
        if (enumConstantsChanged) {
            kinds.add(ChangeKind.ENUM_CONSTANTS);
        }

        if (!setEquals(before.innerClasses, after.innerClasses)) {
            kinds.add(ChangeKind.INNER_CLASSES_MAP);
        }

        if (!before.annotations.equals(after.annotations)) {
            kinds.add(ChangeKind.ANNOTATIONS);
        }

        List<MemberRef> addedMethods = new ArrayList<>();
        List<MemberRef> removedMethods = new ArrayList<>();
        List<MemberRef> bodyChangedMethods = new ArrayList<>();
        diffMethods(before, after, kinds, addedMethods, removedMethods, bodyChangedMethods);

        List<MemberRef> addedFields = new ArrayList<>();
        List<MemberRef> removedFields = new ArrayList<>();
        diffFields(before, after, kinds, addedFields, removedFields);

        boolean anonymousIndexShiftLikely = detectAnonymousIndexShift(before, after);

        if (kinds.isEmpty() && bytesDiffer) {
            kinds.add(ChangeKind.CONSTANT_POOL_ONLY);
        }

        return new ClassDelta(
                after.internalName,
                kinds,
                addedMethods,
                removedMethods,
                bodyChangedMethods,
                addedFields,
                removedFields,
                superclassChanged,
                interfacesChanged,
                nestHostChanged,
                permittedSubclassesChanged,
                recordComponentsChanged,
                enumConstantsChanged,
                anonymousIndexShiftLikely);
    }

    private static void diffMethods(
            ClassFileModel before,
            ClassFileModel after,
            EnumSet<ChangeKind> kinds,
            List<MemberRef> addedMethods,
            List<MemberRef> removedMethods,
            List<MemberRef> bodyChangedMethods) {
        Map<MemberRef, ClassFileModel.MethodModel> beforeMap = before.methodMap();
        Map<MemberRef, ClassFileModel.MethodModel> afterMap = after.methodMap();

        for (Map.Entry<MemberRef, ClassFileModel.MethodModel> entry : afterMap.entrySet()) {
            MemberRef ref = entry.getKey();
            ClassFileModel.MethodModel next = entry.getValue();
            ClassFileModel.MethodModel prev = beforeMap.get(ref);
            if (prev == null) {
                addedMethods.add(ref);
                kinds.add(next.isConstructor() ? ChangeKind.ADD_CONSTRUCTOR : ChangeKind.ADD_METHOD);
                continue;
            }
            if (next.bodyDiffers(prev)) {
                bodyChangedMethods.add(ref);
                kinds.add(ChangeKind.METHOD_BODY);
            }
            if (next.modifiersDiffer(prev)) {
                kinds.add(ChangeKind.CHANGE_METHOD_MODIFIERS);
            }
            if (next.annotationsDiffer(prev)) {
                kinds.add(ChangeKind.ANNOTATIONS);
            }
        }

        for (Map.Entry<MemberRef, ClassFileModel.MethodModel> entry : beforeMap.entrySet()) {
            if (!afterMap.containsKey(entry.getKey())) {
                removedMethods.add(entry.getKey());
                kinds.add(
                        entry.getValue().isConstructor()
                                ? ChangeKind.REMOVE_CONSTRUCTOR
                                : ChangeKind.REMOVE_METHOD);
            }
        }

        if (descriptorChanged(beforeMap.keySet(), afterMap.keySet())) {
            kinds.add(ChangeKind.CHANGE_METHOD_DESC);
        }
    }

    private static void diffFields(
            ClassFileModel before,
            ClassFileModel after,
            EnumSet<ChangeKind> kinds,
            List<MemberRef> addedFields,
            List<MemberRef> removedFields) {
        Map<MemberRef, ClassFileModel.FieldModel> beforeMap = before.fieldMap();
        Map<MemberRef, ClassFileModel.FieldModel> afterMap = after.fieldMap();

        for (Map.Entry<MemberRef, ClassFileModel.FieldModel> entry : afterMap.entrySet()) {
            MemberRef ref = entry.getKey();
            ClassFileModel.FieldModel next = entry.getValue();
            ClassFileModel.FieldModel prev = beforeMap.get(ref);
            if (prev == null) {
                addedFields.add(ref);
                kinds.add(ChangeKind.ADD_FIELD);
                continue;
            }
            if (next.modifiersDiffer(prev)) {
                kinds.add(ChangeKind.CHANGE_FIELD_MODIFIERS);
            }
            if (next.annotationsDiffer(prev)) {
                kinds.add(ChangeKind.ANNOTATIONS);
            }
        }

        for (MemberRef ref : beforeMap.keySet()) {
            if (!afterMap.containsKey(ref)) {
                removedFields.add(ref);
                kinds.add(ChangeKind.REMOVE_FIELD);
            }
        }

        if (descriptorChanged(beforeMap.keySet(), afterMap.keySet())) {
            kinds.add(ChangeKind.CHANGE_FIELD_DESC);
        }
    }

    /**
     * Same member name exists on both sides with disjoint descriptors — a rename of the type, not a
     * net add or remove of that name.
     */
    private static boolean descriptorChanged(Set<MemberRef> before, Set<MemberRef> after) {
        Map<String, Set<String>> beforeDescs = descriptorsByName(before);
        Map<String, Set<String>> afterDescs = descriptorsByName(after);
        for (Map.Entry<String, Set<String>> entry : beforeDescs.entrySet()) {
            Set<String> next = afterDescs.get(entry.getKey());
            if (next == null) {
                continue;
            }
            Set<String> onlyBefore = new LinkedHashSet<>(entry.getValue());
            onlyBefore.removeAll(next);
            Set<String> onlyAfter = new LinkedHashSet<>(next);
            onlyAfter.removeAll(entry.getValue());
            if (!onlyBefore.isEmpty() && !onlyAfter.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Set<String>> descriptorsByName(Set<MemberRef> refs) {
        Map<String, Set<String>> map = new java.util.LinkedHashMap<>();
        for (MemberRef ref : refs) {
            map.computeIfAbsent(ref.name, ignored -> new LinkedHashSet<>()).add(ref.descriptor);
        }
        return map;
    }

    /**
     * {@code Foo$1 → Foo$2} (or a lambda {@code $0 → $1}) is a remapping. Adding or dropping a
     * trailing {@code Foo$N+1} while {@code $1..$N} keep their names is not.
     */
    static boolean detectAnonymousIndexShift(ClassFileModel before, ClassFileModel after) {
        return remapsNumberedIndices(before.anonymousInnerIndices(), after.anonymousInnerIndices())
                || remapsNumberedIndices(before.lambdaMethodIndices(), after.lambdaMethodIndices())
                || remapsNumberedIndices(
                        before.bootstrapAnonymousIndices(), after.bootstrapAnonymousIndices());
    }

    static boolean remapsNumberedIndices(Collection<Integer> before, Collection<Integer> after) {
        Set<Integer> previous = new TreeSet<>(before);
        Set<Integer> current = new TreeSet<>(after);
        if (previous.equals(current)) {
            return false;
        }
        Set<Integer> removed = new TreeSet<>(previous);
        removed.removeAll(current);
        Set<Integer> added = new TreeSet<>(current);
        added.removeAll(previous);
        return !removed.isEmpty() && !added.isEmpty();
    }

    private static <T> boolean setEquals(Collection<T> left, Collection<T> right) {
        return Set.copyOf(left).equals(Set.copyOf(right));
    }
}

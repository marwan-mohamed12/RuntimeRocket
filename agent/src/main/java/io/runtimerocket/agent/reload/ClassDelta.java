package io.runtimerocket.agent.reload;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Structural difference between two versions of one class file. */
public final class ClassDelta {

    public final String internalName;
    public final EnumSet<ChangeKind> kinds;
    public final List<MemberRef> addedMethods;
    public final List<MemberRef> removedMethods;
    public final List<MemberRef> bodyChangedMethods;
    public final List<MemberRef> addedFields;
    public final List<MemberRef> removedFields;
    public final boolean superclassChanged;
    public final boolean interfacesChanged;
    public final boolean nestHostChanged;
    public final boolean permittedSubclassesChanged;
    public final boolean recordComponentsChanged;
    public final boolean enumConstantsChanged;
    public final boolean anonymousIndexShiftLikely;

    public ClassDelta(
            String internalName,
            EnumSet<ChangeKind> kinds,
            List<MemberRef> addedMethods,
            List<MemberRef> removedMethods,
            List<MemberRef> bodyChangedMethods,
            List<MemberRef> addedFields,
            List<MemberRef> removedFields,
            boolean superclassChanged,
            boolean interfacesChanged,
            boolean nestHostChanged,
            boolean permittedSubclassesChanged,
            boolean recordComponentsChanged,
            boolean enumConstantsChanged,
            boolean anonymousIndexShiftLikely) {
        this.internalName = Objects.requireNonNull(internalName, "internalName");
        this.kinds = copyKinds(kinds);
        this.addedMethods = List.copyOf(addedMethods);
        this.removedMethods = List.copyOf(removedMethods);
        this.bodyChangedMethods = List.copyOf(bodyChangedMethods);
        this.addedFields = List.copyOf(addedFields);
        this.removedFields = List.copyOf(removedFields);
        this.superclassChanged = superclassChanged;
        this.interfacesChanged = interfacesChanged;
        this.nestHostChanged = nestHostChanged;
        this.permittedSubclassesChanged = permittedSubclassesChanged;
        this.recordComponentsChanged = recordComponentsChanged;
        this.enumConstantsChanged = enumConstantsChanged;
        this.anonymousIndexShiftLikely = anonymousIndexShiftLikely;
    }

    private static EnumSet<ChangeKind> copyKinds(EnumSet<ChangeKind> kinds) {
        Objects.requireNonNull(kinds, "kinds");
        return kinds.isEmpty() ? EnumSet.noneOf(ChangeKind.class) : EnumSet.copyOf(kinds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClassDelta other)) {
            return false;
        }
        return internalName.equals(other.internalName)
                && kinds.equals(other.kinds)
                && addedMethods.equals(other.addedMethods)
                && removedMethods.equals(other.removedMethods)
                && bodyChangedMethods.equals(other.bodyChangedMethods)
                && addedFields.equals(other.addedFields)
                && removedFields.equals(other.removedFields)
                && superclassChanged == other.superclassChanged
                && interfacesChanged == other.interfacesChanged
                && nestHostChanged == other.nestHostChanged
                && permittedSubclassesChanged == other.permittedSubclassesChanged
                && recordComponentsChanged == other.recordComponentsChanged
                && enumConstantsChanged == other.enumConstantsChanged
                && anonymousIndexShiftLikely == other.anonymousIndexShiftLikely;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                internalName,
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

    @Override
    public String toString() {
        return "ClassDelta{name="
                + internalName
                + ", kinds="
                + kinds
                + ", addedMethods="
                + addedMethods
                + ", removedMethods="
                + removedMethods
                + ", bodyChangedMethods="
                + bodyChangedMethods
                + ", addedFields="
                + addedFields
                + ", removedFields="
                + removedFields
                + ", superclassChanged="
                + superclassChanged
                + ", interfacesChanged="
                + interfacesChanged
                + ", nestHostChanged="
                + nestHostChanged
                + ", permittedSubclassesChanged="
                + permittedSubclassesChanged
                + ", recordComponentsChanged="
                + recordComponentsChanged
                + ", enumConstantsChanged="
                + enumConstantsChanged
                + ", anonymousIndexShiftLikely="
                + anonymousIndexShiftLikely
                + '}';
    }
}

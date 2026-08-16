package io.runtimerocket.agent.spi;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/** Backend reload shapes that adapters may inspect. Hierarchy/enum/anonymous stay false in v1. */
public final class Capabilities {

    public final boolean methodBody;
    public final boolean addRemoveMethods;
    public final boolean addRemoveFields;
    public final boolean addConstructors;
    public final boolean hierarchyChanges;
    public final boolean enumConstants;
    public final boolean anonymousRemap;

    public Capabilities(
            boolean methodBody,
            boolean addRemoveMethods,
            boolean addRemoveFields,
            boolean addConstructors,
            boolean hierarchyChanges,
            boolean enumConstants,
            boolean anonymousRemap) {
        this.methodBody = methodBody;
        this.addRemoveMethods = addRemoveMethods;
        this.addRemoveFields = addRemoveFields;
        this.addConstructors = addConstructors;
        this.hierarchyChanges = hierarchyChanges;
        this.enumConstants = enumConstants;
        this.anonymousRemap = anonymousRemap;
    }

    public static Capabilities none() {
        return new Capabilities(false, false, false, false, false, false, false);
    }

    /**
     * Maps backend capability name tokens such as {@code METHOD_BODY} and {@code ADD_METHOD}.
     * {@code hierarchyChanges}, {@code enumConstants}, and {@code anonymousRemap} stay false.
     */
    public static Capabilities fromNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return none();
        }
        boolean methodBody = false;
        boolean addRemoveMethods = false;
        boolean addRemoveFields = false;
        boolean addConstructors = false;
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim().toUpperCase(Locale.ROOT);
            switch (name) {
                case "METHOD_BODY" -> methodBody = true;
                case "ADD_METHOD", "REMOVE_METHOD" -> addRemoveMethods = true;
                case "ADD_FIELD", "REMOVE_FIELD" -> addRemoveFields = true;
                case "ADD_CONSTRUCTOR", "REMOVE_CONSTRUCTOR" -> addConstructors = true;
                default -> {
                    // CONSTANT_POOL_ONLY, NEW_TYPE, and other tokens have no Capabilities field
                }
            }
        }
        return new Capabilities(
                methodBody, addRemoveMethods, addRemoveFields, addConstructors, false, false, false);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Capabilities that)) {
            return false;
        }
        return methodBody == that.methodBody
                && addRemoveMethods == that.addRemoveMethods
                && addRemoveFields == that.addRemoveFields
                && addConstructors == that.addConstructors
                && hierarchyChanges == that.hierarchyChanges
                && enumConstants == that.enumConstants
                && anonymousRemap == that.anonymousRemap;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                methodBody,
                addRemoveMethods,
                addRemoveFields,
                addConstructors,
                hierarchyChanges,
                enumConstants,
                anonymousRemap);
    }

    @Override
    public String toString() {
        return "Capabilities{methodBody="
                + methodBody
                + ", addRemoveMethods="
                + addRemoveMethods
                + ", addRemoveFields="
                + addRemoveFields
                + ", addConstructors="
                + addConstructors
                + ", hierarchyChanges="
                + hierarchyChanges
                + ", enumConstants="
                + enumConstants
                + ", anonymousRemap="
                + anonymousRemap
                + '}';
    }
}

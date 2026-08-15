package io.runtimerocket.agent.reload;

/** Structural or bytecode change detected between two versions of a class file. */
public enum ChangeKind {
    NEW_TYPE,
    METHOD_BODY,
    ADD_METHOD,
    REMOVE_METHOD,
    CHANGE_METHOD_DESC,
    CHANGE_METHOD_MODIFIERS,
    ADD_FIELD,
    REMOVE_FIELD,
    CHANGE_FIELD_DESC,
    CHANGE_FIELD_MODIFIERS,
    ADD_CONSTRUCTOR,
    REMOVE_CONSTRUCTOR,
    HIERARCHY_SUPER,
    HIERARCHY_IFACES,
    CLASS_MODIFIERS,
    ANNOTATIONS,
    ENUM_CONSTANTS,
    RECORD_COMPONENTS,
    NESTMATES,
    PERMITTED_SUBCLASSES,
    INNER_CLASSES_MAP,
    CONSTANT_POOL_ONLY
}

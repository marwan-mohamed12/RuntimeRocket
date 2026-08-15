package io.runtimerocket.agent.reload;

/** One already-loaded type to pass to {@code Instrumentation.redefineClasses}. */
public final class Redefinition {

    public final Class<?> loaded;
    public final byte[] bytes;
    public final ClassDelta delta;

    public Redefinition(Class<?> loaded, byte[] bytes, ClassDelta delta) {
        this.loaded = loaded;
        this.bytes = bytes;
        this.delta = delta;
    }
}

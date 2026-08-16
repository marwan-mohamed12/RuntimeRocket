package io.runtimerocket.agent.reload;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Defines a helper class in an application loader via reflected {@code ClassLoader.defineClass}.
 * Transformers still skip {@code io.runtimerocket.**}; helpers are not retransformed.
 */
public final class ClassInjector {

    private static final String AGENT_PACKAGE = "io.runtimerocket.agent";

    private final ClassLoader loader;

    private ClassInjector(ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public static ClassInjector into(ClassLoader appLoader) {
        return new ClassInjector(appLoader);
    }

    /** Same rule as {@link ClassIndex}: agent and helper packages are never retransformed. */
    public static boolean skip(String binaryName) {
        return ClassIndex.skipBinary(binaryName);
    }

    public Class<?> inject(String binaryName, byte[] bytes) {
        Objects.requireNonNull(binaryName, "binaryName");
        Objects.requireNonNull(bytes, "bytes");
        if (skipAgentType(binaryName)) {
            throw new IllegalArgumentException("refusing to inject agent type: " + binaryName);
        }
        Class<?> existing = findLoaded(binaryName);
        if (existing != null) {
            return existing;
        }
        try {
            Method define =
                    ClassLoader.class.getDeclaredMethod(
                            "defineClass", String.class, byte[].class, int.class, int.class);
            define.setAccessible(true);
            return (Class<?>) define.invoke(loader, binaryName, bytes, 0, bytes.length);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof LinkageError) {
                Class<?> loaded = findLoaded(binaryName);
                if (loaded != null) {
                    return loaded;
                }
            }
            throw new IllegalStateException(
                    "defineClass failed for " + binaryName + ": " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("defineClass failed for " + binaryName + ": " + e.getMessage(), e);
        }
    }

    static boolean skipAgentType(String binaryName) {
        return AGENT_PACKAGE.equals(binaryName) || binaryName.startsWith(AGENT_PACKAGE + ".");
    }

    private Class<?> findLoaded(String binaryName) {
        try {
            Method find = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            find.setAccessible(true);
            return (Class<?>) find.invoke(loader, binaryName);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}

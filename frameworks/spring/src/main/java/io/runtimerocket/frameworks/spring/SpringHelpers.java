package io.runtimerocket.frameworks.spring;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/** Defines helper classes in an application loader. Avoids referencing helper types from this loader. */
final class SpringHelpers {

    private SpringHelpers() {}

    static Class<?> inject(ClassLoader appLoader, String binaryName) {
        Objects.requireNonNull(binaryName, "binaryName");
        ClassLoader loader = appLoader == null ? SpringHelpers.class.getClassLoader() : appLoader;
        Class<?> existing = findLoaded(loader, binaryName);
        if (existing != null) {
            return existing;
        }
        byte[] bytes = readBytes(binaryName);
        try {
            Method define =
                    ClassLoader.class.getDeclaredMethod(
                            "defineClass", String.class, byte[].class, int.class, int.class);
            define.setAccessible(true);
            return (Class<?>) define.invoke(loader, binaryName, bytes, 0, bytes.length);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof LinkageError) {
                Class<?> loaded = findLoaded(loader, binaryName);
                if (loaded != null) {
                    return loaded;
                }
                try {
                    return Class.forName(binaryName, false, loader);
                } catch (ClassNotFoundException ignored) {
                    // fall through
                }
            }
            throw new IllegalStateException(
                    "defineClass failed for " + binaryName + ": " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("defineClass failed for " + binaryName + ": " + e.getMessage(), e);
        }
    }

    static byte[] readBytes(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        ClassLoader self = SpringHelpers.class.getClassLoader();
        try (InputStream in =
                self != null ? self.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing helper " + binaryName);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read helper " + binaryName, e);
        }
    }

    private static Class<?> findLoaded(ClassLoader loader, String binaryName) {
        try {
            Method find = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            find.setAccessible(true);
            return (Class<?>) find.invoke(loader, binaryName);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}

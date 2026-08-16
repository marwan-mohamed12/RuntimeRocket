package io.runtimerocket.frameworks.spring;

/**
 * Picks the helper class by Spring Framework major version. Main helpers cover 6.x; fw7 helpers
 * are loaded by name when {@code SpringVersion} is 7+.
 */
public final class SpringVersionGate {

    public static final String MAIN_HELPER = "io.runtimerocket.frameworks.spring.internal.SpringRefreshHelper";
    public static final String FW7_HELPER = "io.runtimerocket.frameworks.spring.fw7.Fw7SpringRefreshHelper";

    private SpringVersionGate() {}

    public static String helperClassName(ClassLoader loader) {
        return frameworkMajor(loader) >= 7 ? FW7_HELPER : MAIN_HELPER;
    }

    public static int frameworkMajor(ClassLoader loader) {
        String version = frameworkVersion(loader);
        if (version == null || version.isBlank()) {
            return 0;
        }
        int dot = version.indexOf('.');
        String major = dot < 0 ? version : version.substring(0, dot);
        int dash = major.indexOf('-');
        if (dash > 0) {
            major = major.substring(0, dash);
        }
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String frameworkVersion(ClassLoader loader) {
        Class<?> type = load(loader, "org.springframework.core.SpringVersion");
        if (type == null) {
            return null;
        }
        try {
            Object version = type.getMethod("getVersion").invoke(null);
            return version == null ? null : version.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static String bootVersion(ClassLoader loader) {
        Class<?> type = load(loader, "org.springframework.boot.SpringBootVersion");
        if (type == null) {
            return null;
        }
        try {
            Object version = type.getMethod("getVersion").invoke(null);
            return version == null ? null : version.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader == null ? SpringVersionGate.class.getClassLoader() : loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}

package io.runtimerocket.frameworks.spring;

/** Reflection-only probes for DevTools, WebFlux, and AOT. */
final class SpringEnvironment {

    private SpringEnvironment() {}

    static boolean springPresent(ClassLoader loader) {
        return load(loader, "org.springframework.context.ApplicationContext") != null;
    }

    /**
     * Restarter on the loader plus restart not explicitly disabled. Consults the sysprop/env first,
     * then a live context {@code Environment} if one is already tracked.
     */
    static boolean devToolsActive(ClassLoader loader) {
        if (load(loader, "org.springframework.boot.devtools.restart.Restarter") == null) {
            return false;
        }
        String prop = firstNonBlank(
                System.getProperty("spring.devtools.restart.enabled"),
                System.getenv("SPRING_DEVTOOLS_RESTART_ENABLED"));
        if (prop != null) {
            return !"false".equalsIgnoreCase(prop);
        }
        for (Object context : SpringContextTracker.liveContexts()) {
            String fromEnv = environmentProperty(context, "spring.devtools.restart.enabled");
            if ("false".equalsIgnoreCase(fromEnv)) {
                return false;
            }
        }
        return true;
    }

    static boolean webFluxOrAot(ClassLoader[] loaders, Iterable<Object> contexts) {
        if (aotEnabled(loaders)) {
            return true;
        }
        if (reactivePropertySet()) {
            return true;
        }
        if (contexts == null) {
            return false;
        }
        for (Object context : contexts) {
            if (isReactiveContext(context)
                    || "reactive".equalsIgnoreCase(environmentProperty(context, "spring.main.web-application-type"))) {
                return true;
            }
        }
        return false;
    }

    static boolean aotEnabled(ClassLoader[] loaders) {
        if (Boolean.getBoolean("spring.aot.enabled")) {
            return true;
        }
        ClassLoader[] search = loaders == null || loaders.length == 0
                ? new ClassLoader[] {SpringEnvironment.class.getClassLoader()}
                : loaders;
        for (ClassLoader loader : search) {
            Class<?> detector = load(loader, "org.springframework.aot.AotDetector");
            if (detector == null) {
                continue;
            }
            try {
                Object used = detector.getMethod("useGeneratedArtifacts").invoke(null);
                if (Boolean.TRUE.equals(used)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // keep looking
            }
        }
        return false;
    }

    static boolean isReactiveContext(Object context) {
        if (context == null) {
            return false;
        }
        for (Class<?> type = context.getClass(); type != null; type = type.getSuperclass()) {
            if (isReactiveTypeName(type.getName()) || hasReactiveInterface(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReactiveInterface(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if (isReactiveTypeName(iface.getName()) || hasReactiveInterface(iface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReactiveTypeName(String name) {
        return "org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext".equals(name)
                || "org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext"
                        .equals(name)
                || "org.springframework.web.reactive.context.ConfigurableReactiveWebApplicationContext".equals(name)
                || "org.springframework.web.reactive.context.ReactiveWebApplicationContext".equals(name);
    }

    private static boolean reactivePropertySet() {
        if ("reactive".equalsIgnoreCase(System.getProperty("spring.main.web-application-type"))) {
            return true;
        }
        String env = firstNonBlank(
                System.getenv("SPRING_MAIN_WEBAPPLICATIONTYPE"), System.getenv("SPRING_MAIN_WEB_APPLICATION_TYPE"));
        return "reactive".equalsIgnoreCase(env);
    }

    static String environmentProperty(Object context, String key) {
        if (context == null || key == null) {
            return null;
        }
        try {
            Object env = context.getClass().getMethod("getEnvironment").invoke(context);
            if (env == null) {
                return null;
            }
            Object value = env.getClass().getMethod("getProperty", String.class).invoke(env, key);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader == null ? SpringEnvironment.class.getClassLoader() : loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}

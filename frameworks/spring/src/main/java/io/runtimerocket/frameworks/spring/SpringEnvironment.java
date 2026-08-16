package io.runtimerocket.frameworks.spring;

import java.lang.instrument.Instrumentation;

/** Reflection-only probes for DevTools, WebFlux, and AOT. */
final class SpringEnvironment {

    private SpringEnvironment() {}

    static boolean springPresent(ClassLoader loader) {
        return load(loader, "org.springframework.context.ApplicationContext") != null;
    }

    static boolean devToolsActive(ClassLoader loader) {
        if (load(loader, "org.springframework.boot.devtools.restart.Restarter") == null) {
            return false;
        }
        String prop = System.getProperty("spring.devtools.restart.enabled");
        if (prop == null || prop.isBlank()) {
            prop = System.getenv("SPRING_DEVTOOLS_RESTART_ENABLED");
        }
        return prop == null || !"false".equalsIgnoreCase(prop.trim());
    }

    static boolean webFluxOrAot(ClassLoader[] loaders, Instrumentation inst) {
        if (aotEnabled(loaders)) {
            return true;
        }
        if ("reactive".equalsIgnoreCase(System.getProperty("spring.main.web-application-type"))) {
            return true;
        }
        String env = System.getenv("SPRING_MAIN_WEBAPPLICATIONTYPE");
        if (env == null) {
            env = System.getenv("SPRING_MAIN_WEB_APPLICATION_TYPE");
        }
        if ("reactive".equalsIgnoreCase(env)) {
            return true;
        }
        return reactiveContextLoaded(loaders, inst);
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

    private static boolean reactiveContextLoaded(ClassLoader[] loaders, Instrumentation inst) {
        String[] names = {
            "org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext",
            "org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext",
            "org.springframework.web.reactive.HandlerMapping"
        };
        if (inst != null) {
            for (Class<?> loaded : inst.getAllLoadedClasses()) {
                String name = loaded.getName();
                for (String expected : names) {
                    if (expected.equals(name) && isLiveInstance(inst, expected)) {
                        return !expected.endsWith("HandlerMapping") || hasReactiveWebTypeProperty();
                    }
                }
            }
        }
        if (loaders != null) {
            for (ClassLoader loader : loaders) {
                if (load(loader, "org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext")
                        != null) {
                    return hasReactiveWebTypeProperty();
                }
            }
        }
        return false;
    }

    private static boolean hasReactiveWebTypeProperty() {
        return "reactive".equalsIgnoreCase(System.getProperty("spring.main.web-application-type"));
    }

    private static boolean isLiveInstance(Instrumentation inst, String typeName) {
        if (inst == null) {
            return false;
        }
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (typeName.equals(loaded.getName())) {
                return true;
            }
        }
        return false;
    }

    static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader == null ? SpringEnvironment.class.getClassLoader() : loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}

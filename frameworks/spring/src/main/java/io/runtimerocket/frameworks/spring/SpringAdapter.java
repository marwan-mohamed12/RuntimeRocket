package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.AdapterContext;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.FrameworkAdapter;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Boot adapter: tracks live contexts, registers new stereotype beans, keeps existing
 * singletons, rebuilds controller mappings, recreates proxies best-effort, and reports idle
 * late-attach as PARTIAL.
 */
public final class SpringAdapter implements FrameworkAdapter {

    public static final String ID = "spring";
    public static final int ORDER = 100;
    public static final String INACTIVE_DETAIL =
            "Spring adapter inactive until a request hits the app or you restart with -javaagent (premain).";
    public static final String WEBFLUX_AOT_DETAIL = "WebFlux/AOT not supported";
    public static final String CONFIG_CHANGED_DETAIL = "config changed — restart to apply";
    public static final String STATIC_DETAIL = "static-on-disk";
    public static final String MAPPING_STALE_DETAIL = "controller mappings may be stale — restart";
    public static final String PROXY_STALE_DETAIL = "proxy stale — restart";

    private final SpringHookTransformer transformer = new SpringHookTransformer();
    private final AtomicBoolean installed = new AtomicBoolean(false);
    private final AtomicBoolean devToolsLogged = new AtomicBoolean(false);
    private volatile Instrumentation instrumentation;
    private volatile boolean refuse;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean isAvailable(ClassLoader appLoader) {
        if (!SpringEnvironment.springPresent(appLoader)) {
            return false;
        }
        return !refuse;
    }

    @Override
    public void onAgentStart(AdapterContext ctx) {
        if (ctx != null && devToolsOn(ctx)) {
            refuse = true;
            ctx.log("error", "Spring DevTools restart is enabled; Spring adapter will not start");
            return;
        }
        Instrumentation inst = peekInst(ctx);
        if (inst != null) {
            this.instrumentation = inst;
            installTransformer(inst);
            retransformSpring(inst, ctx);
        }
        SpringContextTracker.setListener(registered -> {
            if (ctx != null) {
                ctx.log("info", "Spring ApplicationContext registered");
            }
        });
    }

    @Override
    public AdapterOutcome onLateAttach(AdapterContext ctx) {
        if (ctx != null && devToolsOn(ctx)) {
            refuse = true;
            ctx.log("error", "Spring DevTools restart is enabled; Spring adapter will not start");
            return new AdapterOutcome(ID, AdapterOutcome.FAILED, 0L, "Spring DevTools restart is enabled");
        }
        Instrumentation inst = peekInst(ctx);
        if (inst != null) {
            this.instrumentation = inst;
            installTransformer(inst);
            retransformSpring(inst, ctx);
        }
        ClassLoader[] loaders = ctx == null ? new ClassLoader[0] : ctx.applicationLoaders();
        for (Object discovered : SpringContextFinder.probe(loaders, inst)) {
            SpringContextTracker.register(discovered);
        }
        if (SpringEnvironment.webFluxOrAot(loaders, SpringContextTracker.liveContexts())) {
            return new AdapterOutcome(ID, AdapterOutcome.PARTIAL, 0L, WEBFLUX_AOT_DETAIL);
        }
        if (SpringContextTracker.isEmpty()) {
            return new AdapterOutcome(ID, AdapterOutcome.PARTIAL, 0L, INACTIVE_DETAIL);
        }
        return AdapterOutcome.ok(ID);
    }

    @Override
    public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
        List<ReloadedClass> classes = event == null ? List.of() : event.classes;
        List<Object> contexts = SpringContextTracker.liveContexts();
        if (contexts.isEmpty()) {
            if (anySpringBean(classes)) {
                return new AdapterOutcome(ID, AdapterOutcome.PARTIAL, 0L, INACTIVE_DETAIL);
            }
            return AdapterOutcome.ok(ID);
        }
        try {
            String status = AdapterOutcome.SUCCESS;
            String detail = null;
            for (ReloadedClass reloaded : classes) {
                if (reloaded == null || reloaded.type == null || skip(reloaded.binaryName)) {
                    continue;
                }
                ClassLoader loader = reloaded.type.getClassLoader();
                String helperName = SpringVersionGate.helperClassName(loader);
                Class<?> helper = SpringHelpers.inject(loader, helperName);
                for (Object context : contexts) {
                    helper.getMethod("refresh", Object.class, Class.class, List.class)
                            .invoke(null, context, reloaded.type, reloaded.changeKinds);
                    String mapping = SpringRequestMappings.rebuild(context, reloaded.type);
                    if (mapping != null) {
                        status = AdapterOutcome.FAILED;
                        detail = mapping;
                    }
                    String proxy = SpringProxies.recreate(context, reloaded.type);
                    if (proxy != null && !AdapterOutcome.FAILED.equals(status)) {
                        status = AdapterOutcome.PARTIAL;
                        detail = proxy;
                    }
                }
            }
            if (!AdapterOutcome.SUCCESS.equals(status)) {
                return new AdapterOutcome(ID, status, 0L, detail);
            }
            return AdapterOutcome.ok(ID);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            return new AdapterOutcome(ID, AdapterOutcome.FAILED, 0L, detail);
        }
    }

    @Override
    public AdapterOutcome onResourcesChanged(ResourceChangeEvent event) {
        if (event == null || event.resources == null || event.resources.isEmpty()) {
            return AdapterOutcome.ok(ID);
        }
        boolean allStatic = true;
        for (ResourceChangeEvent.ChangedResource resource : event.resources) {
            if (SpringResourcePolicy.isBootConfig(resource)) {
                return new AdapterOutcome(ID, AdapterOutcome.RESTART_REQUIRED, 0L, CONFIG_CHANGED_DETAIL);
            }
            if (!SpringResourcePolicy.isStatic(resource)) {
                allStatic = false;
            }
        }
        if (allStatic) {
            return new AdapterOutcome(ID, AdapterOutcome.SUCCESS, 0L, STATIC_DETAIL);
        }
        return AdapterOutcome.ok(ID);
    }

    @Override
    public void onAgentShutdown() {
        Instrumentation inst = instrumentation;
        if (inst != null && installed.compareAndSet(true, false)) {
            inst.removeTransformer(transformer);
            restoreSpring(inst);
        }
        SpringContextTracker.reset();
        refuse = false;
        devToolsLogged.set(false);
    }

    private void installTransformer(Instrumentation inst) {
        if (inst == null || !installed.compareAndSet(false, true)) {
            return;
        }
        inst.addTransformer(transformer, true);
    }

    private void retransformSpring(Instrumentation inst, AdapterContext ctx) {
        List<Class<?>> targets = springTypes(inst);
        if (targets.isEmpty()) {
            return;
        }
        try {
            inst.retransformClasses(targets.toArray(Class<?>[]::new));
        } catch (UnmodifiableClassException | RuntimeException e) {
            if (ctx != null) {
                ctx.log("warn", "Spring retransform failed: " + e.getMessage());
            }
        }
    }

    private void restoreSpring(Instrumentation inst) {
        List<Class<?>> targets = springTypes(inst);
        if (targets.isEmpty()) {
            return;
        }
        try {
            inst.retransformClasses(targets.toArray(Class<?>[]::new));
        } catch (UnmodifiableClassException | RuntimeException ignored) {
            // process is exiting or the types are no longer modifiable
        }
    }

    private static List<Class<?>> springTypes(Instrumentation inst) {
        List<Class<?>> targets = new ArrayList<>();
        if (inst == null || !inst.isRetransformClassesSupported()) {
            return targets;
        }
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            String name = loaded.getName();
            if (name.startsWith("io.runtimerocket.")) {
                continue;
            }
            if ("org.springframework.context.support.AbstractApplicationContext".equals(name)) {
                if (inst.isModifiableClass(loaded)) {
                    targets.add(loaded);
                }
            }
        }
        return targets;
    }

    private boolean devToolsOn(AdapterContext ctx) {
        ClassLoader[] loaders = ctx.applicationLoaders();
        if (loaders == null) {
            return false;
        }
        for (ClassLoader loader : loaders) {
            if (SpringEnvironment.devToolsActive(loader)) {
                if (devToolsLogged.compareAndSet(false, true)) {
                    ctx.log("error", "refusing Spring adapter; spring.devtools.restart.enabled is not false");
                }
                return true;
            }
        }
        return false;
    }

    private static Instrumentation peekInst(AdapterContext ctx) {
        if (ctx == null) {
            return null;
        }
        try {
            return ctx.peekService(Instrumentation.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean anySpringBean(List<ReloadedClass> classes) {
        for (ReloadedClass reloaded : classes) {
            if (reloaded != null && reloaded.type != null && looksLikeSpringBean(reloaded.type)) {
                return true;
            }
        }
        return false;
    }

    static boolean looksLikeSpringBean(Class<?> type) {
        if (type == null) {
            return false;
        }
        for (java.lang.annotation.Annotation annotation : type.getAnnotations()) {
            if (isStereotypeName(annotation.annotationType().getName())) {
                return true;
            }
            for (java.lang.annotation.Annotation meta : annotation.annotationType().getAnnotations()) {
                if (isStereotypeName(meta.annotationType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStereotypeName(String name) {
        return "org.springframework.stereotype.Component".equals(name)
                || "org.springframework.stereotype.Service".equals(name)
                || "org.springframework.stereotype.Repository".equals(name)
                || "org.springframework.stereotype.Controller".equals(name)
                || "org.springframework.web.bind.annotation.RestController".equals(name)
                || "org.springframework.context.annotation.Configuration".equals(name);
    }

    private static boolean skip(String binaryName) {
        if (binaryName == null) {
            return false;
        }
        // fixture types live under frameworks.spring.testapp and must be refreshed
        if (binaryName.contains(".testapp.")) {
            return false;
        }
        return binaryName.startsWith("io.runtimerocket.agent.")
                || binaryName.startsWith("io.runtimerocket.frameworks.")
                || binaryName.startsWith("io.runtimerocket.protocol.");
    }
}

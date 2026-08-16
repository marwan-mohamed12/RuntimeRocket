package io.runtimerocket.frameworks.spring;

import demo.rr.spring.ChangedController;
import demo.rr.spring.HelloController;
import demo.rr.spring.MappingConfig;
import io.runtimerocket.agent.spi.Capabilities;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-spring", mode = ResourceAccessMode.READ_WRITE)
class SpringRequestMappingTest {

    private final SpringAdapter adapter = new SpringAdapter();
    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        adapter.onAgentShutdown();
        SpringContextTracker.reset();
    }

    @Test
    void addedGetMappingIsVisibleOnLiveHandlerMapping() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        context = new AnnotationConfigApplicationContext();
        context.register(MappingConfig.class, HelloController.class);
        context.refresh();
        RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
        assertTrue(paths(mapping).contains("/hello"), paths(mapping).toString());
        assertFalse(paths(mapping).contains("/added"));

        String name = SpringTestSupport.unique("AddedController");
        Class<?> added = SpringTestSupport.defineController(context.getClassLoader(), name, "/added");
        AdapterOutcome outcome = adapter.onClassesReloaded(reload(name, added, "NEW_TYPE"));
        assertEquals(AdapterOutcome.SUCCESS, outcome.status, outcome.detail);

        Set<String> live = paths(mapping);
        assertTrue(live.contains("/hello"), live.toString());
        assertTrue(live.contains("/added"), "lookup must use live RequestMappingHandlerMapping: " + live);
        assertTrue(handlerMethodExists(mapping, "/added"));
    }

    @Test
    void changedGetMappingIsRebuiltOnLiveHandlerMapping() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        context = new AnnotationConfigApplicationContext();
        context.register(MappingConfig.class, ChangedController.class);
        context.refresh();
        RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
        assertTrue(paths(mapping).contains("/hello"), paths(mapping).toString());
        assertTrue(paths(mapping).contains("/changed"), paths(mapping).toString());

        RequestMappingInfo stale = mappingInfo(mapping, "/changed");
        mapping.unregisterMapping(stale);
        assertFalse(paths(mapping).contains("/changed"), paths(mapping).toString());
        SpringContextTracker.register(context);

        AdapterOutcome outcome =
                adapter.onClassesReloaded(reload(ChangedController.class.getName(), ChangedController.class, "ADD_METHOD"));
        assertEquals(AdapterOutcome.SUCCESS, outcome.status, outcome.detail);

        Set<String> live = paths(mapping);
        assertTrue(live.contains("/hello"), live.toString());
        assertTrue(live.contains("/changed"), "rebuilt mapping must be visible on live handler mapping: " + live);
        assertTrue(handlerMethodExists(mapping, "/changed"));
    }

    @Test
    void mappingReflectionFailureReturnsFailedWithoutThrowing() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        BoomMapping mapping = new BoomMapping();
        context = new AnnotationConfigApplicationContext();
        context.registerBean("requestMappingHandlerMapping", BoomMapping.class, () -> mapping);
        context.register(HelloController.class);
        context.refresh();
        mapping.fail = true;
        SpringContextTracker.register(context);
        assertTrue(paths(mapping).contains("/hello"), paths(mapping).toString());

        AdapterOutcome outcome =
                adapter.onClassesReloaded(reload(HelloController.class.getName(), HelloController.class, "BODY"));
        assertEquals(AdapterOutcome.FAILED, outcome.status, outcome.detail);
        assertEquals(SpringAdapter.MAPPING_STALE_DETAIL, outcome.detail);
        assertTrue(paths(mapping).contains("/hello"), "failed rebuild must restore live mappings: " + paths(mapping));
    }

    @Test
    void missingDetectMethodOnDummyMappingIsFailedNotSilent() {
        DummyMapping dummy = new DummyMapping();
        String detail = SpringRequestMappings.rebuild(new MappingOnlyContext(dummy), HelloController.class);
        assertEquals(SpringAdapter.MAPPING_STALE_DETAIL, detail);
    }

    @Test
    void framework62KeepsDetectHandlerMethodsName() {
        assertEquals(
                "detectHandlerMethods",
                SpringRequestMappings.detectMethodName(RequestMappingHandlerMapping.class));
        assertFalse(SpringRequestMappings.webFluxMappingPresent(getClass().getClassLoader()));
    }

    private static ClassReloadEvent reload(String name, Class<?> type, String kind) {
        return new ClassReloadEvent(
                SpringTestSupport.context(false),
                List.of(new ReloadedClass(name, type, List.of(kind))),
                "standard",
                Capabilities.none());
    }

    static Set<String> paths(RequestMappingHandlerMapping mapping) {
        Set<String> out = new HashSet<>();
        mapping.getHandlerMethods().forEach((info, method) -> out.addAll(info.getPatternValues()));
        return out;
    }

    static boolean handlerMethodExists(RequestMappingHandlerMapping mapping, String path) {
        return mapping.getHandlerMethods().entrySet().stream()
                .anyMatch(entry -> entry.getKey().getPatternValues().contains(path) && entry.getValue() != null);
    }

    private static RequestMappingInfo mappingInfo(RequestMappingHandlerMapping mapping, String path) {
        return mapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().contains(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing mapping " + path));
    }

    static final class BoomMapping extends RequestMappingHandlerMapping {
        volatile boolean fail;

        @Override
        protected void detectHandlerMethods(Object handler) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            super.detectHandlerMethods(handler);
        }
    }

    static final class DummyMapping {
        public java.util.Map<Object, Object> getHandlerMethods() {
            return java.util.Map.of();
        }

        public void unregisterMapping(Object mapping) {}

        public void registerMapping(Object mapping, Object handler, java.lang.reflect.Method method) {}
    }

    private static final class MappingOnlyContext {
        private final DummyMapping mapping;

        MappingOnlyContext(DummyMapping mapping) {
            this.mapping = mapping;
        }

        @SuppressWarnings("unused")
        public boolean isActive() {
            return true;
        }

        @SuppressWarnings("unused")
        public java.util.Map<String, Object> getBeansOfType(Class<?> type) {
            if (type == DummyMapping.class
                    || RequestMappingHandlerMapping.class.getName().equals(type.getName())
                    || type.isInstance(mapping)) {
                return java.util.Map.of("requestMappingHandlerMapping", mapping);
            }
            if (type == HelloController.class) {
                return java.util.Map.of("helloController", new HelloController());
            }
            return java.util.Map.of();
        }
    }
}

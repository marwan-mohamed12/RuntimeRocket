package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.Capabilities;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.frameworks.spring.internal.SpringRefreshHelper;
import io.runtimerocket.frameworks.spring.testapp.AddedBean;
import io.runtimerocket.frameworks.spring.testapp.ConfigWithExtraBean;
import io.runtimerocket.frameworks.spring.testapp.ExistingBean;
import io.runtimerocket.frameworks.spring.testapp.TestConfig;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-spring", mode = ResourceAccessMode.READ_WRITE)
class SpringBeanReloadTest {

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
    void addServiceWhileRunningRegistersAndKeepsExistingSingleton() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();
        assertFalse(SpringContextTracker.isEmpty(), "finishRefresh hook must register the context");

        ExistingBean before = context.getBean(ExistingBean.class);
        String name = SpringTestSupport.unique("AddedService");
        Class<?> added = SpringTestSupport.defineService(context.getClassLoader(), name);
        ClassReloadEvent event = new ClassReloadEvent(
                SpringTestSupport.context(false),
                List.of(new ReloadedClass(name, added, List.of("NEW_TYPE"))),
                "standard",
                Capabilities.none());

        AdapterOutcome outcome = adapter.onClassesReloaded(event);
        assertEquals(AdapterOutcome.SUCCESS, outcome.status, outcome.detail);
        Object bean = context.getBean(added);
        assertNotNull(bean);
        assertEquals("ok", invokePing(bean));
        assertSame(before, context.getBean(ExistingBean.class));
    }

    @Test
    void factoryOnlyRegistrationDoesNotSucceedStereotypeReload() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        SpringContextTracker.register(factory);
        assertTrue(SpringContextTracker.isEmpty());

        String name = SpringTestSupport.unique("OrphanService");
        Class<?> added = SpringTestSupport.defineService(getClass().getClassLoader(), name);
        AdapterOutcome outcome = adapter.onClassesReloaded(new ClassReloadEvent(
                SpringTestSupport.context(false),
                List.of(new ReloadedClass(name, added, List.of("NEW_TYPE"))),
                "standard",
                Capabilities.none()));
        assertEquals(AdapterOutcome.PARTIAL, outcome.status);
        assertEquals(SpringAdapter.INACTIVE_DETAIL, outcome.detail);
    }

    @Test
    void newBeanFactoryMethodIsInvokedOnExistingConfiguration() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        try {
            ConfigWithExtraBean config = new ConfigWithExtraBean();
            ExistingBean existing = config.existingBean();
            ctx.getDefaultListableBeanFactory().registerSingleton("configWithExtraBean", config);
            ctx.getDefaultListableBeanFactory().registerSingleton("existingBean", existing);
            ctx.refresh();

            SpringRefreshHelper.refresh(ctx, ConfigWithExtraBean.class, List.of("ADD_METHOD"));

            assertSame(existing, ctx.getBean(ExistingBean.class));
            assertEquals("added", ctx.getBean(AddedBean.class).id());
        } finally {
            ctx.close();
        }
    }

    private static String invokePing(Object bean) {
        try {
            return (String) bean.getClass().getMethod("ping").invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

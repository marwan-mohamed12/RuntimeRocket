package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.Capabilities;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.ReloadedClass;
import io.runtimerocket.frameworks.spring.testapp.ForcedProxyService;
import io.runtimerocket.frameworks.spring.testapp.ProxiedTarget;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-spring", mode = ResourceAccessMode.READ_WRITE)
class SpringProxyRecreateTest {

    private final SpringAdapter adapter = new SpringAdapter();
    private GenericApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        adapter.onAgentShutdown();
        SpringContextTracker.reset();
    }

    @Test
    void proxyRecreateKeepsTargetIdentity() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        ProxiedTarget target = new ProxiedTarget();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodBeforeAdvice) (method, args, source) -> {});
        Object proxy = factory.getProxy();
        assertTrue(AopUtils.isAopProxy(proxy));

        context = new GenericApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton("proxiedTarget", proxy);
        context.refresh();
        SpringContextTracker.register(context);

        AdapterOutcome outcome =
                adapter.onClassesReloaded(reload(ProxiedTarget.class.getName(), ProxiedTarget.class, "BODY"));
        assertEquals(AdapterOutcome.SUCCESS, outcome.status, outcome.detail);

        Object after = context.getBean("proxiedTarget");
        assertTrue(AopUtils.isAopProxy(after));
        assertSame(target, AopProxyUtils.getSingletonTarget(after));
        assertSame(target, AopProxyUtils.getSingletonTarget(proxy));
        assertEquals("target", ((ProxiedTarget) after).id());
    }

    @Test
    void forcedProxyFailureReturnsPartialStaleString() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        ForcedProxyService stale =
                (ForcedProxyService)
                        Proxy.newProxyInstance(
                                ForcedProxyService.class.getClassLoader(),
                                new Class<?>[] {ForcedProxyService.class, SpringProxy.class},
                                (p, method, args) -> {
                                    if ("hashCode".equals(method.getName())) {
                                        return System.identityHashCode(p);
                                    }
                                    if ("equals".equals(method.getName())) {
                                        return p == args[0];
                                    }
                                    if ("toString".equals(method.getName())) {
                                        return "stale-proxy";
                                    }
                                    return "stale";
                                });
        assertTrue(AopUtils.isAopProxy(stale));

        context = new GenericApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton("forcedProxyService", stale);
        context.refresh();
        SpringContextTracker.register(context);

        String direct = SpringProxies.recreate(context, ForcedProxyService.class);
        assertEquals(SpringAdapter.PROXY_STALE_DETAIL, direct, "direct recreate");
        AdapterOutcome outcome =
                adapter.onClassesReloaded(
                        reload(ForcedProxyService.class.getName(), ForcedProxyService.class, "BODY"));
        assertEquals(AdapterOutcome.PARTIAL, outcome.status);
        assertEquals(SpringAdapter.PROXY_STALE_DETAIL, outcome.detail);
        assertSame(stale, context.getBean("forcedProxyService"));
    }

    @Test
    void successfulRecreateReplacesProxyInstance() {
        adapter.onAgentStart(SpringTestSupport.context(false));
        ProxiedTarget target = new ProxiedTarget();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodBeforeAdvice) (method, args, source) -> {});
        Object proxy = factory.getProxy();

        context = new GenericApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton("proxiedTarget", proxy);
        context.refresh();
        SpringContextTracker.register(context);

        String direct = SpringProxies.recreate(context, ProxiedTarget.class);
        assertEquals(null, direct, "direct recreate");
        adapter.onClassesReloaded(reload(ProxiedTarget.class.getName(), ProxiedTarget.class, "BODY"));
        Object after = context.getBean("proxiedTarget");
        assertNotSame(proxy, after);
        assertSame(target, AopProxyUtils.getSingletonTarget(after));
    }

    private static ClassReloadEvent reload(String name, Class<?> type, String kind) {
        return new ClassReloadEvent(
                SpringTestSupport.context(false),
                List.of(new ReloadedClass(name, type, List.of(kind))),
                "standard",
                Capabilities.none());
    }
}

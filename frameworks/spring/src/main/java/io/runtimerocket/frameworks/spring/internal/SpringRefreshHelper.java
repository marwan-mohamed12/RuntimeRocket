package io.runtimerocket.frameworks.spring.internal;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/** Framework 6.2 helper. Injected into the application loader so it can see Spring types. */
public final class SpringRefreshHelper {

    private SpringRefreshHelper() {}

    public static void refresh(Object context, Class<?> type, @SuppressWarnings("unused") List<String> changeKinds) {
        if (!(context instanceof ConfigurableApplicationContext cac) || type == null || !cac.isActive()) {
            return;
        }
        DefaultListableBeanFactory factory;
        try {
            factory = (DefaultListableBeanFactory) cac.getBeanFactory();
        } catch (RuntimeException e) {
            return;
        }
        boolean configuration = AnnotationUtils.findAnnotation(type, Configuration.class) != null;
        boolean component = AnnotationUtils.findAnnotation(type, Component.class) != null;
        String[] names = factory.getBeanNamesForType(type, false, false);
        if (names.length > 0) {
            if (configuration) {
                invokeNewBeanMethods(factory, type, names[0]);
            }
            return;
        }
        if (component || configuration) {
            registerAndInstantiate(factory, type);
            if (configuration) {
                invokeNewBeanMethods(factory, type, null);
            }
        }
    }

    private static void registerAndInstantiate(DefaultListableBeanFactory factory, Class<?> type) {
        AnnotatedGenericBeanDefinition bd = new AnnotatedGenericBeanDefinition(type);
        bd.setScope(BeanDefinition.SCOPE_SINGLETON);
        String name = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(bd, factory);
        if (!factory.containsBeanDefinition(name)) {
            factory.registerBeanDefinition(name, bd);
        }
        factory.getBean(name);
    }

    private static void invokeNewBeanMethods(DefaultListableBeanFactory factory, Class<?> type, String configBeanName) {
        Object config = configInstance(factory, type, configBeanName);
        if (config == null) {
            return;
        }
        config = unwrapProxy(config);
        for (Method method : type.getDeclaredMethods()) {
            Bean bean = AnnotationUtils.findAnnotation(method, Bean.class);
            if (bean == null) {
                continue;
            }
            String name = beanName(method, bean);
            if (factory.containsBean(name) || factory.containsBeanDefinition(name)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object created = method.invoke(config, resolveArgs(factory, method));
                if (created != null && !factory.containsSingleton(name)) {
                    factory.registerSingleton(name, created);
                }
            } catch (ReflectiveOperationException ignored) {
                // leave the missing bean; next reload or restart can retry
            }
        }
    }

    private static Object configInstance(DefaultListableBeanFactory factory, Class<?> type, String preferred) {
        if (preferred != null && factory.containsBean(preferred)) {
            return factory.getBean(preferred);
        }
        String[] names = factory.getBeanNamesForType(type, false, false);
        if (names.length > 0) {
            return factory.getBean(names[0]);
        }
        return null;
    }

    private static Object unwrapProxy(Object instance) {
        if (instance == null) {
            return null;
        }
        try {
            Class<?> utils = Class.forName("org.springframework.aop.framework.AopProxyUtils", false, instance.getClass().getClassLoader());
            Object target = utils.getMethod("getSingletonTarget", Object.class).invoke(null, instance);
            return target == null ? instance : target;
        } catch (ReflectiveOperationException e) {
            return instance;
        }
    }

    private static Object[] resolveArgs(DefaultListableBeanFactory factory, Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = factory.getBean(types[i]);
        }
        return args;
    }

    private static String beanName(Method method, Bean bean) {
        if (bean.value().length > 0 && !bean.value()[0].isBlank()) {
            return bean.value()[0];
        }
        if (bean.name().length > 0 && !bean.name()[0].isBlank()) {
            return bean.name()[0];
        }
        return method.getName();
    }
}

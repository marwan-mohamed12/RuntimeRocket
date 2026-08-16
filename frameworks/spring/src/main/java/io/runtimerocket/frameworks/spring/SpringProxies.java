package io.runtimerocket.frameworks.spring;

import java.lang.reflect.Method;

/**
 * Best-effort recreate of Spring AOP proxies after the target class reloads. Reuses the singleton
 * target; failure is {@link SpringAdapter#PROXY_STALE_DETAIL}.
 */
final class SpringProxies {

    private SpringProxies() {}

    /**
     * @return {@code null} on success or when the type is not a proxy target; otherwise
     *     {@link SpringAdapter#PROXY_STALE_DETAIL}
     */
    static String recreate(Object context, Class<?> type) {
        if (context == null || type == null || isSpringInternal(type)) {
            return null;
        }
        if (!isActive(context)) {
            return null;
        }
        try {
            ClassLoader loader = loaderOf(context, type);
            Class<?> aopUtils = load(loader, "org.springframework.aop.support.AopUtils");
            Class<?> aopProxyUtils = load(loader, "org.springframework.aop.framework.AopProxyUtils");
            if (aopUtils == null) {
                return null;
            }
            Method isAopProxy = aopUtils.getMethod("isAopProxy", Object.class);
            Object factory = beanFactory(context);
            if (factory == null) {
                return null;
            }
            for (String name : candidateNames(factory, context, type)) {
                if (name == null || name.startsWith("&")) {
                    continue;
                }
                Object bean = getSingleton(factory, name);
                if (bean == null) {
                    bean = getBean(context, name);
                }
                if (bean == null || !isProxyForType(isAopProxy, aopProxyUtils, bean, type)) {
                    continue;
                }
                if (!recreateOne(factory, loader, name, bean)) {
                    return SpringAdapter.PROXY_STALE_DETAIL;
                }
            }
            return null;
        } catch (Throwable e) {
            return SpringAdapter.PROXY_STALE_DETAIL;
        }
    }

    private static boolean isProxyForType(Method isAopProxy, Class<?> aopProxyUtils, Object bean, Class<?> type)
            throws Exception {
        if (!Boolean.TRUE.equals(isAopProxy.invoke(null, bean))) {
            return false;
        }
        if (type.isInstance(bean)) {
            return true;
        }
        if (aopProxyUtils == null) {
            return false;
        }
        Object target = aopProxyUtils.getMethod("getSingletonTarget", Object.class).invoke(null, bean);
        return target != null && (type.isInstance(target) || type == target.getClass());
    }

    private static String[] candidateNames(Object factory, Object context, Class<?> type) throws Exception {
        String[] typed = beanNamesForType(factory, type);
        if (typed.length > 0) {
            return typed;
        }
        Method beansOfType = findMethod(context.getClass(), "getBeansOfType", Class.class);
        if (beansOfType != null) {
            Object beans = beansOfType.invoke(context, type);
            if (beans instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
                return map.keySet().stream().map(String::valueOf).toArray(String[]::new);
            }
        }
        Method singletons = findMethod(factory.getClass(), "getSingletonNames");
        if (singletons == null) {
            return new String[0];
        }
        Object names = singletons.invoke(factory);
        return names instanceof String[] array ? array : new String[0];
    }

    private static boolean recreateOne(Object factory, ClassLoader loader, String name, Object proxy) {
        try {
            Class<?> aopProxyUtils = load(loader, "org.springframework.aop.framework.AopProxyUtils");
            Class<?> advisedType = load(loader, "org.springframework.aop.framework.Advised");
            Class<?> proxyFactoryType = load(loader, "org.springframework.aop.framework.ProxyFactory");
            Class<?> advisorType = load(loader, "org.springframework.aop.Advisor");
            if (aopProxyUtils == null || advisedType == null || proxyFactoryType == null || advisorType == null) {
                return false;
            }
            if (!advisedType.isInstance(proxy)) {
                return false;
            }
            Object target = aopProxyUtils.getMethod("getSingletonTarget", Object.class).invoke(null, proxy);
            if (target == null) {
                return false;
            }
            Object pf = proxyFactoryType.getConstructor().newInstance();
            copyBoolean(proxy, pf, "isProxyTargetClass", "setProxyTargetClass");
            copyBoolean(proxy, pf, "isExposeProxy", "setExposeProxy");
            copyBoolean(proxy, pf, "isOpaque", "setOpaque");
            copyBoolean(proxy, pf, "isOptimize", "setOptimize");
            Object interfaces = advisedType.getMethod("getProxiedInterfaces").invoke(proxy);
            Method setInterfaces = findMethod(pf.getClass(), "setInterfaces", Class[].class);
            if (setInterfaces != null && interfaces instanceof Class<?>[]) {
                setInterfaces.invoke(pf, (Object) interfaces);
            }
            Object targetSource = advisedType.getMethod("getTargetSource").invoke(proxy);
            Class<?> targetSourceType = load(loader, "org.springframework.aop.TargetSource");
            Method setTargetSource =
                    targetSourceType == null ? null : findMethod(pf.getClass(), "setTargetSource", targetSourceType);
            if (setTargetSource != null && targetSource != null) {
                setTargetSource.invoke(pf, targetSource);
            } else {
                Method setTarget = findMethod(pf.getClass(), "setTarget", Object.class);
                if (setTarget == null) {
                    return false;
                }
                setTarget.invoke(pf, target);
            }
            Object advisors = advisedType.getMethod("getAdvisors").invoke(proxy);
            Method addAdvisor = findMethod(pf.getClass(), "addAdvisor", advisorType);
            if (addAdvisor == null || !(advisors instanceof Object[] list)) {
                return false;
            }
            for (Object advisor : list) {
                addAdvisor.invoke(pf, advisor);
            }
            Method getProxy = findMethod(pf.getClass(), "getProxy", ClassLoader.class);
            if (getProxy == null) {
                return false;
            }
            ClassLoader proxyLoader = target.getClass().getClassLoader();
            if (proxyLoader == null) {
                proxyLoader = loader;
            }
            Object replacement = getProxy.invoke(pf, proxyLoader);
            return replacement != null && replaceSingleton(factory, name, replacement);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean replaceSingleton(Object factory, String name, Object replacement) {
        try {
            Method remove = findDeclared(factory.getClass(), "removeSingleton", String.class);
            if (remove == null) {
                return false;
            }
            remove.invoke(factory, name);
            Method register = findMethod(factory.getClass(), "registerSingleton", String.class, Object.class);
            if (register == null) {
                return false;
            }
            register.invoke(factory, name, replacement);
            Method clear = findMethod(factory.getClass(), "clearMetadataCache");
            if (clear != null) {
                clear.invoke(factory);
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void copyBoolean(Object proxy, Object pf, String getter, String setter) throws Exception {
        Method get = findMethod(proxy.getClass(), getter);
        if (get == null) {
            return;
        }
        Object value = get.invoke(proxy);
        Method method = findMethod(pf.getClass(), setter, boolean.class);
        if (method != null && value instanceof Boolean) {
            method.invoke(pf, value);
        }
    }

    private static boolean isSpringInternal(Class<?> type) {
        return type.getName().startsWith("org.springframework.");
    }

    private static boolean isActive(Object context) {
        try {
            Method isActive = findMethod(context.getClass(), "isActive");
            return isActive == null || Boolean.TRUE.equals(isActive.invoke(context));
        } catch (Exception e) {
            return false;
        }
    }

    private static Object beanFactory(Object context) {
        try {
            Method method = findMethod(context.getClass(), "getBeanFactory");
            return method == null ? null : method.invoke(context);
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] beanNamesForType(Object factory, Class<?> type) throws Exception {
        Method method =
                findMethod(factory.getClass(), "getBeanNamesForType", Class.class, boolean.class, boolean.class);
        if (method == null) {
            return new String[0];
        }
        Object names = method.invoke(factory, type, false, false);
        return names instanceof String[] array ? array : new String[0];
    }

    private static Object getSingleton(Object factory, String name) {
        try {
            Method method = findMethod(factory.getClass(), "getSingleton", String.class);
            return method == null ? null : method.invoke(factory, name);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getBean(Object context, String name) {
        try {
            Method method = findMethod(context.getClass(), "getBean", String.class);
            return method == null ? null : method.invoke(context, name);
        } catch (Exception e) {
            return null;
        }
    }

    private static ClassLoader loaderOf(Object context, Class<?> type) {
        if (type.getClassLoader() != null) {
            return type.getClassLoader();
        }
        return context.getClass().getClassLoader();
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return findDeclared(type, name, params);
        }
    }

    private static Method findDeclared(Class<?> type, String name, Class<?>... params) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method method = cursor.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // walk superclass
            }
        }
        return null;
    }
}

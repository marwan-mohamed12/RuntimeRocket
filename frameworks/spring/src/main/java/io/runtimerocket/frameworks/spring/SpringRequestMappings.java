package io.runtimerocket.frameworks.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds servlet {@code RequestMappingHandlerMapping} entries for a reloaded controller. Uses
 * reflection so Framework 6.2 and 7 stay on separate compile classpaths.
 */
public final class SpringRequestMappings {

    public static final String SERVLET_MAPPING =
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping";
    public static final String WEBFLUX_MAPPING =
            "org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping";

    private static final String[] DETECT_NAMES = {"detectHandlerMethods", "detectAndRegisterHandlerMethods"};

    private SpringRequestMappings() {}

    /**
     * @return {@code null} on success or when there is nothing to rebuild; otherwise
     *     {@link SpringAdapter#MAPPING_STALE_DETAIL}
     */
    static String rebuild(Object context, Class<?> type) {
        if (context == null || type == null || !isController(type) || isSpringInternal(type)) {
            return null;
        }
        if (!isActive(context)) {
            return null;
        }
        try {
            ClassLoader loader = loaderOf(context, type);
            Class<?> mappingType = load(loader, SERVLET_MAPPING);
            if (mappingType == null) {
                return null;
            }
            Map<String, Object> mappings = beansOfType(context, mappingType);
            if (mappings.isEmpty()) {
                return null;
            }
            Map<String, Object> handlers = beansOfType(context, type);
            if (handlers.isEmpty()) {
                return null;
            }
            for (Object mapping : mappings.values()) {
                if (mapping == null) {
                    continue;
                }
                if (!isHandler(mapping, type)) {
                    continue;
                }
                Method detect = findDetectMethod(mapping.getClass());
                if (detect == null) {
                    return SpringAdapter.MAPPING_STALE_DETAIL;
                }
                Method getHandlerMethods = findPublic(mapping.getClass(), "getHandlerMethods");
                Method unregister = findPublic(mapping.getClass(), "unregisterMapping", Object.class);
                if (getHandlerMethods == null || unregister == null) {
                    return SpringAdapter.MAPPING_STALE_DETAIL;
                }
                for (Map.Entry<String, Object> handler : handlers.entrySet()) {
                    rebuildOne(
                            mapping,
                            detect,
                            getHandlerMethods,
                            unregister,
                            handler.getKey(),
                            handler.getValue(),
                            type);
                }
            }
            return null;
        } catch (Throwable e) {
            return SpringAdapter.MAPPING_STALE_DETAIL;
        }
    }

    public static String detectMethodName(Class<?> mappingType) {
        Method method = findDetectMethod(mappingType);
        return method == null ? null : method.getName();
    }

    public static boolean webFluxMappingPresent(ClassLoader loader) {
        return load(loader, WEBFLUX_MAPPING) != null;
    }

    static Method findDetectMethod(Class<?> mappingType) {
        if (mappingType == null) {
            return null;
        }
        for (String name : DETECT_NAMES) {
            Method method = findDeclared(mappingType, name, Object.class);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static void rebuildOne(
            Object mapping,
            Method detect,
            Method getHandlerMethods,
            Method unregister,
            String handlerName,
            Object handler,
            Class<?> type)
            throws Exception {
        Object raw = invoke(getHandlerMethods, mapping);
        if (!(raw instanceof Map<?, ?> methods)) {
            throw new IllegalStateException("getHandlerMethods");
        }
        List<Object> stale = new ArrayList<>();
        List<Object[]> snapshot = new ArrayList<>();
        for (Map.Entry<?, ?> entry : methods.entrySet()) {
            if (matchesHandler(entry.getValue(), handlerName, handler, type)) {
                stale.add(entry.getKey());
                snapshot.add(new Object[] {entry.getKey(), entry.getValue()});
            }
        }
        for (Object key : stale) {
            invoke(unregister, mapping, key);
        }
        try {
            Object detectArg = handlerName != null ? handlerName : handler;
            invoke(detect, mapping, detectArg);
        } catch (Exception e) {
            restoreMappings(mapping, snapshot);
            throw e;
        }
    }

    private static void restoreMappings(Object mapping, List<Object[]> snapshot) {
        Method register = findPublic(mapping.getClass(), "registerMapping", Object.class, Object.class, Method.class);
        if (register == null) {
            return;
        }
        for (Object[] item : snapshot) {
            try {
                Object handlerMethod = item[1];
                Method getBean = findPublic(handlerMethod.getClass(), "getBean");
                Method getMethod = findPublic(handlerMethod.getClass(), "getMethod");
                if (getBean == null || getMethod == null) {
                    continue;
                }
                Object bean = invoke(getBean, handlerMethod);
                Object method = invoke(getMethod, handlerMethod);
                if (method instanceof Method reflected) {
                    invoke(register, mapping, item[0], bean, reflected);
                }
            } catch (Exception ignored) {
                // best-effort restore; caller still reports FAILED
            }
        }
    }

    private static boolean matchesHandler(Object handlerMethod, String handlerName, Object handler, Class<?> type) {
        if (handlerMethod == null) {
            return false;
        }
        try {
            Method getBean = findPublic(handlerMethod.getClass(), "getBean");
            if (getBean == null) {
                return false;
            }
            Object bean = invoke(getBean, handlerMethod);
            if (bean instanceof String name) {
                return name.equals(handlerName);
            }
            if (handler != null && bean == handler) {
                return true;
            }
            Method getBeanType = findPublic(handlerMethod.getClass(), "getBeanType");
            if (getBeanType == null) {
                return false;
            }
            Object beanType = invoke(getBeanType, handlerMethod);
            return beanType == type;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isHandler(Object mapping, Class<?> type) throws Exception {
        Method isHandler = findDeclared(mapping.getClass(), "isHandler", Class.class);
        if (isHandler == null) {
            return isController(type);
        }
        Object result = invoke(isHandler, mapping, type);
        return Boolean.TRUE.equals(result);
    }

    static boolean isController(Class<?> type) {
        if (type == null) {
            return false;
        }
        for (Annotation annotation : type.getAnnotations()) {
            if (isControllerName(annotation.annotationType().getName())) {
                return true;
            }
            for (Annotation meta : annotation.annotationType().getAnnotations()) {
                if (isControllerName(meta.annotationType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isControllerName(String name) {
        return "org.springframework.stereotype.Controller".equals(name)
                || "org.springframework.web.bind.annotation.RestController".equals(name);
    }

    private static boolean isSpringInternal(Class<?> type) {
        String name = type.getName();
        return name.startsWith("org.springframework.");
    }

    private static boolean isActive(Object context) {
        try {
            Method isActive = findPublic(context.getClass(), "isActive");
            return isActive == null || Boolean.TRUE.equals(invoke(isActive, context));
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> beansOfType(Object context, Class<?> type) throws Exception {
        Method method = findPublic(context.getClass(), "getBeansOfType", Class.class);
        if (method == null) {
            return Map.of();
        }
        Object beans = invoke(method, context, type);
        if (beans instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String name) {
                    copy.put(name, entry.getValue());
                }
            }
            return copy;
        }
        return Map.of();
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

    static Method findDeclared(Class<?> type, String name, Class<?>... params) {
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

    private static Method findPublic(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return findDeclared(type, name, params);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}

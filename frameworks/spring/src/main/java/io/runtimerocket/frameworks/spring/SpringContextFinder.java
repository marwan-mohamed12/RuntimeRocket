package io.runtimerocket.frameworks.spring;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Late-attach discovery: JMX / servlet-attribute, then {@code ContextLoader}, then a live request.
 * No heap walk.
 */
public final class SpringContextFinder {

    static final String ROOT_ATTRIBUTE = "org.springframework.web.context.WebApplicationContext.ROOT";

    private static final String[] SERVER_TYPES = {
        "org.springframework.boot.web.embedded.tomcat.TomcatWebServer",
        "org.springframework.boot.web.embedded.jetty.JettyWebServer",
        "org.springframework.boot.web.embedded.undertow.UndertowWebServer",
        "org.springframework.boot.tomcat.TomcatWebServer",
        "org.springframework.boot.jetty.JettyWebServer",
        "org.springframework.boot.undertow.UndertowWebServer",
        "org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory",
        "org.springframework.boot.jetty.servlet.JettyServletWebServerFactory",
        "org.springframework.boot.undertow.servlet.UndertowServletWebServerFactory"
    };

    // Domain-qualified only. A "*:" domain match walks every MBean and can stall
    // late-attach on a loaded JVM; idle Boot still returns immediately.
    private static final String[] MBEAN_PATTERNS = {
        "Catalina:j2eeType=WebModule,*",
        "Catalina:type=Context,*",
        "Tomcat:j2eeType=WebModule,*",
        "Tomcat:type=Context,*",
        "org.eclipse.jetty.servlet:type=context,*",
        "org.eclipse.jetty.webapp:type=webappcontext,*"
    };

    private SpringContextFinder() {}

    public static List<Object> probe(ClassLoader[] loaders, Instrumentation inst) {
        List<Object> found = new ArrayList<>();
        addAll(found, probeJmx());
        addAll(found, probeServerStatics(inst));
        addAll(found, probeContextLoader(loaders));
        addAll(found, probeRequest(loaders));
        return found;
    }

    static List<Object> probeJmx() {
        List<Object> found = new ArrayList<>();
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            for (String pattern : MBEAN_PATTERNS) {
                for (ObjectName name : server.queryNames(new ObjectName(pattern), null)) {
                    Object context = contextFromMbean(server, name);
                    if (context != null) {
                        found.add(context);
                    }
                }
            }
        } catch (Exception ignored) {
            // idle Boot has no servlet MBeans
        }
        return found;
    }

    static List<Object> probeServerStatics(Instrumentation inst) {
        List<Object> found = new ArrayList<>();
        if (inst == null) {
            return found;
        }
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            String name = loaded.getName();
            if (!isServerType(name)) {
                continue;
            }
            for (Field field : loaded.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    Object context = contextFromHolder(value);
                    if (context != null) {
                        found.add(context);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // keep scanning
                }
            }
        }
        return found;
    }

    static List<Object> probeContextLoader(ClassLoader[] loaders) {
        List<Object> found = new ArrayList<>();
        if (loaders == null) {
            return found;
        }
        for (ClassLoader loader : loaders) {
            Class<?> type = SpringEnvironment.load(loader, "org.springframework.web.context.ContextLoader");
            if (type == null) {
                continue;
            }
            try {
                Object ctx = type.getMethod("getCurrentWebApplicationContext").invoke(null);
                if (ctx != null) {
                    found.add(ctx);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // classic WAR only; servlet types may be absent
            }
        }
        return found;
    }

    static List<Object> probeRequest(ClassLoader[] loaders) {
        List<Object> found = new ArrayList<>();
        if (loaders == null) {
            return found;
        }
        for (ClassLoader loader : loaders) {
            Class<?> holder =
                    SpringEnvironment.load(loader, "org.springframework.web.context.request.RequestContextHolder");
            if (holder == null) {
                continue;
            }
            try {
                Object attrs = holder.getMethod("getRequestAttributes").invoke(null);
                if (attrs == null) {
                    continue;
                }
                Object request = invoke(attrs, "getRequest");
                if (request == null) {
                    continue;
                }
                Object servletContext = invoke(request, "getServletContext");
                Object ctx = attribute(servletContext, ROOT_ATTRIBUTE);
                if (ctx == null) {
                    ctx = webApplicationContextUtils(loader, servletContext);
                }
                if (ctx != null) {
                    found.add(ctx);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // no request in flight; servlet types may be absent
            }
        }
        return found;
    }

    private static Object contextFromMbean(MBeanServer server, ObjectName name) {
        Object resource = attributeOrNull(server, name, "managedResource");
        if (resource == null) {
            resource = attributeOrNull(server, name, "context");
        }
        if (resource == null) {
            resource = invokeOrNull(server, name, "getObject");
        }
        Object servletContext = servletContextOf(resource);
        if (servletContext == null) {
            servletContext = attributeOrNull(server, name, "servletContext");
        }
        return attribute(servletContext, ROOT_ATTRIBUTE);
    }

    private static Object servletContextOf(Object resource) {
        if (resource == null) {
            return null;
        }
        Object sc = invoke(resource, "getServletContext");
        if (sc != null) {
            return sc;
        }
        return invoke(resource, "getContext");
    }

    private static Object contextFromHolder(Object value) {
        if (value == null) {
            return null;
        }
        if (isApplicationContext(value)) {
            return value;
        }
        Object ctx = invoke(value, "getApplicationContext");
        if (ctx != null) {
            return ctx;
        }
        Object servletContext = servletContextOf(value);
        return attribute(servletContext, ROOT_ATTRIBUTE);
    }

    private static boolean isApplicationContext(Object value) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if ("org.springframework.context.ApplicationContext".equals(type.getName())
                    || "org.springframework.context.ConfigurableApplicationContext".equals(type.getName())) {
                return true;
            }
            for (Class<?> iface : type.getInterfaces()) {
                if ("org.springframework.context.ApplicationContext".equals(iface.getName())
                        || "org.springframework.context.ConfigurableApplicationContext".equals(iface.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isServerType(String name) {
        for (String expected : SERVER_TYPES) {
            if (expected.equals(name)) {
                return true;
            }
        }
        return name != null && name.startsWith("org.springframework.boot.") && name.endsWith("WebServer");
    }

    private static Object webApplicationContextUtils(ClassLoader loader, Object servletContext) {
        Class<?> utils =
                SpringEnvironment.load(loader, "org.springframework.web.context.support.WebApplicationContextUtils");
        if (utils == null || servletContext == null) {
            return null;
        }
        try {
            return utils.getMethod("getWebApplicationContext", servletContext.getClass().getInterfaces().length == 0
                            ? servletContext.getClass()
                            : firstServletContextType(servletContext))
                    .invoke(null, servletContext);
        } catch (ReflectiveOperationException e) {
            try {
                for (Method method : utils.getMethods()) {
                    if ("getWebApplicationContext".equals(method.getName()) && method.getParameterCount() == 1) {
                        return method.invoke(null, servletContext);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
            return null;
        }
    }

    private static Class<?> firstServletContextType(Object servletContext) {
        for (Class<?> iface : servletContext.getClass().getInterfaces()) {
            if ("jakarta.servlet.ServletContext".equals(iface.getName())
                    || "javax.servlet.ServletContext".equals(iface.getName())) {
                return iface;
            }
        }
        return servletContext.getClass();
    }

    private static Object attribute(Object servletContext, String name) {
        if (servletContext == null) {
            return null;
        }
        try {
            return servletContext.getClass().getMethod("getAttribute", String.class).invoke(servletContext, name);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object attributeOrNull(MBeanServer server, ObjectName name, String attribute) {
        try {
            return server.getAttribute(name, attribute);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeOrNull(MBeanServer server, ObjectName name, String operation) {
        try {
            return server.invoke(name, operation, new Object[0], new String[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static void addAll(List<Object> into, List<Object> extra) {
        for (Object item : extra) {
            if (item != null && !into.contains(item)) {
                into.add(item);
            }
        }
    }
}

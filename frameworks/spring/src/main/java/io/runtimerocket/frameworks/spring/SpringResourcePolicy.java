package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.ResourceChangeEvent;

import java.util.Locale;

/** Classifies watched resource files. Bytes are never injected. */
public final class SpringResourcePolicy {

    private SpringResourcePolicy() {}

    public static boolean isBootConfig(ResourceChangeEvent.ChangedResource resource) {
        String name = fileName(resource);
        if (name == null) {
            return false;
        }
        return isBootConfigName(name);
    }

    public static boolean isStatic(ResourceChangeEvent.ChangedResource resource) {
        String name = fileName(resource);
        if (name == null) {
            return false;
        }
        return name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".js")
                || name.endsWith(".css")
                || name.endsWith(".svg")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".ico")
                || name.endsWith(".woff")
                || name.endsWith(".woff2")
                || looksStaticPath(resource);
    }

    public static boolean isBootConfigName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return "application.properties".equals(name)
                || "application.yml".equals(name)
                || "application.yaml".equals(name)
                || matchesProfileConfig(name, ".properties")
                || matchesProfileConfig(name, ".yml")
                || matchesProfileConfig(name, ".yaml");
    }

    private static boolean matchesProfileConfig(String name, String ext) {
        return name.startsWith("application-") && name.endsWith(ext) && name.length() > ("application-".length() + ext.length());
    }

    private static boolean looksStaticPath(ResourceChangeEvent.ChangedResource resource) {
        String path = combinedPath(resource).toLowerCase(Locale.ROOT).replace('\\', '/');
        return path.contains("/static/")
                || path.contains("/public/")
                || path.contains("/resources/")
                || path.contains("/meta-inf/resources/");
    }

    private static String fileName(ResourceChangeEvent.ChangedResource resource) {
        String path = combinedPath(resource);
        if (path.isEmpty()) {
            return null;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String combinedPath(ResourceChangeEvent.ChangedResource resource) {
        if (resource == null) {
            return "";
        }
        if (resource.classpathName != null && !resource.classpathName.isBlank()) {
            return resource.classpathName;
        }
        return resource.path == null ? "" : resource.path;
    }
}

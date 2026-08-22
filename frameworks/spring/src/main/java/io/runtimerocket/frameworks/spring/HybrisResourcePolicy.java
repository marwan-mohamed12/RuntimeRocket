package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.ResourceChangeEvent;

import java.util.Locale;

/**
 * SAP Commerce (Hybris) files that cannot be hot-reloaded. Type system, Spring XML, ImpEx, and
 * {@code local.properties} need HAC / {@code ant} / a process restart.
 */
public final class HybrisResourcePolicy {

    public static final String ITEMS_XML_DETAIL =
            "items.xml / type system — HAC Update + restart hybrisserver";
    public static final String SPRING_XML_DETAIL = "Spring XML — restart hybrisserver to apply";
    public static final String PROPERTIES_DETAIL = "Hybris properties — restart hybrisserver to apply";
    public static final String IMPEX_DETAIL = "ImpEx is not hot-reloaded — import in HAC";
    public static final String EXTENSION_DETAIL = "extension config — restart hybrisserver";

    private HybrisResourcePolicy() {}

    public static String restartDetail(ResourceChangeEvent.ChangedResource resource) {
        return restartDetailForName(fileName(resource));
    }

    static String restartDetailForName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if ("items.xml".equals(name) || name.endsWith("-items.xml")) {
            return ITEMS_XML_DETAIL;
        }
        if ("extensioninfo.xml".equals(name) || "localextensions.xml".equals(name)) {
            return EXTENSION_DETAIL;
        }
        if (name.endsWith("-spring.xml")
                || name.endsWith("-web-spring.xml")
                || "spring.xml".equals(name)
                || name.endsWith("-spring-mvc.xml")) {
            return SPRING_XML_DETAIL;
        }
        if ("local.properties".equals(name) || "project.properties".equals(name)) {
            return PROPERTIES_DETAIL;
        }
        if (name.endsWith(".impex")) {
            return IMPEX_DETAIL;
        }
        return null;
    }

    private static String fileName(ResourceChangeEvent.ChangedResource resource) {
        if (resource == null) {
            return null;
        }
        if (resource.classpathName != null && !resource.classpathName.isBlank()) {
            return resource.classpathName;
        }
        return resource.path;
    }
}

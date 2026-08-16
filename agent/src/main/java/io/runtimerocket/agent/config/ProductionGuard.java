package io.runtimerocket.agent.config;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Aborts start on platform production signals unless explicitly allowed. {@code prod}+{@code PORT}
 * is a warning only.
 */
public final class ProductionGuard {

    public static final String ENV_ALLOW = "RUNTIMEROCKET_ALLOW_NONDEV";
    public static final String PROP_ALLOW = "rr.allowNonDev";
    public static final String PROP_PRODUCTION = "rr.production";

    private ProductionGuard() {}

    public static boolean shouldAbort() {
        return shouldAbort(System.getenv(), System.getProperties());
    }

    public static boolean shouldAbort(Map<String, String> env, Properties properties) {
        if (allowNonDev(env, properties)) {
            return false;
        }
        if (booleanProperty(properties, PROP_PRODUCTION)) {
            return true;
        }
        return has(env, "KUBERNETES_SERVICE_HOST")
                || has(env, "WEBSITE_SITE_NAME")
                || has(env, "DYNO");
    }

    public static boolean shouldWarnProdPort() {
        return shouldWarnProdPort(System.getenv(), System.getProperties());
    }

    public static boolean shouldWarnProdPort(Map<String, String> env, Properties properties) {
        if (!has(env, "PORT")) {
            return false;
        }
        return containsProdProfile(env, properties);
    }

    static boolean allowNonDev(Map<String, String> env, Properties properties) {
        if (booleanProperty(properties, PROP_ALLOW)) {
            return true;
        }
        String envValue = env == null ? null : env.get(ENV_ALLOW);
        return envValue != null && Boolean.parseBoolean(envValue.trim());
    }

    private static boolean containsProdProfile(Map<String, String> env, Properties properties) {
        String fromProp = properties == null ? null : properties.getProperty("spring.profiles.active");
        if (containsProdToken(fromProp)) {
            return true;
        }
        return containsProdToken(env == null ? null : env.get("SPRING_PROFILES_ACTIVE"));
    }

    private static boolean containsProdToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String[] parts = raw.split("[,]");
        for (String part : parts) {
            if ("prod".equals(part.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean booleanProperty(Properties properties, String key) {
        if (properties == null) {
            return false;
        }
        String raw = properties.getProperty(key);
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    private static boolean has(Map<String, String> env, String key) {
        if (env == null) {
            return false;
        }
        String value = env.get(key);
        return value != null && !value.isBlank();
    }
}

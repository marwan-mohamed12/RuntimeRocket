package io.runtimerocket.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Comma-separated {@code key=value} agent arguments. {@code -Drr.*} system properties override
 * matching keys.
 */
public final class AgentOptions {

    public static final String BACKEND_AUTO = "auto";
    public static final String BACKEND_STANDARD = "standard";
    public static final String BACKEND_ENHANCED = "enhanced";
    public static final String BACKEND_VERSIONING = "versioning";

    public final int port;
    public final Path tokenFile;
    public final String token;
    public final Path config;
    public final String backend;
    public final boolean watch;
    public final int debounceMs;
    public final String log;
    public final Path logFile;
    public final List<String> disabledAdapters;
    public final List<Path> watchDirs;

    AgentOptions(
            int port,
            Path tokenFile,
            String token,
            Path config,
            String backend,
            boolean watch,
            int debounceMs,
            String log,
            Path logFile,
            List<String> disabledAdapters,
            List<Path> watchDirs) {
        this.port = port;
        this.tokenFile = tokenFile;
        this.token = token;
        this.config = config;
        this.backend = backend;
        this.watch = watch;
        this.debounceMs = debounceMs;
        this.log = log;
        this.logFile = logFile;
        this.disabledAdapters = List.copyOf(disabledAdapters);
        this.watchDirs = List.copyOf(watchDirs);
    }

    public static AgentOptions parse(String args) {
        return parse(args, System.getProperties());
    }

    public static AgentOptions parse(String args, Properties properties) {
        Map<String, List<String>> values = parseArgs(args);
        overlay(values, properties, "port", "rr.port");
        overlay(values, properties, "tokenFile", "rr.tokenFile");
        overlay(values, properties, "token", "rr.token");
        overlay(values, properties, "config", "rr.config");
        overlay(values, properties, "backend", "rr.backend");
        overlay(values, properties, "watch", "rr.watch");
        overlay(values, properties, "debounceMs", "rr.debounceMs");
        overlay(values, properties, "log", "rr.log");
        overlay(values, properties, "logFile", "rr.logFile");
        overlay(values, properties, "disabledAdapters", "rr.disabledAdapters");
        overlayAppend(values, properties, "watchDir", "rr.watchDir");

        int port = parsePort(first(values, "port"), 0);
        Path tokenFile = pathOrNull(first(values, "tokenFile"));
        String token = emptyToNull(first(values, "token"));
        Path config = pathOrNull(first(values, "config"));
        String backend = first(values, "backend");
        if (backend == null || backend.isBlank()) {
            backend = BACKEND_AUTO;
        } else {
            backend = backend.trim().toLowerCase(Locale.ROOT);
        }
        boolean watch = parseBoolean(first(values, "watch"), true);
        int debounceMs = parseInt(first(values, "debounceMs"), 150);
        if (debounceMs < 0) {
            throw new AgentStartException("debounceMs must be >= 0");
        }
        String log = first(values, "log");
        if (log == null || log.isBlank()) {
            log = "info";
        }
        Path logFile = pathOrNull(first(values, "logFile"));
        List<String> disabledAdapters = parseAdapterIds(values.get("disabledAdapters"));
        List<Path> watchDirs = parseWatchDirs(values.get("watchDir"));
        return new AgentOptions(
                port,
                tokenFile,
                token,
                config,
                backend,
                watch,
                debounceMs,
                log,
                logFile,
                disabledAdapters,
                watchDirs);
    }

    static Map<String, List<String>> parseArgs(String args) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (args == null || args.isBlank()) {
            return values;
        }
        for (String part : args.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return values;
    }

    private static void overlay(Map<String, List<String>> values, Properties properties, String key, String prop) {
        if (properties == null) {
            return;
        }
        String raw = properties.getProperty(prop);
        if (raw == null) {
            return;
        }
        values.put(key, List.of(raw));
    }

    private static void overlayAppend(
            Map<String, List<String>> values, Properties properties, String key, String prop) {
        if (properties == null) {
            return;
        }
        String raw = properties.getProperty(prop);
        if (raw == null || raw.isBlank()) {
            return;
        }
        values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(raw);
    }

    private static String first(Map<String, List<String>> values, String key) {
        List<String> list = values.get(key);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    private static int parsePort(String raw, int defaultValue) {
        int port = parseInt(raw, defaultValue);
        if (port < 0 || port > 65535) {
            throw new AgentStartException("port must be between 0 and 65535");
        }
        return port;
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new AgentStartException("not an integer: " + raw, e);
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new AgentStartException("not a boolean: " + raw);
        };
    }

    private static Path pathOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String path = raw.trim();
        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
            path = path.substring(1, path.length() - 1);
        }
        return Path.of(path);
    }

    private static String emptyToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private static List<String> parseAdapterIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            for (String part : item.split("[;|]")) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static List<Path> parseWatchDirs(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            out.add(Path.of(item.trim()));
        }
        return out;
    }
}

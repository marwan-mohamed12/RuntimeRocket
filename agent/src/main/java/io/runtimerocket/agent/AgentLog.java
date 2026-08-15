package io.runtimerocket.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/** Stdout (+ optional file) logger. Prefix is {@code [RuntimeRocket]}. */
public final class AgentLog {

    public enum Level {
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE
    }

    private final Level configured;
    private final Path logFile;
    private final Object fileLock = new Object();

    public AgentLog(String level, Path logFile) {
        this.configured = parseLevel(level);
        this.logFile = logFile;
    }

    public void error(String message) {
        write(Level.ERROR, message);
    }

    public void warn(String message) {
        write(Level.WARN, message);
    }

    public void info(String message) {
        write(Level.INFO, message);
    }

    public void debug(String message) {
        write(Level.DEBUG, message);
    }

    public void trace(String message) {
        write(Level.TRACE, message);
    }

    public boolean isDebugEnabled() {
        return configured.ordinal() >= Level.DEBUG.ordinal();
    }

    private void write(Level level, String message) {
        if (level.ordinal() > configured.ordinal()) {
            return;
        }
        String line = "[RuntimeRocket] " + message;
        if (level.ordinal() <= Level.WARN.ordinal()) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
        if (logFile == null) {
            return;
        }
        synchronized (fileLock) {
            try {
                Path parent = logFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        logFile,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // logging must not abort the agent
            }
        }
    }

    static Level parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return Level.INFO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "error" -> Level.ERROR;
            case "warn", "warning" -> Level.WARN;
            case "info" -> Level.INFO;
            case "debug" -> Level.DEBUG;
            case "trace" -> Level.TRACE;
            default -> Level.INFO;
        };
    }

    @Override
    public String toString() {
        return "AgentLog{level=" + configured + ", file=" + Objects.toString(logFile, "-") + '}';
    }
}

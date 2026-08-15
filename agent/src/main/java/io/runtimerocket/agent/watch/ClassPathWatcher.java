package io.runtimerocket.agent.watch;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.AgentOptions;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholder watcher. Full {@code WatchService} registration is not installed here; {@code
 * watch=false} is accepted and {@code watch=true} is a no-op.
 */
public final class ClassPathWatcher implements AutoCloseable {

    private final ConcurrentHashMap<Path, Long> suppressedUntil = new ConcurrentHashMap<>();

    public ClassPathWatcher(AgentOptions options, AgentLog log) {
        if (options.watch && log != null && log.isDebugEnabled()) {
            log.debug("classpath watcher not started");
        }
    }

    public void start() {
        // no-op
    }

    public void suppress(Path path, long durationMs) {
        if (path == null) {
            return;
        }
        suppressedUntil.put(path.toAbsolutePath().normalize(), System.currentTimeMillis() + durationMs);
    }

    public boolean isSuppressed(Path path) {
        if (path == null) {
            return false;
        }
        Long until = suppressedUntil.get(path.toAbsolutePath().normalize());
        return until != null && until > System.currentTimeMillis();
    }

    @Override
    public void close() {
        suppressedUntil.clear();
    }
}

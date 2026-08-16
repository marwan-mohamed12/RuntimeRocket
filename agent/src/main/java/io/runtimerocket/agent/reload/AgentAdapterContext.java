package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.spi.AdapterContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** {@link AdapterContext} backed by the live {@link ClassIndex}. */
public final class AgentAdapterContext implements AdapterContext {

    private final ClassIndex index;
    private final boolean lateAttach;
    private final AgentLog log;

    public AgentAdapterContext(ClassIndex index, boolean lateAttach, AgentLog log) {
        this.index = Objects.requireNonNull(index, "index");
        this.lateAttach = lateAttach;
        this.log = log;
    }

    @Override
    public ClassLoader[] applicationLoaders() {
        List<ClassLoader> loaders = new ArrayList<>(index.applicationLoaders());
        if (loaders.isEmpty()) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                loaders.add(tccl);
            }
            ClassLoader sys = ClassLoader.getSystemClassLoader();
            if (sys != null && !loaders.contains(sys)) {
                loaders.add(sys);
            }
        }
        return loaders.toArray(ClassLoader[]::new);
    }

    @Override
    public boolean isLateAttach() {
        return lateAttach;
    }

    @Override
    public void log(String level, String msg) {
        if (log == null || msg == null) {
            return;
        }
        String lv = level == null ? "info" : level;
        switch (lv.toLowerCase(Locale.ROOT)) {
            case "error" -> log.error(msg);
            case "warn", "warning" -> log.warn(msg);
            case "debug" -> log.debug(msg);
            case "trace" -> log.trace(msg);
            default -> log.info(msg);
        }
    }

    @Override
    public <T> T peekService(Class<T> type) {
        return null;
    }
}

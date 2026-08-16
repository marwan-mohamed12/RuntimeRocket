package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentLog;
import io.runtimerocket.agent.spi.AdapterContext;
import io.runtimerocket.agent.spi.ClassReloadEvent;
import io.runtimerocket.agent.spi.FrameworkAdapter;
import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Discovers {@link FrameworkAdapter}s and isolates their failures from JVMTI redefine.
 */
public final class AdapterHost {

    private final List<FrameworkAdapter> adapters = new CopyOnWriteArrayList<>();
    private final Set<String> disabledIds;
    private final AgentLog log;

    public AdapterHost(List<String> disabledIds, AgentLog log) {
        this.disabledIds = new HashSet<>();
        if (disabledIds != null) {
            for (String id : disabledIds) {
                if (id != null && !id.isBlank()) {
                    this.disabledIds.add(id.trim());
                }
            }
        }
        this.log = log;
    }

    public static AdapterHost none() {
        return new AdapterHost(List.of(), null);
    }

    public static AdapterHost load(ClassLoader loader, List<String> disabledIds, AgentLog log) {
        AdapterHost host = new AdapterHost(disabledIds, log);
        ClassLoader discovery = loader != null ? loader : AdapterHost.class.getClassLoader();
        ServiceLoader<FrameworkAdapter> services = ServiceLoader.load(FrameworkAdapter.class, discovery);
        services.stream().forEach(provider -> {
            try {
                host.register(provider.get());
            } catch (ServiceConfigurationError | RuntimeException e) {
                if (log != null) {
                    log.warn("adapter discovery failed: " + e.getMessage());
                }
            }
        });
        return host;
    }

    public void register(FrameworkAdapter adapter) {
        adapters.add(Objects.requireNonNull(adapter, "adapter"));
    }

    public List<FrameworkAdapter> registered() {
        return List.copyOf(adapters);
    }

    public void onAgentStart(AdapterContext ctx) {
        for (FrameworkAdapter adapter : eligible(ctx)) {
            try {
                adapter.onAgentStart(ctx);
            } catch (RuntimeException e) {
                warn(idOf(adapter), "onAgentStart", e);
            }
        }
    }

    public List<AdapterOutcome> onLateAttach(AdapterContext ctx) {
        List<AdapterOutcome> outcomes = new ArrayList<>();
        for (FrameworkAdapter adapter : eligible(ctx)) {
            outcomes.add(invoke(adapter, () -> adapter.onLateAttach(ctx)));
        }
        return outcomes;
    }

    public void onNewClass(Class<?> type) {
        if (type == null) {
            return;
        }
        AdapterContext ctx = new LoaderContext(type.getClassLoader());
        for (FrameworkAdapter adapter : eligible(ctx)) {
            try {
                adapter.onNewClass(type);
            } catch (RuntimeException e) {
                warn(idOf(adapter), "onNewClass", e);
            }
        }
    }

    public List<AdapterOutcome> onClassesReloaded(ClassReloadEvent event) {
        AdapterContext ctx = event == null ? null : event.ctx;
        List<AdapterOutcome> outcomes = new ArrayList<>();
        for (FrameworkAdapter adapter : eligible(ctx)) {
            outcomes.add(invoke(adapter, () -> adapter.onClassesReloaded(event)));
        }
        return outcomes;
    }

    public List<AdapterOutcome> onResourcesChanged(ResourceChangeEvent event) {
        AdapterContext ctx = event == null ? null : event.ctx;
        List<AdapterOutcome> outcomes = new ArrayList<>();
        for (FrameworkAdapter adapter : eligible(ctx)) {
            outcomes.add(invoke(adapter, () -> adapter.onResourcesChanged(event)));
        }
        return outcomes;
    }

    public void onAgentShutdown() {
        for (FrameworkAdapter adapter : adapters) {
            if (disabled(adapter)) {
                continue;
            }
            try {
                adapter.onAgentShutdown();
            } catch (RuntimeException e) {
                warn(idOf(adapter), "onAgentShutdown", e);
            }
        }
    }

    public static boolean softFailed(List<AdapterOutcome> outcomes) {
        if (outcomes == null) {
            return false;
        }
        for (AdapterOutcome outcome : outcomes) {
            if (outcome == null || outcome.status == null) {
                continue;
            }
            if (AdapterOutcome.FAILED.equals(outcome.status) || AdapterOutcome.PARTIAL.equals(outcome.status)) {
                return true;
            }
        }
        return false;
    }

    public static String firstFailureDetail(List<AdapterOutcome> outcomes) {
        if (outcomes == null) {
            return null;
        }
        for (AdapterOutcome outcome : outcomes) {
            if (outcome == null || outcome.status == null) {
                continue;
            }
            if (AdapterOutcome.FAILED.equals(outcome.status) || AdapterOutcome.PARTIAL.equals(outcome.status)) {
                if (outcome.detail != null && !outcome.detail.isBlank()) {
                    return outcome.detail;
                }
                return outcome.adapterId + " " + outcome.status;
            }
        }
        return null;
    }

    private List<FrameworkAdapter> eligible(AdapterContext ctx) {
        List<FrameworkAdapter> out = new ArrayList<>();
        for (FrameworkAdapter adapter : adapters) {
            if (disabled(adapter) || !available(adapter, ctx)) {
                continue;
            }
            out.add(adapter);
        }
        out.sort(Comparator.comparingInt(this::orderOf));
        return out;
    }

    private boolean disabled(FrameworkAdapter adapter) {
        return disabledIds.contains(idOf(adapter));
    }

    private boolean available(FrameworkAdapter adapter, AdapterContext ctx) {
        ClassLoader[] loaders = ctx == null ? null : ctx.applicationLoaders();
        if (loaders == null || loaders.length == 0) {
            ClassLoader fallback = Thread.currentThread().getContextClassLoader();
            if (fallback == null) {
                fallback = ClassLoader.getSystemClassLoader();
            }
            loaders = new ClassLoader[] {fallback};
        }
        for (ClassLoader loader : loaders) {
            try {
                if (adapter.isAvailable(loader)) {
                    return true;
                }
            } catch (RuntimeException e) {
                warn(idOf(adapter), "isAvailable", e);
            }
        }
        return false;
    }

    private AdapterOutcome invoke(FrameworkAdapter adapter, OutcomeCall call) {
        String id = idOf(adapter);
        long started = System.nanoTime();
        try {
            AdapterOutcome outcome = call.run();
            if (outcome == null) {
                outcome = AdapterOutcome.ok(id);
            }
            if (outcome.adapterId == null || outcome.adapterId.isBlank()) {
                outcome.adapterId = id;
            }
            if (outcome.durationMs <= 0L) {
                outcome.durationMs = elapsedMs(started);
            }
            return outcome;
        } catch (RuntimeException e) {
            warn(id, "invoke", e);
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new AdapterOutcome(id, AdapterOutcome.FAILED, elapsedMs(started), detail);
        }
    }

    private int orderOf(FrameworkAdapter adapter) {
        try {
            return adapter.order();
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    private String idOf(FrameworkAdapter adapter) {
        try {
            String id = adapter.id();
            return id == null || id.isBlank() ? adapter.getClass().getName() : id;
        } catch (RuntimeException e) {
            return adapter.getClass().getName();
        }
    }

    private void warn(String id, String phase, RuntimeException e) {
        if (log != null) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("adapter " + id + " " + phase + " failed: " + detail);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    @FunctionalInterface
    private interface OutcomeCall {
        AdapterOutcome run();
    }

    /** Probes {@code isAvailable} against the defining loader of a newly defined type. */
    private static final class LoaderContext implements AdapterContext {
        private final ClassLoader[] loaders;

        LoaderContext(ClassLoader loader) {
            this.loaders = loader == null ? new ClassLoader[0] : new ClassLoader[] {loader};
        }

        @Override
        public ClassLoader[] applicationLoaders() {
            return loaders;
        }

        @Override
        public boolean isLateAttach() {
            return false;
        }

        @Override
        public void log(String level, String msg) {}

        @Override
        public <T> T peekService(Class<T> type) {
            return null;
        }
    }
}

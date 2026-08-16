package io.runtimerocket.agent.reload;

import java.util.List;

/** Outcome of {@link ReloadBackend#apply} for already-loaded types. */
public final class ReloadBackendResult {

    public final List<Class<?>> redefined;

    public ReloadBackendResult(List<Class<?>> redefined) {
        this.redefined = List.copyOf(redefined);
    }
}

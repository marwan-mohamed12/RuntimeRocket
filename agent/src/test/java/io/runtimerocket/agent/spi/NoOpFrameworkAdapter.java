package io.runtimerocket.agent.spi;

import io.runtimerocket.protocol.AdapterOutcome;

/** ServiceLoader example used by agent tests. Always available and always succeeds. */
public final class NoOpFrameworkAdapter implements FrameworkAdapter {

    public static final String ID = "noop";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 1_000;
    }

    @Override
    public boolean isAvailable(ClassLoader appLoader) {
        return true;
    }

    @Override
    public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
        return AdapterOutcome.ok(id());
    }
}

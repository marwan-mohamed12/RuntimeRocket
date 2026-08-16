package io.runtimerocket.agent.spi;

import io.runtimerocket.protocol.AdapterOutcome;

/**
 * Framework recovery plugin discovered via {@code ServiceLoader}. Implementations must isolate
 * their own failures; the host still does so if they do not.
 */
public interface FrameworkAdapter {

    String id();

    /** Lower runs first. */
    int order();

    boolean isAvailable(ClassLoader appLoader);

    default void onAgentStart(AdapterContext ctx) {}

    /** Late attach: discover already-built framework state. */
    default AdapterOutcome onLateAttach(AdapterContext ctx) {
        return AdapterOutcome.ok(id());
    }

    default void onNewClass(Class<?> type) {}

    AdapterOutcome onClassesReloaded(ClassReloadEvent event);

    default AdapterOutcome onResourcesChanged(ResourceChangeEvent event) {
        return AdapterOutcome.ok(id());
    }

    default void onAgentShutdown() {}
}

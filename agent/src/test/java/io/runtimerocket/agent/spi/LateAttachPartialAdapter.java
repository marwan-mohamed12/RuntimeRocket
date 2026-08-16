package io.runtimerocket.agent.spi;

import io.runtimerocket.protocol.AdapterOutcome;

/** Test-only adapter. Off unless {@link #enable()} is called; used to assert late-attach handshake notes. */
public final class LateAttachPartialAdapter implements FrameworkAdapter {

    public static final String ID = "test-late-partial";
    public static final String DETAIL =
            "Spring adapter inactive until a request hits the app or you restart with -javaagent (premain).";

    private static volatile boolean enabled;

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 2_000;
    }

    @Override
    public boolean isAvailable(ClassLoader appLoader) {
        return enabled;
    }

    @Override
    public AdapterOutcome onLateAttach(AdapterContext ctx) {
        return new AdapterOutcome(ID, AdapterOutcome.PARTIAL, 0L, DETAIL);
    }

    @Override
    public AdapterOutcome onClassesReloaded(ClassReloadEvent event) {
        return AdapterOutcome.ok(id());
    }
}

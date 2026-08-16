package io.runtimerocket.agent.spi;

import java.util.List;
import java.util.Objects;

/** Fired after a successful define/redefine batch. Carries backend id, not the agent backend type. */
public final class ClassReloadEvent {

    public final AdapterContext ctx;
    public final List<ReloadedClass> classes;
    public final String backendId;
    public final Capabilities capabilities;

    public ClassReloadEvent(
            AdapterContext ctx, List<ReloadedClass> classes, String backendId, Capabilities capabilities) {
        this.ctx = ctx;
        this.classes = classes == null ? List.of() : List.copyOf(classes);
        this.backendId = backendId;
        this.capabilities = capabilities == null ? Capabilities.none() : capabilities;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClassReloadEvent that)) {
            return false;
        }
        return Objects.equals(ctx, that.ctx)
                && classes.equals(that.classes)
                && Objects.equals(backendId, that.backendId)
                && Objects.equals(capabilities, that.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ctx, classes, backendId, capabilities);
    }
}

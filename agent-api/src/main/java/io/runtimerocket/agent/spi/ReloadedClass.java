package io.runtimerocket.agent.spi;

import java.util.List;
import java.util.Objects;

/** One type that was defined or redefined in the current batch. */
public final class ReloadedClass {

    public final String binaryName;
    public final Class<?> type;
    public final List<String> changeKinds;

    public ReloadedClass(String binaryName, Class<?> type, List<String> changeKinds) {
        this.binaryName = binaryName;
        this.type = type;
        this.changeKinds = changeKinds == null ? List.of() : List.copyOf(changeKinds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ReloadedClass that)) {
            return false;
        }
        return Objects.equals(binaryName, that.binaryName)
                && type == that.type
                && changeKinds.equals(that.changeKinds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryName, type, changeKinds);
    }
}

package io.runtimerocket.agent.reload;

import java.util.Objects;

/** A method or field identity: JVM name plus descriptor. */
public final class MemberRef {

    public final String name;
    public final String descriptor;

    public MemberRef(String name, String descriptor) {
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MemberRef other)) {
            return false;
        }
        return name.equals(other.name) && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptor);
    }

    @Override
    public String toString() {
        return name + descriptor;
    }
}

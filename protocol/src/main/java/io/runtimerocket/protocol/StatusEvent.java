package io.runtimerocket.protocol;

import java.util.Objects;

public final class StatusEvent extends Frame {

    public static final String ATTACHED = "ATTACHED";
    public static final String RELOADING = "RELOADING";
    public static final String IDLE = "IDLE";
    public static final String SHUTDOWN = "SHUTDOWN";

    public String phase;
    public String backend;

    public StatusEvent() {
        super(FrameTypes.STATUS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StatusEvent that)) {
            return false;
        }
        return seq == that.seq
                && Objects.equals(type, that.type)
                && Objects.equals(session, that.session)
                && Objects.equals(phase, that.phase)
                && Objects.equals(backend, that.backend);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, phase, backend);
    }
}

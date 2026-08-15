package io.runtimerocket.protocol;

import java.util.List;
import java.util.Objects;

public final class ReloadResult extends Frame {

    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String RESTART_REQUIRED = "RESTART_REQUIRED";
    public static final String FAILED = "FAILED";

    public String status;
    public long durationMs;
    public List<ClassOutcome> classes;
    public List<AdapterOutcome> adapters;
    public String message;

    public ReloadResult() {
        super(FrameTypes.RELOAD_RESULT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReloadResult that)) {
            return false;
        }
        return seq == that.seq
                && durationMs == that.durationMs
                && Objects.equals(type, that.type)
                && Objects.equals(session, that.session)
                && Objects.equals(status, that.status)
                && Objects.equals(classes, that.classes)
                && Objects.equals(adapters, that.adapters)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, status, durationMs, classes, adapters, message);
    }
}

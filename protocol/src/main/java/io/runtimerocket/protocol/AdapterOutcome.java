package io.runtimerocket.protocol;

import java.util.Objects;

public final class AdapterOutcome {

    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String RESTART_REQUIRED = "RESTART_REQUIRED";

    public String adapterId;
    public String status;
    public long durationMs;
    public String detail;

    public AdapterOutcome() {}

    public AdapterOutcome(String adapterId, String status, long durationMs, String detail) {
        this.adapterId = adapterId;
        this.status = status;
        this.durationMs = durationMs;
        this.detail = detail;
    }

    public static AdapterOutcome ok(String adapterId) {
        return new AdapterOutcome(adapterId, SUCCESS, 0L, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdapterOutcome that)) {
            return false;
        }
        return durationMs == that.durationMs
                && Objects.equals(adapterId, that.adapterId)
                && Objects.equals(status, that.status)
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adapterId, status, durationMs, detail);
    }
}

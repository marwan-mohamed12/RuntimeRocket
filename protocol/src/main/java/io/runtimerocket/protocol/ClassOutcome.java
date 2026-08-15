package io.runtimerocket.protocol;

import java.util.List;
import java.util.Objects;

public final class ClassOutcome {

    public static final String REDEFINED = "REDEFINED";
    public static final String DEFINED = "DEFINED";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    public String binaryName;
    public String status;
    public List<String> changeKinds;
    public String reason;

    public ClassOutcome() {}

    public ClassOutcome(String binaryName, String status, List<String> changeKinds, String reason) {
        this.binaryName = binaryName;
        this.status = status;
        this.changeKinds = changeKinds;
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassOutcome that)) {
            return false;
        }
        return Objects.equals(binaryName, that.binaryName)
                && Objects.equals(status, that.status)
                && Objects.equals(changeKinds, that.changeKinds)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryName, status, changeKinds, reason);
    }
}

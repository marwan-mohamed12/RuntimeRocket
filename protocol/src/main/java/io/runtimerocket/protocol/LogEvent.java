package io.runtimerocket.protocol;

import java.util.Objects;

public final class LogEvent extends Frame {

    public String level;
    public String logger;
    public String message;

    public LogEvent() {
        super(FrameTypes.LOG);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LogEvent that)) {
            return false;
        }
        return seq == that.seq
                && Objects.equals(type, that.type)
                && Objects.equals(session, that.session)
                && Objects.equals(level, that.level)
                && Objects.equals(logger, that.logger)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, level, logger, message);
    }
}

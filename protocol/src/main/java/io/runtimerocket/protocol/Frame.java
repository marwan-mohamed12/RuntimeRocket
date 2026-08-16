package io.runtimerocket.protocol;

import java.util.Objects;

/** Common header on every RR/1 message. */
public class Frame {

    public String type;
    public String session;
    public long seq;

    public Frame() {}

    public Frame(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Frame frame = (Frame) o;
        return seq == frame.seq
                && Objects.equals(type, frame.type)
                && Objects.equals(session, frame.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq);
    }
}

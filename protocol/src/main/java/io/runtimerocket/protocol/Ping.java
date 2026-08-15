package io.runtimerocket.protocol;

public final class Ping extends Frame {

    public Ping() {
        super(FrameTypes.PING);
    }
}

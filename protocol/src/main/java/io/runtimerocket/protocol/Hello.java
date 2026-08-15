package io.runtimerocket.protocol;

import java.util.Objects;

public final class Hello extends Frame {

    public String token;
    public String pluginVersion;
    public String protocolVersion;

    public Hello() {
        super(FrameTypes.HELLO);
        this.protocolVersion = Protocol.VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Hello hello)) {
            return false;
        }
        return seq == hello.seq
                && Objects.equals(type, hello.type)
                && Objects.equals(session, hello.session)
                && Objects.equals(token, hello.token)
                && Objects.equals(pluginVersion, hello.pluginVersion)
                && Objects.equals(protocolVersion, hello.protocolVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, token, pluginVersion, protocolVersion);
    }
}

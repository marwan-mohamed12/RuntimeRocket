package io.runtimerocket.protocol;

import java.util.List;
import java.util.Objects;

public final class HelloOk extends Frame {

    public String backend;
    public List<String> capabilities;
    public String agentVersion;
    public String vmName;
    public String javaVersion;

    public HelloOk() {
        super(FrameTypes.HELLO_OK);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HelloOk helloOk)) {
            return false;
        }
        return seq == helloOk.seq
                && Objects.equals(type, helloOk.type)
                && Objects.equals(session, helloOk.session)
                && Objects.equals(backend, helloOk.backend)
                && Objects.equals(capabilities, helloOk.capabilities)
                && Objects.equals(agentVersion, helloOk.agentVersion)
                && Objects.equals(vmName, helloOk.vmName)
                && Objects.equals(javaVersion, helloOk.javaVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, session, seq, backend, capabilities, agentVersion, vmName, javaVersion);
    }
}

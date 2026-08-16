package io.runtimerocket.agent;

/** Agent version reported on the handshake and HelloOk. */
public final class AgentVersion {

    public static final String VERSION = read();

    private AgentVersion() {}

    private static String read() {
        String fromManifest = AgentMain.class.getPackage().getImplementationVersion();
        return fromManifest != null && !fromManifest.isBlank() ? fromManifest : "0.1.0-SNAPSHOT";
    }
}

package io.runtimerocket.agent;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.UUID;

final class AgentTestSupport {

    private static final Object INSTALL_LOCK = new Object();
    private static Instrumentation instrumentation;

    private AgentTestSupport() {}

    static Instrumentation instrumentation() {
        synchronized (INSTALL_LOCK) {
            if (instrumentation == null) {
                instrumentation = ByteBuddyAgent.install();
            }
            return instrumentation;
        }
    }

    static void start(Path watchDir, String token) {
        String args = "port=0,watch=false,token="
                + token
                + ",backend=standard,log=debug,watchDir="
                + watchDir.toAbsolutePath();
        AgentMain.premain(args, instrumentation());
    }

    static void stop() {
        AgentRuntime.get().stop();
    }

    static String token() {
        return "t" + UUID.randomUUID().toString().replace("-", "");
    }
}

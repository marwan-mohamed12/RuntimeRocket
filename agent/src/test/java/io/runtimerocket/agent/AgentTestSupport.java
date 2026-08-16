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
        start(watchDir, token, false, 150);
    }

    static void startWatching(Path watchDir, String token) {
        start(watchDir, token, true, 40);
    }

    static void start(Path watchDir, String token, boolean watch, int debounceMs) {
        start(watchDir, token, watch, debounceMs, false);
    }

    static void startLate(Path watchDir, String token) {
        start(watchDir, token, false, 150, true);
    }

    static void start(Path watchDir, String token, boolean watch, int debounceMs, boolean late) {
        String args = "port=0,watch="
                + watch
                + ",debounceMs="
                + debounceMs
                + ",token="
                + token
                + ",backend=standard,log=debug,watchDir="
                + watchDir.toAbsolutePath();
        if (late) {
            AgentMain.agentmain(args, instrumentation());
        } else {
            AgentMain.premain(args, instrumentation());
        }
    }

    static void stop() {
        AgentRuntime.get().stop();
    }

    static String token() {
        return "t" + UUID.randomUUID().toString().replace("-", "");
    }
}

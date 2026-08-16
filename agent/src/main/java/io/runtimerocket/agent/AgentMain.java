package io.runtimerocket.agent;

import java.lang.instrument.Instrumentation;

/** Java agent entry: {@code premain} at launch and {@code agentmain} for late attach. */
public final class AgentMain {

    private AgentMain() {}

    public static void premain(String args, Instrumentation inst) {
        start(args, inst, false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(args, inst, true);
    }

    private static synchronized void start(String args, Instrumentation inst, boolean late) {
        AgentOptions opt = AgentOptions.parse(args);
        AgentRuntime.get().start(opt, inst, late);
    }
}

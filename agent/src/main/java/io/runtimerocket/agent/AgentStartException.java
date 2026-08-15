package io.runtimerocket.agent;

/** Fatal start failure; the JVM should not keep a half-initialized agent. */
public final class AgentStartException extends RuntimeException {

    public AgentStartException(String message) {
        super(message);
    }

    public AgentStartException(String message, Throwable cause) {
        super(message, cause);
    }
}

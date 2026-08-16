package io.runtimerocket.agent.config;

/** Unreadable or unsupported {@code runtimerocket.xml}. */
public final class RocketXmlException extends RuntimeException {

    public RocketXmlException(String message) {
        super(message);
    }

    public RocketXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}

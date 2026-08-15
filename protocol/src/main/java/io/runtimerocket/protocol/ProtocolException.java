package io.runtimerocket.protocol;

/** Thrown when a frame cannot be encoded or decoded. */
public final class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}

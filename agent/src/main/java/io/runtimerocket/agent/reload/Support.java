package io.runtimerocket.agent.reload;

/** Result of {@link ReloadBackend#assess}. {@link #PARTIAL} is treated as unsupported for the batch. */
public enum Support {
    FULL,
    PARTIAL,
    UNSUPPORTED
}

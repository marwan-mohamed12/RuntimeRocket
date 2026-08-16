package io.runtimerocket.protocol;

/** RR/1 protocol constants. */
public final class Protocol {

    /** Value of {@link Hello#protocolVersion}. */
    public static final String VERSION = "1";

    /** Reject every framed message above this UTF-8 size (also the inline-payload cap). */
    public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private Protocol() {}
}

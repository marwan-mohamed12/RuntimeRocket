package io.runtimerocket.protocol;

/** Wire discriminators for {@link Frame#type}. */
public final class FrameTypes {

    public static final String HELLO = "hello";
    public static final String HELLO_OK = "hello-ok";
    public static final String RELOAD = "reload";
    public static final String RELOAD_RESULT = "reload-result";
    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String LOG = "log";
    public static final String STATUS = "status";
    public static final String GOODBYE = "goodbye";

    private FrameTypes() {}
}

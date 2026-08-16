package io.runtimerocket.agent.spi;

/** Services the agent exposes to a {@link FrameworkAdapter}. */
public interface AdapterContext {

    ClassLoader[] applicationLoaders();

    boolean isLateAttach();

    void log(String level, String msg);

    /** Reserved for future agent services. */
    <T> T peekService(Class<T> type);
}

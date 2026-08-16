package io.runtimerocket.agent.reload;

import java.lang.instrument.Instrumentation;
import java.util.List;

/** Agent-internal reload mechanism. Not referenced from {@code :agent-api}. */
public interface ReloadBackend {

    String id();

    List<String> capabilityNames();

    boolean probe(Instrumentation inst);

    Support assess(ClassDelta delta);

    ReloadBackendResult apply(Instrumentation inst, List<Redefinition> batch) throws Exception;
}

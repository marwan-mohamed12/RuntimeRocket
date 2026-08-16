package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.AgentStartException;

import java.lang.instrument.Instrumentation;

/**
 * Chooses a {@link ReloadBackend} from options or by probing. Versioning is never auto-selected.
 */
public final class BackendSelector {

    private BackendSelector() {}

    public static ReloadBackend select(String requested, Instrumentation inst) {
        String id = requested == null || requested.isBlank() ? AgentOptions.BACKEND_AUTO : requested;
        if (AgentOptions.BACKEND_VERSIONING.equals(id)) {
            throw new AgentStartException("versioning backend is not implemented");
        }
        if (AgentOptions.BACKEND_ENHANCED.equals(id)) {
            return require(new EnhancedHotSwapBackend(), inst, "enhanced HotSwap is not available");
        }
        if (AgentOptions.BACKEND_STANDARD.equals(id)) {
            return require(new StandardHotSwapBackend(), inst, "standard HotSwap is not available");
        }
        if (!AgentOptions.BACKEND_AUTO.equals(id)) {
            throw new AgentStartException("backend '" + id + "' is not available");
        }
        EnhancedHotSwapBackend enhanced = new EnhancedHotSwapBackend();
        if (enhanced.probe(inst)) {
            return enhanced;
        }
        return require(new StandardHotSwapBackend(), inst, "standard HotSwap is not available");
    }

    private static ReloadBackend require(ReloadBackend backend, Instrumentation inst, String failure) {
        if (!backend.probe(inst)) {
            throw new AgentStartException(failure);
        }
        return backend;
    }
}

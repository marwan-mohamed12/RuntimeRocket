package io.runtimerocket.agent.reload;

import io.runtimerocket.agent.AgentOptions;
import io.runtimerocket.agent.AgentStartException;

import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendSelectorTest {

    @Test
    void autoUsesEnhancedWhenProbeSucceeds() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {});
        ReloadBackend selected = BackendSelector.select(AgentOptions.BACKEND_AUTO, inst);
        assertEquals(EnhancedHotSwapBackend.ID, selected.id());
    }

    @Test
    void autoFallsBackToStandardWhenEnhancedProbeFails() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {
            throw new UnsupportedOperationException("no enhanced");
        });
        ReloadBackend selected = BackendSelector.select(AgentOptions.BACKEND_AUTO, inst);
        assertEquals(StandardHotSwapBackend.ID, selected.id());
    }

    @Test
    void honorsExplicitStandard() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {
            throw new UnsupportedOperationException("no enhanced");
        });
        ReloadBackend selected = BackendSelector.select(AgentOptions.BACKEND_STANDARD, inst);
        assertEquals(StandardHotSwapBackend.ID, selected.id());
    }

    @Test
    void honorsExplicitEnhancedWhenProbeSucceeds() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {});
        ReloadBackend selected = BackendSelector.select(AgentOptions.BACKEND_ENHANCED, inst);
        assertEquals(EnhancedHotSwapBackend.ID, selected.id());
    }

    @Test
    void explicitEnhancedFailsStartWhenProbeFails() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {
            throw new UnsupportedOperationException("no enhanced");
        });
        AgentStartException ex = assertThrows(
                AgentStartException.class,
                () -> BackendSelector.select(AgentOptions.BACKEND_ENHANCED, inst));
        assertTrue(ex.getMessage().contains("enhanced"), ex.getMessage());
    }

    @Test
    void explicitStandardFailsStartWhenRedefineUnsupported() {
        Instrumentation inst = FakeInstrumentation.of(false, defs -> {});
        AgentStartException ex = assertThrows(
                AgentStartException.class,
                () -> BackendSelector.select(AgentOptions.BACKEND_STANDARD, inst));
        assertTrue(ex.getMessage().contains("standard"), ex.getMessage());
    }

    @Test
    void versioningIsNeverAutoSelectedAndFailsStart() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {});
        AgentStartException ex = assertThrows(
                AgentStartException.class,
                () -> BackendSelector.select(AgentOptions.BACKEND_VERSIONING, inst));
        assertTrue(ex.getMessage().toLowerCase().contains("versioning"), ex.getMessage());
        assertEquals(EnhancedHotSwapBackend.ID, BackendSelector.select(AgentOptions.BACKEND_AUTO, inst).id());
    }

    @Test
    void unknownBackendFailsStart() {
        Instrumentation inst = FakeInstrumentation.of(true, defs -> {});
        AgentStartException ex =
                assertThrows(AgentStartException.class, () -> BackendSelector.select("dcevm", inst));
        assertTrue(ex.getMessage().contains("dcevm"), ex.getMessage());
    }
}

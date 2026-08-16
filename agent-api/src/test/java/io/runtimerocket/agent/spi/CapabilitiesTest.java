package io.runtimerocket.agent.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitiesTest {

    @Test
    void fromNamesMapsStandardHotSwapTokens() {
        Capabilities caps = Capabilities.fromNames(List.of("METHOD_BODY", "CONSTANT_POOL_ONLY", "NEW_TYPE"));
        assertTrue(caps.methodBody);
        assertFalse(caps.addRemoveMethods);
        assertFalse(caps.addRemoveFields);
        assertFalse(caps.addConstructors);
        assertFalse(caps.hierarchyChanges);
        assertFalse(caps.enumConstants);
        assertFalse(caps.anonymousRemap);
    }

    @Test
    void fromNamesMapsEnhancedHotSwapTokens() {
        Capabilities caps = Capabilities.fromNames(List.of(
                "METHOD_BODY",
                "ADD_METHOD",
                "REMOVE_METHOD",
                "ADD_FIELD",
                "REMOVE_FIELD",
                "ADD_CONSTRUCTOR",
                "REMOVE_CONSTRUCTOR",
                "HIERARCHY_SUPER",
                "ENUM_CONSTANTS"));
        assertTrue(caps.methodBody);
        assertTrue(caps.addRemoveMethods);
        assertTrue(caps.addRemoveFields);
        assertTrue(caps.addConstructors);
        assertFalse(caps.hierarchyChanges);
        assertFalse(caps.enumConstants);
        assertFalse(caps.anonymousRemap);
    }

    @Test
    void noneAndNullNamesAreAllFalse() {
        assertEquals(Capabilities.none(), Capabilities.fromNames(null));
        assertEquals(Capabilities.none(), Capabilities.fromNames(List.of()));
    }
}

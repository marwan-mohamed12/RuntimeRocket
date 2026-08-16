package io.runtimerocket.frameworks.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAdapterAvailabilityTest {

    @Test
    void availableWhenApplicationContextIsLoadable() {
        SpringAdapter adapter = new SpringAdapter();
        assertEquals(SpringAdapter.ID, adapter.id());
        assertEquals(100, adapter.order());
        assertTrue(adapter.isAvailable(SpringAdapterAvailabilityTest.class.getClassLoader()));
    }
}

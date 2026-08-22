package io.runtimerocket.frameworks.spring;

import io.runtimerocket.agent.spi.ResourceChangeEvent;
import io.runtimerocket.protocol.AdapterOutcome;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HybrisResourcePolicyTest {

    @Test
    void itemsXmlAndSpringXmlRequireRestart() {
        assertEquals(HybrisResourcePolicy.ITEMS_XML_DETAIL, HybrisResourcePolicy.restartDetailForName("cchcore-items.xml"));
        assertEquals(HybrisResourcePolicy.SPRING_XML_DETAIL, HybrisResourcePolicy.restartDetailForName("cchcore-spring.xml"));
        assertEquals(HybrisResourcePolicy.PROPERTIES_DETAIL, HybrisResourcePolicy.restartDetailForName("local.properties"));
        assertEquals(HybrisResourcePolicy.IMPEX_DETAIL, HybrisResourcePolicy.restartDetailForName("catalog.impex"));
        assertNull(HybrisResourcePolicy.restartDetailForName("Foo.java"));
    }

    @Test
    void springAdapterSurfacesHybrisItemsXml() {
        SpringAdapter adapter = new SpringAdapter();
        AdapterOutcome outcome =
                adapter.onResourcesChanged(
                        new ResourceChangeEvent(
                                SpringTestSupport.context(false),
                                List.of(
                                        new ResourceChangeEvent.ChangedResource(
                                                "cchcore-items.xml", "/ext/cchcore-items.xml", "a".repeat(64)))));
        assertEquals(AdapterOutcome.RESTART_REQUIRED, outcome.status);
        assertEquals(HybrisResourcePolicy.ITEMS_XML_DETAIL, outcome.detail);
    }
}

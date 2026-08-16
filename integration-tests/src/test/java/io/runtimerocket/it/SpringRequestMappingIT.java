package io.runtimerocket.it;

import io.runtimerocket.frameworks.spring.SpringRequestMappings;
import io.runtimerocket.frameworks.spring.SpringVersionGate;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringRequestMappingIT {

    @Test
    void framework7KeepsDetectHandlerMethodsName() throws Exception {
        assertTrue(SpringVersion.getVersion().startsWith("7."), SpringVersion.getVersion());
        assertEquals(
                SpringVersionGate.FW7_HELPER,
                SpringVersionGate.helperClassName(SpringRequestMappingIT.class.getClassLoader()));
        Class<?> api = Class.forName("io.runtimerocket.frameworks.spring.fw7.Fw7MappingApi");
        assertEquals("detectHandlerMethods", api.getMethod("detectHandlerMethodsName").invoke(null));
        assertEquals(SpringRequestMappings.SERVLET_MAPPING, api.getField("SERVLET_MAPPING").get(null));
        assertEquals(SpringRequestMappings.WEBFLUX_MAPPING, api.getField("WEBFLUX_MAPPING").get(null));
        assertFalse(
                SpringRequestMappings.webFluxMappingPresent(SpringRequestMappingIT.class.getClassLoader()),
                "this fixture is servlet-free Boot starter");
    }
}

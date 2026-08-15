package io.runtimerocket.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOptionsTest {

    @Test
    void defaults() {
        AgentOptions opt = AgentOptions.parse(null, new Properties());
        assertEquals(0, opt.port);
        assertNull(opt.tokenFile);
        assertNull(opt.token);
        assertEquals(AgentOptions.BACKEND_AUTO, opt.backend);
        assertTrue(opt.watch);
        assertEquals(150, opt.debounceMs);
        assertEquals("info", opt.log);
        assertTrue(opt.disabledAdapters.isEmpty());
        assertTrue(opt.watchDirs.isEmpty());
    }

    @Test
    void parsesCommaSeparatedKeys() {
        AgentOptions opt = AgentOptions.parse(
                "port=4242,watch=false,token=abc,backend=standard,debounceMs=200,log=debug,"
                        + "disabledAdapters=spring;foo,watchDir=/tmp/a,watchDir=/tmp/b",
                new Properties());
        assertEquals(4242, opt.port);
        assertFalse(opt.watch);
        assertEquals("abc", opt.token);
        assertEquals(AgentOptions.BACKEND_STANDARD, opt.backend);
        assertEquals(200, opt.debounceMs);
        assertEquals("debug", opt.log);
        assertEquals(2, opt.disabledAdapters.size());
        assertEquals(Path.of("/tmp/a"), opt.watchDirs.get(0));
        assertEquals(Path.of("/tmp/b"), opt.watchDirs.get(1));
    }

    @Test
    void systemPropertiesOverrideArgs() {
        Properties props = new Properties();
        props.setProperty("rr.port", "9");
        props.setProperty("rr.watch", "false");
        props.setProperty("rr.backend", "standard");
        AgentOptions opt = AgentOptions.parse("port=1,watch=true,backend=auto", props);
        assertEquals(9, opt.port);
        assertFalse(opt.watch);
        assertEquals("standard", opt.backend);
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(AgentStartException.class, () -> AgentOptions.parse("port=70000", new Properties()));
    }
}

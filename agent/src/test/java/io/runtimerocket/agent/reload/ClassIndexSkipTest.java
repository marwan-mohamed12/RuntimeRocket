package io.runtimerocket.agent.reload;

import net.bytebuddy.agent.ByteBuddyAgent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "runtimerocket-agent", mode = ResourceAccessMode.READ_WRITE)
class ClassIndexSkipTest {

    @Test
    void transformerSkipsRuntimeRocketPackages() {
        assertTrue(ClassIndex.skipBinary("io.runtimerocket.agent.AgentMain"));
        assertTrue(ClassIndex.skipInternal("io/runtimerocket/agent/reload/ClassIndex"));
        assertTrue(ClassIndex.skipBinary("io.runtimerocket.frameworks.spring.SpringAdapter"));
        assertTrue(ClassInjector.skip("io.runtimerocket.agent.spi.FrameworkAdapter"));
        assertFalse(ClassIndex.skipBinary("demo.rr.App"));

        ClassIndex index = new ClassIndex(ByteBuddyAgent.install());
        index.install();
        try {
            assertTrue(index.findAll("io.runtimerocket.agent.AgentMain").isEmpty());
            assertTrue(index.findAll("io.runtimerocket.agent.reload.ClassIndex").isEmpty());
            assertTrue(index.findAll("io.runtimerocket.agent.spi.FrameworkAdapter").isEmpty());
        } finally {
            index.uninstall();
        }
    }
}

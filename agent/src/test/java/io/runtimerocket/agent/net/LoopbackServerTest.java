package io.runtimerocket.agent.net;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackServerTest {

    @Test
    void idleTimeoutIsTenMinutesNotTwentySeconds() {
        assertEquals(Duration.ofMinutes(10), LoopbackServer.IDLE_TIMEOUT);
        assertTrue(LoopbackServer.IDLE_TIMEOUT.toSeconds() > 20);
    }
}

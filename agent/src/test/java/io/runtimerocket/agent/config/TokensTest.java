package io.runtimerocket.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokensTest {

    @TempDir
    Path temp;

    @Test
    void generateIs64HexChars() {
        String token = Tokens.generate();
        assertEquals(64, token.length());
        assertTrue(token.matches("[0-9a-f]+"));
    }

    @Test
    void equalIsTrueOnlyForSameBytes() {
        assertTrue(Tokens.equal("abc", "abc"));
        assertFalse(Tokens.equal("abc", "abd"));
        assertFalse(Tokens.equal("abc", "ab"));
        assertFalse(Tokens.equal(null, "abc"));
        assertFalse(Tokens.equal("abc", null));
    }

    @Test
    void readFileTrims() throws Exception {
        Path file = temp.resolve("token");
        Files.writeString(file, "  deadbeef  \n");
        assertEquals("deadbeef", Tokens.readFile(file));
    }
}

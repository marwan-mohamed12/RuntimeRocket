package io.runtimerocket.agent.config;

import io.runtimerocket.agent.AgentStartException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Session token helpers. Comparison is constant-time. */
public final class Tokens {

    private static final int TOKEN_BYTES = 32;

    private Tokens() {}

    public static String generate() {
        byte[] raw = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    public static String readFile(Path tokenFile) {
        try {
            String raw = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                throw new AgentStartException("token file is empty: " + tokenFile);
            }
            return raw;
        } catch (IOException e) {
            throw new AgentStartException("cannot read token file: " + tokenFile, e);
        }
    }

    public static boolean equal(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}

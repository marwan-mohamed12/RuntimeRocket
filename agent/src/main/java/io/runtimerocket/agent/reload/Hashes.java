package io.runtimerocket.agent.reload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** SHA-256 helpers for reload dedup. */
final class Hashes {

    private Hashes() {}

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static boolean equalHex(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return expected.toLowerCase(Locale.ROOT).equals(actual.toLowerCase(Locale.ROOT));
    }
}

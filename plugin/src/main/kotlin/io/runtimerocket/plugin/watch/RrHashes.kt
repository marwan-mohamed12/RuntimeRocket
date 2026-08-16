package io.runtimerocket.plugin.watch

import java.security.MessageDigest
import java.util.HexFormat

object RrHashes {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return HexFormat.of().formatHex(digest.digest(bytes))
    }
}

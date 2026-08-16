package io.runtimerocket.plugin.run

import java.time.Instant
import java.util.regex.Pattern

/** Plugin-side view of `${tmpdir}/runtimerocket/${pid}.json`. */
data class HandshakeDocument(
    val pid: Long,
    val port: Int,
    val token: String,
    val backend: String,
    val capabilities: List<String>,
    val version: String,
    val startedAt: Instant,
) {
    companion object {
        fun parse(json: String): HandshakeDocument {
            return HandshakeDocument(
                pid = longField(json, "pid"),
                port = longField(json, "port").toInt(),
                token = stringField(json, "token"),
                backend = stringField(json, "backend"),
                capabilities = stringArray(json, "capabilities"),
                version = stringField(json, "version"),
                startedAt = Instant.parse(stringField(json, "startedAt")),
            )
        }

        private fun stringField(json: String, name: String): String {
            val p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            val m = p.matcher(json)
            if (!m.find()) {
                throw IllegalArgumentException("missing handshake field: $name")
            }
            return unescape(m.group(1))
        }

        private fun longField(json: String, name: String): Long {
            val p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?\\d+)")
            val m = p.matcher(json)
            if (!m.find()) {
                throw IllegalArgumentException("missing handshake field: $name")
            }
            return m.group(1).toLong()
        }

        private fun stringArray(json: String, name: String): List<String> {
            val p = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
            val m = p.matcher(json)
            if (!m.find()) {
                return emptyList()
            }
            val body = m.group(1).trim()
            if (body.isEmpty()) {
                return emptyList()
            }
            val item = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body)
            val out = mutableListOf<String>()
            while (item.find()) {
                out.add(unescape(item.group(1)))
            }
            return out
        }

        private fun unescape(raw: String): String {
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    i++
                    sb.append(
                        when (raw[i]) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> raw[i]
                        },
                    )
                } else {
                    sb.append(c)
                }
                i++
            }
            return sb.toString()
        }
    }
}

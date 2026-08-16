package io.runtimerocket.plugin.watch

import io.runtimerocket.protocol.ClassPayload
import io.runtimerocket.protocol.Protocol
import io.runtimerocket.protocol.ProtocolCodec
import io.runtimerocket.protocol.ProtocolException
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ResourcePayload
import java.util.Base64

/** Builds a [ReloadRequest] from an [OutputSnapshot.Diff]. Resources are path+sha only. */
object ReloadRequestFactory {
    fun fromDiff(
        diff: OutputSnapshot.Diff,
        trigger: String,
        maxFrameBytes: Int = Protocol.MAX_FRAME_BYTES,
    ): ReloadRequest {
        val inlined = build(diff, trigger, byReference = false)
        if (fits(inlined, maxFrameBytes)) {
            return inlined
        }
        return build(diff, trigger, byReference = true)
    }

    private fun build(diff: OutputSnapshot.Diff, trigger: String, byReference: Boolean): ReloadRequest {
        val request = ReloadRequest()
        request.byReference = byReference
        request.trigger = trigger
        request.classes =
            diff.classes.map { changed ->
                ClassPayload(
                    changed.binaryName,
                    changed.path.toAbsolutePath().toString(),
                    changed.sha256,
                    if (byReference) null else Base64.getEncoder().encodeToString(changed.bytes),
                )
            }
        request.resources =
            diff.resources.map { changed ->
                ResourcePayload(
                    changed.classpathName,
                    changed.path.toAbsolutePath().toString(),
                    changed.sha256,
                )
            }
        return request
    }

    private fun fits(request: ReloadRequest, maxFrameBytes: Int): Boolean {
        return try {
            val encoded = ProtocolCodec.encode(request)
            encoded.toByteArray(Charsets.UTF_8).size <= maxFrameBytes
        } catch (_: ProtocolException) {
            false
        }
    }
}

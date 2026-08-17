package io.runtimerocket.plugin.watch

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.runtimerocket.protocol.AdapterOutcome
import io.runtimerocket.protocol.ClassOutcome
import io.runtimerocket.protocol.ReloadResult
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class RrReloadHistory {
    data class Event(
        val time: Instant,
        val classCount: Int,
        val resourceCount: Int,
        val status: String,
        val durationMs: Long,
        val latencyMs: Long,
        val message: String?,
        val classes: List<ClassOutcome>,
        val adapters: List<AdapterOutcome>,
        val trigger: String,
    )

    private val lock = Any()
    private val events = ArrayDeque<Event>()
    private val lastReloaded = ConcurrentHashMap<String, Instant>()

    @Volatile
    var lastMissingOutput: String? = null

    @Volatile
    var lastSteps: List<String> = emptyList()

    fun beginSteps() {
        lastSteps = emptyList()
    }

    fun addStep(line: String) {
        lastSteps = lastSteps + line
    }

    @Volatile
    var restartBannerGeneration: Int = 0
        private set

    @Volatile
    var dismissedRestartGeneration: Int = 0

    fun record(event: Event) {
        synchronized(lock) {
            events.addFirst(event)
            while (events.size > MAX_EVENTS) {
                events.removeLast()
            }
        }
        if (event.status == ReloadResult.SUCCESS || event.status == ReloadResult.PARTIAL) {
            for (outcome in event.classes) {
                if (outcome.status == ClassOutcome.REDEFINED || outcome.status == ClassOutcome.DEFINED) {
                    val name = outcome.binaryName ?: continue
                    lastReloaded[name] = event.time
                }
            }
        }
        if (event.status == ReloadResult.RESTART_REQUIRED || event.status == ReloadResult.FAILED) {
            restartBannerGeneration += 1
        }
    }

    fun latest(): Event? = synchronized(lock) { events.firstOrNull() }

    fun recent(): List<Event> = synchronized(lock) { events.toList() }

    fun lastReloadedAt(binaryName: String): Instant? = lastReloaded[binaryName]

    fun clearGutter(binaryNames: Collection<String>) {
        for (name in binaryNames) {
            lastReloaded.remove(name)
        }
    }

    fun showRestartBanner(): Boolean {
        val last = latest() ?: return false
        return (last.status == ReloadResult.RESTART_REQUIRED || last.status == ReloadResult.FAILED) &&
            restartBannerGeneration != dismissedRestartGeneration
    }

    fun dismissRestartBanner() {
        dismissedRestartGeneration = restartBannerGeneration
    }

    companion object {
        const val MAX_EVENTS = 50

        fun getInstance(project: Project): RrReloadHistory {
            return project.getService(RrReloadHistory::class.java)
        }
    }
}

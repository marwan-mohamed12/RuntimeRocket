package io.runtimerocket.plugin.watch

import io.runtimerocket.protocol.ReloadResult

/** Pure next-step rules for one-click Reload. */
object RrReloadPlan {
    enum class Step {
        HOT_RELOAD,
        BUILD,
        DIAGNOSE,
        DONE,
    }

    enum class Outcome {
        SUCCESS,
        PARTIAL,
        RESTART_REQUIRED,
        EMPTY,
        COMPILE_FAILED,
        SEND_FAILED,
        NOT_ATTACHED,
        REAL_ERRORS,
    }

    fun outcomeOf(status: String?, emptyDiff: Boolean, attached: Boolean): Outcome {
        if (!attached) {
            return Outcome.NOT_ATTACHED
        }
        if (emptyDiff) {
            return Outcome.EMPTY
        }
        return when (status?.uppercase()) {
            ReloadResult.SUCCESS -> Outcome.SUCCESS
            ReloadResult.PARTIAL -> Outcome.PARTIAL
            ReloadResult.RESTART_REQUIRED -> Outcome.RESTART_REQUIRED
            ReloadResult.FAILED -> Outcome.SEND_FAILED
            else -> Outcome.SEND_FAILED
        }
    }

    fun next(step: Step, outcome: Outcome): Step {
        return when (step) {
            Step.HOT_RELOAD ->
                when (outcome) {
                    Outcome.SUCCESS, Outcome.PARTIAL, Outcome.RESTART_REQUIRED, Outcome.NOT_ATTACHED, Outcome.REAL_ERRORS ->
                        Step.DONE
                    Outcome.EMPTY, Outcome.COMPILE_FAILED, Outcome.SEND_FAILED -> Step.BUILD
                }
            Step.BUILD ->
                when (outcome) {
                    Outcome.SUCCESS, Outcome.PARTIAL, Outcome.RESTART_REQUIRED, Outcome.NOT_ATTACHED, Outcome.REAL_ERRORS ->
                        Step.DONE
                    Outcome.EMPTY, Outcome.COMPILE_FAILED, Outcome.SEND_FAILED -> Step.DIAGNOSE
                }
            Step.DIAGNOSE, Step.DONE -> Step.DONE
        }
    }

    fun stopReason(outcome: Outcome): String? {
        return when (outcome) {
            Outcome.SUCCESS -> null
            Outcome.PARTIAL -> null
            Outcome.RESTART_REQUIRED ->
                "Stopped — compiling again will not apply this edit. Restart the process."
            Outcome.NOT_ATTACHED -> "App is not attached. Start it and use Attach."
            Outcome.REAL_ERRORS -> "Compile still has real errors. Fix the sources, then Reload."
            else -> null
        }
    }

    fun stepTitle(step: Step): String {
        return when (step) {
            Step.HOT_RELOAD -> "1. Hot reload"
            Step.BUILD -> "2. Incremental build"
            Step.DIAGNOSE -> "3. Diagnose and retry"
            Step.DONE -> "Done"
        }
    }
}

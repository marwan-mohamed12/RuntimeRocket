package io.runtimerocket.plugin.run

import java.nio.file.Path

/** VM args injected by [RrJavaProgramPatcher]. Token is passed only via tokenFile. */
object RrVmArguments {
    const val ADD_OPENS_LANG = "--add-opens=java.base/java.lang=ALL-UNNAMED"
    const val ADD_OPENS_REFLECT = "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    const val ENHANCED_REDEFINITION = "-XX:+AllowEnhancedClassRedefinition"

    fun javaAgent(agentJar: Path, tokenFile: Path, logLevel: String): String {
        val jar = agentJar.toAbsolutePath().toString()
        val token = tokenFile.toAbsolutePath().toString()
        return "-javaagent:$jar=tokenFile=$token,log=$logLevel"
    }

    fun build(agentJar: Path, tokenFile: Path, logLevel: String, enhanced: Boolean): List<String> {
        val args = mutableListOf(javaAgent(agentJar, tokenFile, logLevel), ADD_OPENS_LANG, ADD_OPENS_REFLECT)
        if (enhanced) {
            args.add(ENHANCED_REDEFINITION)
        }
        return args
    }

    fun containsRawToken(args: List<String>): Boolean {
        return args.any { arg ->
            rawTokenOnCommandLine(arg)
        }
    }

    fun rawTokenOnCommandLine(arg: String): Boolean {
        if (arg.startsWith("-Drr.token=")) {
            return true
        }
        // tokenFile= is the only allowed token-related option on -javaagent.
        if (arg.startsWith("-javaagent:")) {
            val options = arg.substringAfter('=', "")
            return options.split(',').any { part ->
                val key = part.substringBefore('=', "").trim()
                key == "token"
            }
        }
        return false
    }
}

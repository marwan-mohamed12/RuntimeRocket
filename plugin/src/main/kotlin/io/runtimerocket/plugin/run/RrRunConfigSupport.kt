package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

object RrRunConfigSupport {
    const val APPLICATION_TYPE_ID = "Application"
    const val JAR_APPLICATION_TYPE_ID = "JarApplication"
    const val SPRING_BOOT_TYPE_ID = "SpringBootApplicationConfigurationType"
    const val JUNIT_TYPE_ID = "JUnit"
    const val SPRING_BOOT_PLUGIN_ID = "com.intellij.spring.boot"

    fun typeId(profile: RunProfile): String? {
        return (profile as? RunConfiguration)?.type?.id
    }

    fun isApplicationFamily(profile: RunProfile): Boolean {
        val id = typeId(profile) ?: return false
        return id == APPLICATION_TYPE_ID || id == JAR_APPLICATION_TYPE_ID || isSpringBoot(id)
    }

    fun isSpringBoot(typeId: String): Boolean = typeId == SPRING_BOOT_TYPE_ID

    fun isJUnit(typeId: String): Boolean = typeId == JUNIT_TYPE_ID

    fun isJUnit(profile: RunProfile): Boolean {
        val id = typeId(profile) ?: return false
        return isJUnit(id)
    }

    fun isPatchable(profile: RunProfile, includeTests: Boolean): Boolean {
        return isPatchableType(typeId(profile), includeTests)
    }

    fun isPatchableType(typeId: String?, includeTests: Boolean): Boolean {
        if (typeId == null) {
            return false
        }
        return when {
            typeId == APPLICATION_TYPE_ID || typeId == JAR_APPLICATION_TYPE_ID -> true
            isSpringBoot(typeId) -> springBootPluginPresent()
            isJUnit(typeId) -> includeTests
            else -> false
        }
    }

    fun defaultEnabled(typeId: String?, includeTests: Boolean): Boolean {
        if (typeId == null) {
            return false
        }
        return when {
            typeId == APPLICATION_TYPE_ID || typeId == JAR_APPLICATION_TYPE_ID -> true
            isSpringBoot(typeId) -> true
            isJUnit(typeId) -> includeTests
            else -> false
        }
    }

    fun springBootPluginPresent(): Boolean {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(SPRING_BOOT_PLUGIN_ID))
        return plugin != null && plugin.isEnabled
    }
}

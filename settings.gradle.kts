pluginManagement {
    val kotlinVersion = providers.gradleProperty("kotlin.version").get()
    val shadowVersion = providers.gradleProperty("shadow.version").get()
    val intellijPlatformVersion = providers.gradleProperty("intellij.platform.version").get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("com.gradleup.shadow") version shadowVersion
        id("org.jetbrains.intellij.platform") version intellijPlatformVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "runtimerocket"
include(
    "protocol",
    "agent-api",
    "agent",
    "frameworks:spring",
    "plugin",
    "fixtures:plain-java",
    "fixtures:spring-boot",
    "fixtures:two-module",
    "integration-tests",
)

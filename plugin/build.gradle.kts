plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

// Java toolchain is supplied by org.jetbrains.intellij.platform (Java 21).
// Do not inherit java.toolchain=17.

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":protocol"))

    intellijPlatform {
        create(
            providers.gradleProperty("platform.type"),
            providers.gradleProperty("platform.version"),
        )
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "RuntimeRocket"
        ideaVersion {
            sinceBuild = providers.gradleProperty("plugin.since.build")
            untilBuild = providers.gradleProperty("plugin.until.build")
        }
    }
}

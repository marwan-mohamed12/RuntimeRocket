plugins {
    kotlin("jvm") apply false
    id("com.gradleup.shadow") apply false
    id("org.jetbrains.intellij.platform") apply false
}

group = "io.runtimerocket"
version = providers.gradleProperty("rr.version").get()

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

// Java 17 toolchain is set per non-plugin module. Do not pin :plugin here —
// org.jetbrains.intellij.platform supplies the Java 21 toolchain.
subprojects {
    pluginManager.withPlugin("java") {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}

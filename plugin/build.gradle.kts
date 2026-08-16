import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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

val agentResourceDir = layout.buildDirectory.dir("generated/agent-resource")

val syncAgentIntoPlugin =
    tasks.register<Copy>("syncAgentIntoPlugin") {
        group = "build"
        description = "Copy the :agent shadow JAR into plugin resources as runtimerocket-agent.jar"
        val shadowJar = project(":agent").tasks.named("shadowJar")
        dependsOn(shadowJar)
        from(shadowJar)
        into(agentResourceDir.map { it.dir("io/runtimerocket/plugin/agent") })
        rename { "runtimerocket-agent.jar" }
    }

sourceSets {
    main {
        resources.srcDir(agentResourceDir)
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
        testFramework(TestFrameworkType.Platform)
    }

    val junitVersion = providers.gradleProperty("junit.version").get()
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // IJPL-159134: JUnit5 Test Framework still references JUnit 4 TestCase.
    testImplementation("junit:junit:4.13.2")
    // IJPL-157292: TestFrameworkType.Platform does not pull opentest4j.
    testImplementation("org.opentest4j:opentest4j:1.3.0")
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

tasks.named("processResources") {
    dependsOn(syncAgentIntoPlugin)
}

tasks.named("processTestResources") {
    dependsOn(syncAgentIntoPlugin)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    dependsOn(syncAgentIntoPlugin)
}

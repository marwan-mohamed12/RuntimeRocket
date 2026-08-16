plugins {
    `java-library`
    id("com.gradleup.shadow")
}

base {
    archivesName.set("runtimerocket-agent")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

dependencies {
    api(project(":protocol"))
    api(project(":agent-api"))
    implementation(project(":frameworks:spring"))
    implementation("org.ow2.asm:asm:${providers.gradleProperty("asm.version").get()}")
    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.bytebuddy:byte-buddy-agent:${providers.gradleProperty("bytebuddy.version").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes(
            mapOf(
                "Premain-Class" to "io.runtimerocket.agent.AgentMain",
                "Agent-Class" to "io.runtimerocket.agent.AgentMain",
                "Can-Redefine-Classes" to "true",
                "Can-Retransform-Classes" to "true",
                "Implementation-Title" to "RuntimeRocket Agent",
                "Implementation-Version" to project.version.toString(),
            ),
        )
    }
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    dependsOn(tasks.shadowJar)
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "-Djdk.attach.allowAttachSelf=true",
    )
}

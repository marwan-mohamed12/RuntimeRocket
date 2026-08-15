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
    implementation("org.ow2.asm:asm:${providers.gradleProperty("asm.version").get()}")
    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

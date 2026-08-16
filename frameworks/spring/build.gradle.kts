plugins {
    `java-library`
}

base {
    archivesName.set("runtimerocket-frameworks-spring")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

val fw7: SourceSet = sourceSets.create("fw7")

dependencies {
    api(project(":agent-api"))

    // Main compileOnly is Boot 3.5 / Framework 6.2 only — never mix 7.x onto this configuration.
    compileOnly("org.springframework:spring-context:${providers.gradleProperty("spring.framework.version").get()}")
    compileOnly("org.springframework.boot:spring-boot:${providers.gradleProperty("spring.boot.version").get()}")
    compileOnly("org.ow2.asm:asm:${providers.gradleProperty("asm.version").get()}")

    add(fw7.compileOnlyConfigurationName, project(":agent-api"))
    add(fw7.compileOnlyConfigurationName, "org.springframework:spring-context:${providers.gradleProperty("spring.framework.fw7.version").get()}")
    add(fw7.compileOnlyConfigurationName, "org.springframework.boot:spring-boot:${providers.gradleProperty("spring.boot.fw7.version").get()}")
    add(fw7.compileOnlyConfigurationName, "org.springframework:spring-webmvc:${providers.gradleProperty("spring.framework.fw7.version").get()}")

    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-context:${providers.gradleProperty("spring.framework.version").get()}")
    testImplementation("org.springframework:spring-webmvc:${providers.gradleProperty("spring.framework.version").get()}")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("org.springframework.boot:spring-boot-starter:${providers.gradleProperty("spring.boot.version").get()}")
    testImplementation("org.ow2.asm:asm:${providers.gradleProperty("asm.version").get()}")
    testImplementation("net.bytebuddy:byte-buddy-agent:${providers.gradleProperty("bytebuddy.version").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// fw7 is a separate compile classpath (Framework 7). Expose its classes as extra main
// output so project consumers and the agent fat JAR see the helpers.
sourceSets.named("main") {
    output.dir(mapOf("builtBy" to "compileFw7Java"), fw7.output.classesDirs)
}

tasks.named("check") {
    dependsOn(tasks.named("compileFw7Java"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "-Djdk.attach.allowAttachSelf=true",
    )
}

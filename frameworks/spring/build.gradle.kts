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

    add(fw7.compileOnlyConfigurationName, project(":agent-api"))
    add(fw7.compileOnlyConfigurationName, "org.springframework:spring-context:${providers.gradleProperty("spring.framework.fw7.version").get()}")
    add(fw7.compileOnlyConfigurationName, "org.springframework.boot:spring-boot:${providers.gradleProperty("spring.boot.fw7.version").get()}")

    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fw7.compileClasspath += sourceSets.main.get().output

tasks.named("check") {
    dependsOn(tasks.named("compileFw7Java"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

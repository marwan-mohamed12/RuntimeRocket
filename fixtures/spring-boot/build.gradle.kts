plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

val boot35: SourceSet = sourceSets.create("boot35") {
    java.srcDir("src/main/java")
}

dependencies {
    // Primary fixture line is Boot 4.1 / Framework 7 — never mix 3.5 onto this configuration.
    implementation("org.springframework.boot:spring-boot-starter:${providers.gradleProperty("spring.boot.fw7.version").get()}")

    add(
        boot35.compileOnlyConfigurationName,
        "org.springframework.boot:spring-boot-starter:${providers.gradleProperty("spring.boot.version").get()}",
    )
}

tasks.register("boot35Test") {
    group = "verification"
    description = "Compile the fixture against Boot 3.5 / Framework 6.2"
    dependsOn("compileBoot35Java")
}

tasks.named("check") {
    dependsOn("boot35Test")
}

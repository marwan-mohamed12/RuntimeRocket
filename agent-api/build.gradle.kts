plugins {
    `java-library`
}

base {
    archivesName.set("runtimerocket-agent-api")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

dependencies {
    api(project(":protocol"))
    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

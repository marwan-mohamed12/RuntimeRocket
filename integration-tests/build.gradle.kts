plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

dependencies {
    testImplementation(project(":agent"))
    testImplementation(project(":fixtures:plain-java"))
    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junit.version").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

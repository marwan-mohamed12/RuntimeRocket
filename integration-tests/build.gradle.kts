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
    testImplementation("net.bytebuddy:byte-buddy-agent:${providers.gradleProperty("bytebuddy.version").get()}")
    testImplementation("org.ow2.asm:asm:${providers.gradleProperty("asm.version").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "-Djdk.attach.allowAttachSelf=true",
        "-XX:+IgnoreUnrecognizedVMOptions",
        "-XX:+AllowEnhancedClassRedefinition",
    )
    val testJavaHome = providers.gradleProperty("rr.testJavaHome")
    if (testJavaHome.isPresent) {
        val home = testJavaHome.get().trimEnd('/', '\\')
        val exe = if (System.getProperty("os.name").lowercase().startsWith("windows")) "java.exe" else "java"
        executable = "$home/bin/$exe"
    }
}

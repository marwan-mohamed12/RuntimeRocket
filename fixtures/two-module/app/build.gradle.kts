plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain").get()))
    }
}

dependencies {
    implementation(project(":fixtures:two-module:lib"))
}

val writeRocketXml = tasks.register("writeRocketXml") {
    dependsOn(tasks.named("compileJava"))
    val outputDir = layout.buildDirectory.dir("classes/java/main")
    val xmlFile = outputDir.map { it.file("runtimerocket.xml") }
    outputs.file(xmlFile)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        val path = dir.canonicalPath.replace('\\', '/')
        dir.resolve("runtimerocket.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <runtimerocket xmlns="https://runtimerocket.io/ns/config" version="1">
              <id>runtimerocket:two-module-app</id>
              <classpath>
                <dir name="$path"/>
              </classpath>
              <packages>
                <include>demo.twomodule.app.**</include>
              </packages>
            </runtimerocket>
            """.trimIndent() + "\n",
        )
    }
}

tasks.named("classes") {
    dependsOn(writeRocketXml)
}

tasks.named("jar") {
    dependsOn(writeRocketXml)
}

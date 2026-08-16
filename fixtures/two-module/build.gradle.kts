// Aggregator: lib + app each emit a runtimerocket.xml into their compiler output.
tasks.register("classes") {
    dependsOn(":fixtures:two-module:lib:classes", ":fixtures:two-module:app:classes")
}

tasks.register("build") {
    dependsOn(":fixtures:two-module:lib:build", ":fixtures:two-module:app:build")
}

tasks.register("clean") {
    dependsOn(":fixtures:two-module:lib:clean", ":fixtures:two-module:app:clean")
}

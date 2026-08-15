# RuntimeRocket

JRebel-style JVM class reload for IntelliJ IDEA: after a successful compile,
push changed `.class` files into a running application without a restart.

This repository is in early development. The Gradle multi-module skeleton
and legal files exist; agent, protocol, and plugin behavior are **not**
implemented yet. The approved design is in [docs/design.md](docs/design.md).

## Requirements

- Windows (primary), macOS, or Linux
- A JDK 17+ to launch Gradle. The build uses toolchains: **Java 17** for
  agent / protocol / fixtures / tests, and **Java 21** for `:plugin`
  (IntelliJ IDEA 2024.3+ is a Java 21 IDE).
- Internet access on the first build (Gradle distribution, Maven Central,
  and later the IntelliJ Platform SDK)

## Build (Windows PowerShell)

List the Gradle projects:

```powershell
.\gradlew.bat projects
```

Compile the Java 17 libraries (placeholders only):

```powershell
.\gradlew.bat :protocol:compileJava :agent-api:compileJava :agent:compileJava
```

Later, once those modules have real sources:

```powershell
.\gradlew.bat :plugin:buildPlugin :agent:shadowJar
```

On macOS / Linux, use `./gradlew` with the same tasks.

## Modules

| Module | Role |
| --- | --- |
| `:protocol` | IDE ↔ agent wire types (Java 17) |
| `:agent-api` | Framework adapter SPI (Java 17) |
| `:agent` | Java agent; Shadow fat JAR (Java 17) |
| `:frameworks:spring` | Spring adapter. Main `compileOnly` is Boot 3.5 / Framework 6.2; source set `fw7` is Boot 4.1 / Framework 7 |
| `:plugin` | IntelliJ Platform plugin (Kotlin / JVM 21). Targets IDEA 2024.3–2026.2 (`243`–`262.*`) |
| `:fixtures:plain-java` | Sample plain-Java app |
| `:fixtures:spring-boot` | Sample Spring Boot app |
| `:fixtures:two-module` | Multi-module fixture |
| `:integration-tests` | Forked-JVM tests |

## License

Apache License 2.0. See [LICENSE](LICENSE), [NOTICE](NOTICE), and
[docs/legal.md](docs/legal.md) (license matrix and DCO; no CLA).

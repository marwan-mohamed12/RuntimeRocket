# RuntimeRocket

JRebel-style JVM class reload for IntelliJ IDEA: after a successful compile,
push changed `.class` files into a running application without a restart.

**0.1.0-SNAPSHOT** in this tree is dogfoodable. Install the plugin zip, enable
RuntimeRocket on an Application run configuration, compile, and keep the
process running. Architecture and the support matrix live in
[docs/design.md](docs/design.md). Day-to-day UX, `RESTART_REQUIRED`, the
standalone agent, and late attach are in [docs/user-guide.md](docs/user-guide.md).

This is **not** a JetBrains Marketplace listing. There is no Hibernate/JPA
adapter and no stock-JDK versioning backend in 0.1.0.

## What 0.1.0 reloads

| Target JVM | What reloads | What does not |
| --- | --- | --- |
| **JetBrains Runtime / DCEVM** with `-XX:+AllowEnhancedClassRedefinition` | Method bodies; add/remove/rename methods; add/remove fields (new fields stay at Java defaults); add constructors; new classes | Superclass / interface changes; add/remove/reorder **enum constants**; annotation-only edits; record components; lambda/anonymous index shifts |
| **Stock JDK** (Temurin, Oracle, …) | Method-body changes only | Every structural change is `RESTART_REQUIRED` — never silently ignored |

Supported reloads keep object identity, existing field values, and Spring
singleton instances. Constructors and `<clinit>` are **not** re-run.

Spring (Boot 3.5 / Framework 6.2 and Boot 4.1 / Framework 7): new
`@Service` / `@Component` types, keep existing singletons, rebuild
controller mappings, best-effort proxy recreate. Late-attach Spring refresh
is best-effort (usually needs `-javaagent` at start).

## Requirements

- Windows (primary), macOS, or Linux
- A JDK 17+ to launch Gradle. The build uses toolchains: **Java 17** for
  agent / protocol / fixtures / tests, and **Java 21** for `:plugin`
  (IntelliJ IDEA 2024.3+ is a Java 21 IDE).
- IntelliJ IDEA **2024.3–2026.2** (Community or Ultimate). The plugin
  *targets* `243`–`262.*`; CI `pluginVerifier` currently runs only against
  **IC-2024.3**. 2026.2 verification is a follow-up (unified `IntellijIdea`
  type after IC installers ended at 2025.3).
- Internet access on the first build (Gradle distribution, Maven Central,
  IntelliJ Platform SDK)
- For add-method / add-field: **JetBrains Runtime** (the JRE bundled with
  IntelliJ, or a standalone JBR) as the run configuration JRE

## Build

### Windows PowerShell

List the Gradle projects:

```powershell
.\gradlew.bat projects
```

Compile, test, and package the agent fat JAR:

```powershell
.\gradlew.bat :protocol:test :agent-api:test :agent:test :frameworks:spring:test :plugin:test :agent:shadowJar
```

Build the installable plugin zip (first run downloads the IntelliJ Platform SDK):

```powershell
.\gradlew.bat :plugin:buildPlugin
```

The zip is `plugin\build\distributions\plugin-0.1.0-SNAPSHOT.zip`.
The standalone agent is
`agent\build\libs\runtimerocket-agent-0.1.0-SNAPSHOT-all.jar`.

### macOS / Linux

Same tasks with `./gradlew`:

```bash
./gradlew :protocol:test :agent-api:test :agent:test :frameworks:spring:test :plugin:test :agent:shadowJar
./gradlew :plugin:buildPlugin
```

The zip is `plugin/build/distributions/plugin-0.1.0-SNAPSHOT.zip`.

## Install the plugin from disk

0.1.0 is installed from this zip, not from the Marketplace.

1. Build `:plugin:buildPlugin` (above).
2. In IntelliJ: **Settings | Plugins** → gear → **Install Plugin from Disk…**
3. Choose `plugin/build/distributions/plugin-0.1.0-SNAPSHOT.zip`.
4. Restart the IDE when prompted.

You can also run a sandboxed IDE with the plugin already loaded:

```powershell
.\gradlew.bat :plugin:runIde
```

## Enable RuntimeRocket on an Application run configuration

1. Open **Settings | Tools | RuntimeRocket** and leave **Enable RuntimeRocket**
   checked (project default is on).
2. Open your **Application** or **Jar Application** run configuration.
3. Open the **RuntimeRocket** tab and keep **Enable RuntimeRocket** checked.
   New Application configs default to on. On IntelliJ Ultimate, a **Spring Boot**
   run configuration gets the same checkbox when the Spring Boot plugin is present.
4. Point the configuration JRE at **JetBrains Runtime** if you want enhanced
   HotSwap (add methods/fields). The plugin never silently swaps the JRE; it
   offers “Use bundled JetBrains Runtime” once per project.
5. Run or Debug. The status bar should show `RR … waiting for agent`, then
   `RR ● enhanced` (or `RR ● standard` on a stock JDK).
6. Edit a Java class, **Build | Build Project** (or rely on automatic build).
   The status bar shows `RR ✓ 142 ms` on success. Click it to open the
   RuntimeRocket tool window.

JUnit is off unless you enable **Include test output** in project settings.
**Gradle `bootRun` / `JavaExec` is not patched** — see Limitations.

## JetBrains Runtime (enhanced HotSwap)

On a detected JBR / DCEVM, the plugin injects:

```
-XX:+AllowEnhancedClassRedefinition
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
-javaagent:<unpacked-agent.jar>=tokenFile=<0600-token>,log=info
```

On a stock JDK the enhanced flag is omitted. Method-body changes still
reload; adding a method or field is `RESTART_REQUIRED` with a Restart action.

You can add `-XX:+AllowEnhancedClassRedefinition` yourself if you launch
outside the plugin (standalone agent, or a forked process the patcher never
sees).

## Limitations (v1)

- **Gradle `bootRun` / `JavaExec` is not patched.** Forked launchers never
  see `JavaProgramPatcher`. Pass `-javaagent` yourself or use an Application
  run configuration. A Gradle plugin is a later PR.
- **Stock JDK is method-body only.** Structural edits need JBR/DCEVM.
- **Adding, removing, or reordering enum constants is `RESTART_REQUIRED`**
  even on JBR (stale `$VALUES` / `values()`).
- **Late-attach Spring is best-effort.** Classes still redefine; bean and
  mapping refresh usually need `premain` (`-javaagent` at start). An idle
  Boot process reports `PARTIAL` rather than pretending to be live.
- Superclass / implemented-interface changes are not supported.
- Constructors and `<clinit>` are never re-run. New static fields stay at
  defaults.
- Jackson / `ReflectionUtils` caches, `@ConfigurationProperties` rebind,
  `@Scheduled` / `@EventListener` rescan, Hibernate/JPA, and a stock-JDK
  versioning backend are **not** in 0.1.0.
- Not for production. The agent refuses to start if
  `RUNTIMEROCKET_ALLOW_NONDEV` is unset and a platform production heuristic
  matches (k8s / Azure / Heroku / `-Drr.production`).

## Modules

| Module | Role |
| --- | --- |
| `:protocol` | IDE ↔ agent wire types (Java 17) |
| `:agent-api` | Framework adapter SPI (Java 17) |
| `:agent` | Java agent; Shadow fat JAR (Java 17) |
| `:frameworks:spring` | Spring adapter. Main `compileOnly` is Boot 3.5 / Framework 6.2; source set `fw7` is Boot 4.1 / Framework 7 |
| `:plugin` | IntelliJ Platform plugin (Kotlin / JVM 21). Targets IDEA 2024.3–2026.2 (`243`–`262.*`); CI verifies **IC-2024.3** only |
| `:fixtures:plain-java` | Sample plain-Java app |
| `:fixtures:spring-boot` | Sample Spring Boot app (Boot 4.1 + Boot 3.5 test source set) |
| `:fixtures:two-module` | Multi-module fixture (lib + app, two `runtimerocket.xml`) |
| `:integration-tests` | Forked-JVM tests (JBR enhanced job in CI) |

## License

Apache License 2.0. See [LICENSE](LICENSE), [NOTICE](NOTICE), and
[docs/legal.md](docs/legal.md) (license matrix and DCO; no CLA).

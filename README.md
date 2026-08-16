# RuntimeRocket

JRebel-style JVM class reload for IntelliJ IDEA: after a successful compile,
push changed `.class` files into a running application without a restart.

**0.1.0-SNAPSHOT** in this tree is dogfoodable. Install the plugin zip, then
follow [docs/getting-started.md](docs/getting-started.md) — IntelliJ
**Run** for a normal app, or **Attach** for `hybrisserver` / a terminal JVM.
SAP Commerce steps: [docs/hybris.md](docs/hybris.md). Architecture is in
[docs/design.md](docs/design.md). Status bar, `RESTART_REQUIRED`, and the
standalone agent: [docs/user-guide.md](docs/user-guide.md).

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

## Install the plugin into IntelliJ

0.1.0 is installed from this zip, **not** from the JetBrains Marketplace.
You need IntelliJ IDEA **2024.3–2026.2** (Community or Ultimate).

### 1. Build the plugin zip

```powershell
.\gradlew.bat :plugin:buildPlugin
```

```bash
./gradlew :plugin:buildPlugin
```

The zip is `plugin/build/distributions/plugin-0.1.0-SNAPSHOT.zip`
(`plugin\build\distributions\plugin-0.1.0-SNAPSHOT.zip` on Windows).

### 2. Install from disk

1. Open IntelliJ.
2. **File | Settings** (macOS: **IntelliJ IDEA | Settings**).
3. **Plugins**.
4. Click the **gear** next to Marketplace / Installed.
5. **Install Plugin from Disk…**
6. Choose `plugin/build/distributions/plugin-0.1.0-SNAPSHOT.zip`.
7. Restart the IDE when prompted.

After restart, **Settings | Plugins | Installed** should list **RuntimeRocket**.

### 3. Turn it on for the project

1. **Settings | Tools | RuntimeRocket**.
2. Leave **Enable RuntimeRocket** checked (project default is on).
3. Keep **Reload after a successful compile** on.
4. Leave **Include test output** off unless you want JUnit reloads.

### 4. Enable it on the run configuration

1. **Run | Edit Configurations…**
2. Select your **Application** or **Jar Application** configuration.
   On IntelliJ Ultimate, a **Spring Boot** run configuration gets the same
   checkbox when the Spring Boot plugin is present.
3. Open the **RuntimeRocket** tab and keep **Enable RuntimeRocket** checked.
   New Application configs default to on once the project setting is enabled.
4. Point the configuration JRE at **JetBrains Runtime** if you want enhanced
   HotSwap (add methods/fields). The plugin never silently swaps the JRE; it
   offers **Use bundled JetBrains Runtime** once per project.

`hybrisserver`, Gradle `bootRun`, and `JavaExec` are **not** patched.
Pick a path in [docs/getting-started.md](docs/getting-started.md).
Hybris: [docs/hybris.md](docs/hybris.md).

### 5. Verify it attached

1. **Run** or **Debug** that configuration.
2. The status bar should go `RR … waiting for agent`, then `RR ● enhanced`
   (JBR / DCEVM) or `RR ● standard` (stock JDK).
3. Change a Java method and **Build | Build Project** (or rely on automatic
   build).
4. The status bar should show `RR ✓ … ms`. Click it to open
   **View | Tool Windows | RuntimeRocket**.

If you see `RR ○ not attached`, the process never got `-javaagent` (wrong
run-configuration type, or a forked Gradle JVM). Compiling then does **not**
reload.

### Sandbox IDE (no install into your main IntelliJ)

```powershell
.\gradlew.bat :plugin:runIde
```

That starts a throwaway IntelliJ with the plugin already loaded. Your
installed IDE is unchanged.

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

- **Gradle `bootRun` / `JavaExec` / `hybrisserver` is not patched.**
  Forked launchers never see `JavaProgramPatcher`. Attach after start
  ([getting-started](docs/getting-started.md#path-b--attach-to-a-process-already-running))
  or pass `-javaagent` yourself. Hybris: [docs/hybris.md](docs/hybris.md).
  A Gradle plugin is a later PR.
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

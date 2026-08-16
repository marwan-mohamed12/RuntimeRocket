# RuntimeRocket user guide

Day-to-day use of **0.1.0-SNAPSHOT**. Build and install steps are in the
[README](../README.md). Architecture and the full support matrix are in
[docs/design.md](design.md). `runtimerocket.xml` schema is in
[docs/runtimerocket-xml.md](runtimerocket-xml.md).

## Flow-state UX

RuntimeRocket is meant to stay out of the way on success and be loud on
failure. Success never pretends a reload happened when the agent is not
attached.

### Status bar (`RR …`)

Click the widget to open the RuntimeRocket tool window.

| Widget | Meaning |
| --- | --- |
| `RR ○ not attached` | No agent session. A compile does **not** reload. |
| `RR … waiting for agent` | Process started; handshake file not seen yet (up to 90 s). |
| `RR ● enhanced` | Attached; JBR/DCEVM enhanced HotSwap. |
| `RR ● standard` | Attached; stock JDK, method-body only. |
| `RR ⚙ compiling` | Compile in flight (not part of the reload SLO). |
| `RR ↻ N classes` | Reload in flight. |
| `RR ✓ 142 ms` | Last reload succeeded. Time is compile-finished → status bar. |
| `RR ~ 142 ms` | JVMTI succeeded; a framework adapter returned `PARTIAL`. |
| `RR ✕ restart` | `RESTART_REQUIRED` — change was not applied. |
| `RR ✕ failed` | JVMTI or transport failed. |

Success balloons are off by default (status bar is enough). Failures always
balloon. First attach shows `RuntimeRocket attached (enhanced HotSwap)` (or
`standard`).

### Tool window

**View | Tool Windows | RuntimeRocket** (bottom). Sessions show pid, backend,
and agent version. The log lists the last reload result (status, classes,
adapter notes) and recent events. Toolbar: **Reload Now**, **Attach
RuntimeRocket**, **Restart**.

The footer states the debugger-HotSwap policy: `session veto on` (stock
HotSwap is cancelled only for the RR-attached process) or `set IDE HotSwap
to Never` (first Debug attach asks you to set
**Settings | Debugger | HotSwap | Reload classes after compilation = Never**).
The plugin does **not** write that IDE setting itself.

### Editor

- Green rocket gutter on the last successfully reloaded class declaration.
- Sticky editor banner while the session is in `RESTART_REQUIRED` or
  `FAILED`, with **Restart** and **Dismiss**.
- **Tools | RuntimeRocket: Reload Now** pushes the last compile output
  (no default shortcut — bind it under **Settings | Keymap**).

### Never silent no-op

If the classifier or JVMTI rejects a change, the result is
`RESTART_REQUIRED` or `FAILED`, a visible status, and a log line with the
`ChangeKind`. If the agent is not attached, compiling does not pretend to
reload.

## What `RESTART_REQUIRED` means

`RESTART_REQUIRED` is an honest “this edit cannot be applied in this JVM.”
**Nothing in the batch is redefined.** The process is still running the
previous classes.

Typical causes:

- Stock JDK + any structural change (add/remove method or field, new
  constructor, descriptor change).
- Either backend: superclass change, add/remove interface, annotation-only
  edits, record component change, sealed `permits` change, lambda/anonymous
  index shift.
- **Enum constant add/remove/reorder** — even on JBR. The class file would
  load, but `$VALUES` / `values()` stay stale because `<clinit>` is not
  re-run.
- Spring `application.properties` / `application.yml` content change
  (adapter refuses to rebind `Environment`).
- JVMTI rejected a shape the classifier thought was supported (the JVM
  message is shown).

The balloon has a **Restart** action that relaunches the run configuration.
After restart, the banner clears.

`PARTIAL` is different: JVMTI **did** redefine the classes, but an adapter
could not fully refresh framework state (stale proxy, late-attach Spring
inactive). Objects are the new bytecode; mappings or proxies may be stale
until restart.

`FAILED` means redefine threw, or a `defineClass` of a brand-new type
succeeded and the following redefine failed — the process may be dirty.
Restart.

## Enable on a run configuration

1. **Settings | Tools | RuntimeRocket** — project-level enable, auto-reload
   on successful compile, include test output (off), prefer enhanced, log
   level.
2. **Application** / **Jar Application** run configuration → **RuntimeRocket**
   tab → **Enable RuntimeRocket**. New Application configs default to on
   once the project setting is enabled.
3. IntelliJ Ultimate + Spring Boot plugin: the same checkbox on **Spring Boot**
   run configurations.
4. Use JetBrains Runtime as the JRE for add-method / add-field. On first
   limited-JDK run the plugin offers **Use bundled JetBrains Runtime**
   (one consent dialog; it never swaps the JRE silently).
5. Run. Widget: `RR ● enhanced` or `RR ● standard`.

JUnit is not patched unless **Include test output** is on.

## Attach RuntimeRocket (late attach)

**Tools | Attach RuntimeRocket** (also on the tool-window toolbar) lists
local JVMs and loads the agent via `VirtualMachine.attach`.

Requirements:

- Target is a local HotSpot / JBR process started by the **same user**.
- The IDE’s JBR already has `jdk.attach`.
- After `agentmain`, classes can still be redefined.

Limitations:

- Late-attach **+ Spring bean/mapping refresh is best-effort**. An idle
  Boot process usually has no `ContextLoader` / in-flight request, so the
  adapter reports `PARTIAL` with
  `Spring adapter inactive until a request hits the app or you restart with
  -javaagent (premain).` A later inbound request may still register the
  context.
- Some attach failures (wrong user, process gone, not HotSpot) produce a
  dialog; they are never silent.

Prefer `-javaagent` at start (the run-configuration checkbox) for Spring.

## Standalone agent and `runtimerocket.xml`

The agent works without the IDE. Build it, write a config, launch.

```powershell
.\gradlew.bat :agent:shadowJar
```

```text
agent/build/libs/runtimerocket-agent-0.1.0-SNAPSHOT-all.jar
```

Minimal `runtimerocket.xml` next to your classes or passed as `config=`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<runtimerocket xmlns="https://runtimerocket.io/ns/config" version="1">
  <id>com.example:app</id>
  <classpath>
    <dir name="F:/work/app/build/classes/java/main"/>
  </classpath>
  <resources>
    <dir name="F:/work/app/src/main/resources"/>
  </resources>
  <packages>
    <include>com.example.**</include>
  </packages>
</runtimerocket>
```

```powershell
java `
  -XX:+AllowEnhancedClassRedefinition `
  --add-opens=java.base/java.lang=ALL-UNNAMED `
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED `
  "-javaagent:agent/build/libs/runtimerocket-agent-0.1.0-SNAPSHOT-all.jar=config=C:\work\app\runtimerocket.xml,token=dev-only-token,log=info" `
  -cp your-app.jar com.example.Main
```

Notes:

- Headless launches may pass `token=` (visible in `ps`). The IntelliJ plugin
  always uses `tokenFile=` (mode `0600` / user ACL) and never puts the raw
  token on the command line.
- On JBR, add `-XX:+AllowEnhancedClassRedefinition` yourself. Without it the
  agent selects the standard backend.
- The agent watches `<classpath>` / `<resources>` dirs and reloads on file
  events (150 ms debounce). Multiple `runtimerocket.xml` files on the
  classpath are unioned — see `:fixtures:two-module`.
- Handshake file: `${java.io.tmpdir}/runtimerocket/${pid}.json` (token,
  port, backend). ACL/chmod failure aborts start.
- Forked Gradle `bootRun` / `JavaExec` is **not** injected by the plugin.
  Pass `-javaagent` on that JVM, or use an Application run configuration.

Schema details: [runtimerocket-xml.md](runtimerocket-xml.md).

## Settings

Project (**Settings | Tools | RuntimeRocket**):

- Enable RuntimeRocket
- Reload after a successful compile (default on)
- Include test output / JUnit (default off)
- Prefer enhanced HotSwap (default on)
- Agent log level

Application-level defaults (not exposed in the Settings UI yet): success
balloons off; enable on new Application run configurations on. Edit the
IDE’s `runtimerocket.xml` only if you need to change those.

## Verified IDEs

The plugin *targets* IntelliJ IDEA **2024.3–2026.2** (`since-build=243`,
`until-build=262.*`). CI `pluginVerifier` currently runs only against
**IC-2024.3**. 2026.2 verification is a follow-up: Community installers
ended at 2025.3; the unified `IntellijIdea` type needs a pinned 2026.2.x
and a large extra download.

## What 0.1.0 does not do

- JetBrains Marketplace publication
- Hibernate / Jakarta / Quarkus / Micronaut adapters
- Stock-JDK versioning backend (`rr.experimental.versioning` is not shipped)
- Gradle plugin for `bootRun` / `JavaExec`
- Re-running constructors, field initializers, or `<clinit>`
- Flushing Jackson or Spring `ReflectionUtils` caches
- Remote JVM reload (loopback only)

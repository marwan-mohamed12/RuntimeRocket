# Getting started

Install the plugin first ([README](../README.md#install-the-plugin-into-intellij)).
Then pick **one** way to start the app. The plugin checkbox only works for
IntelliJ **Application** / **Jar Application** / (Ultimate) **Spring Boot**
run configurations.

| How you start the JVM | What to do |
| --- | --- |
| **Run** / **Debug** an Application or Spring Boot config | [Path A](#path-a--intellij-run-configuration) |
| Terminal: `hybrisserver`, `java -jar`, Gradle `bootRun` | [Path B](#path-b--attach-to-a-process-already-running) |
| No IntelliJ — only the agent | [user-guide.md](user-guide.md#standalone-agent-and-runtimerocketxml) |

SAP Commerce (Hybris) details (which JVM, handshake folder, what to
compile): [hybris.md](hybris.md).

---

## Path A — IntelliJ run configuration

1. **Settings | Tools | RuntimeRocket** — leave enabled. Keep
   **Reload after a successful compile** on.
2. **Run | Edit Configurations…** → your Application / Spring Boot
   config → **RuntimeRocket** tab → enabled.
3. For add-method / add-field, set the JRE to **JetBrains Runtime**.
   Stock Temurin/Oracle/SapMachine is method-body only (`RR ● standard`).
4. **Run** or **Debug**.
5. Status bar: `RR … waiting for agent` → `RR ● enhanced` or
   `RR ● standard`.
6. Change **the body of an existing method**. Compile
   (**Build | Recompile** the file or module).
7. Status bar: `RR ✓ … ms`. Exercise the app.

`RR ○ not attached` means this process never got `-javaagent`. Wrong
run-config type, or a forked Gradle JVM.

---

## Path B — attach to a process already running

Use this when you start the JVM yourself (Hybris, `java`, `bootRun`).

1. Start the app in the **terminal** as you always do. Wait until it is up.
2. In IntelliJ: **Tools | Attach RuntimeRocket**.
3. Pick the **application** JVM, not IntelliJ and not Gradle.

   The line looks like `12345 — org.apache.catalina.startup.Bootstrap start`.
   Hybris / Tomcat is usually `Bootstrap`. Confirm with `jps -l` if unsure.
4. Status bar: `RR ● standard` (stock JDK) or `RR ● enhanced` (JBR).
5. Change a method body. **Recompile the module that owns that class**,
   not **Build Project** on a huge workspace.
6. `RR ✓` → try the change in the app.

After every process restart, attach **once** again.

### Handshake timed out

The agent can start in the app while IntelliJ never sees
`${tmpdir}/runtimerocket/<pid>.json`.

The app often uses a **different** `java.io.tmpdir` than IntelliJ
(Hybris: `hybris/temp/hybris`). IntelliJ only reads
`%TEMP%\runtimerocket\` (Windows) or `/tmp/runtimerocket` (macOS/Linux).

1. Confirm the agent started in the **app** console:
   `[RuntimeRocket] agent 0.1.0-SNAPSHOT started pid=…`
2. Find `<pid>.json` (search under `%TEMP%` and the app’s temp dir).
3. If the file is **not** under IntelliJ’s temp, link the folders
   **before** the next attach. Stop the app first. In PowerShell:

   ```powershell
   $json = Get-ChildItem -Path $env:TEMP, "D:\path\to\app" -Recurse -Filter "<pid>.json" -ErrorAction SilentlyContinue | Select-Object -First 1
   $hybrisRr = $json.DirectoryName
   if (Test-Path "$env:TEMP\runtimerocket") { Remove-Item "$env:TEMP\runtimerocket" -Recurse -Force }
   cmd /c mklink /J "$env:TEMP\runtimerocket" "$hybrisRr"
   ```

   Run that from **any** directory, in a **new** PowerShell window — not
   inside the server terminal.
4. Start the app again. **Attach once**. Do not attach a second time on
   the same process (the token will not match).

A JDK line about “Java agent has been loaded dynamically” is harmless.

---

## Daily loop (both paths)

1. Status must be `RR ● standard` or `RR ● enhanced`.
2. Edit **inside** an existing method. Do not add a method or field on a
   stock JDK.
3. Compile so IntelliJ succeeds:
   - Small project: **Build | Recompile** the file (`Ctrl+Shift+F9`).
   - Multi-module (Hybris, large Gradle): **recompile the module**, not
     one file and not the whole project.
4. **Build** tool window must be green. `cannot find symbol` means
   compile failed — RuntimeRocket has nothing to send.
5. Read the status bar (`RR ✓` / `RR ✕ restart` / `RR ○ not attached`).

`RR ✕ restart` = that edit cannot apply. Restart the process, then
attach again if you used Path B.

---

## What 0.1.0 will not do

- Superclass / interface changes, enum constant add/remove, record
  components.
- Re-run constructors or `<clinit>`. New fields stay at defaults.
- Spring XML, `application.properties` / `yml` content, Hybris
  `items.xml` / ImpEx / type system.
- Gradle `bootRun` / `JavaExec` / `hybrisserver` injection (attach or
  pass `-javaagent` yourself).

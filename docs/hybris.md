# SAP Commerce (Hybris)

RuntimeRocket **0.1.0 is not a Hybris plugin.** `hybrisserver` is never
patched by the run-configuration checkbox. On a certified Commerce JDK
(Temurin / SapMachine) you get **method-body only** (`RR ● standard`).

Keep that JDK. Do **not** switch `hybrisserver` to JetBrains Runtime.

Install the plugin first:
[README](../README.md#install-the-plugin-into-intellij).
Generic attach / handshake notes:
[getting-started.md](getting-started.md#path-b--attach-to-a-process-already-running).

## What reloads

| You change | Result |
| --- | --- |
| Body of an existing method in custom Java | Reload if IntelliJ compiled it |
| New method, field, constructor, signature | `RR ✕ restart` |
| `items.xml`, `*Model`, type system | HAC Update / `ant updatesystem` + restart |
| `*spring.xml`, ImpEx, `local.properties`, JSP, widgets | Restart |

There is no Commerce adapter. Spring mapping refresh targets Boot 3.5 /
Framework 6.2+, not typical Commerce Spring 5.3.

## One-time setup

### 1. Plugin settings

**Settings | Tools | RuntimeRocket** — enabled. Reload on successful
compile on. Include test output off.

Do **not** enable RuntimeRocket on an Application run configuration and
expect `hybrisserver` to pick it up.

### 2. Leave module compile output as Hybris set it

Example (backoffice):

```text
…\bin\custom\cchbackoffice\backoffice\eclipsebin
```

That is the normal **IDE** output. `ant` uses `classes`; Tomcat loads the
backoffice **JAR**. You do **not** change this for RuntimeRocket.

The plugin sends `.class` **bytes** after a successful IntelliJ compile.
Tomcat does not need to read `eclipsebin` from disk.

Only change **Paths** if IntelliJ writes to `out\production\...` and
`eclipsebin` / `classes` never updates.

### 3. Handshake folder (Windows)

Hybris writes `${hybris.temp}/runtimerocket/<pid>.json`.
IntelliJ reads `%TEMP%\runtimerocket\<pid>.json`.

After the **first** successful agent start you will see a file under
`core-customize\hybris\temp\hybris\runtimerocket`. Link that folder
**once** (stop the server first). Run in a **new** PowerShell window,
from any directory:

```powershell
$json = Get-ChildItem -Path $env:TEMP, "D:\dccp-digitalcommerce-customerportal\core-customize\hybris" -Recurse -Filter "*.json" -ErrorAction SilentlyContinue |
    Where-Object { $_.DirectoryName -match "runtimerocket" } |
    Select-Object -First 1
$rrDir = $json.DirectoryName
Write-Host "Handshake folder: $rrDir"
if (Test-Path "$env:TEMP\runtimerocket") { Remove-Item "$env:TEMP\runtimerocket" -Recurse -Force }
cmd /c mklink /J "$env:TEMP\runtimerocket" "$rrDir"
```

Replace the Hybris root if yours is different. You should see
`Junction created for …\Temp\runtimerocket <<===>> …\hybris\temp\hybris\runtimerocket`.

Do this once per machine. Do not run it inside the `hybrisserver` window.

## Every server start

1. Terminal: `hybrisserver.bat debug` (or `hybrisserver.sh debug`).
2. Wait until HAC / Backoffice / OCC responds.
3. IntelliJ: **Tools | Attach RuntimeRocket**.
4. Pick the line labeled **Hybris / Tomcat** (`Bootstrap`). IntelliJ and
   Gradle JVMs are hidden. If you already attached once, Attach
   reconnects — do not expect a second agent load.
5. Attach **once**. Status: **`RR ● standard`**.

Do not pick IntelliJ, `GradleDaemon`, or `jps.cmdline`.

If the console shows `agent … started` but IntelliJ says
**handshake timed out**, the junction is missing or you attached twice
on the same process. Stop the server, fix the junction, start, attach
once.

`A Java agent has been loaded dynamically` is a JDK 21 warning. Ignore it.
`agent already started; ignoring duplicate start` means you attached
twice — stop attaching; restart the server if the IDE is not connected.

## Daily loop

1. Leave `hybrisserver` running. Status must stay `RR ● standard`.
2. Open a custom class that is **already loaded** (open that Backoffice
   screen or call the facade once).
3. Change **one line inside an existing method** (for example
   `System.out.println("RR test");`).
4. Compile the **module** that owns the file
   (`cchfacades`, `cchcore`, `cchbackoffice`, …):
   right-click the **module** → **Build | Recompile ‘…’**.
   **Reload Now** also tries a hot reload first, then that same module
   build only if needed. Output under `eclipsebin` / `classes` is scanned
   while attached.
5. **View | Tool Windows | Build** must be green. `package … does not
   exist` is an IntelliJ classpath problem (missing `cloudcommons`), not
   a detach. The session stays attached.
6. Status **`RR ✓`**. Use Backoffice / the storefront. Check the Hybris
   console for your log line.

Do **not** use **Build | Build Project**. That walks every Hybris module
and prints `no compiler output found for modules Custom…, Hybris.Unused…`.
That list is noise. Ignore it. Recompile one module.

`Ctrl+Shift+F9` on a **single** file often fails with `cannot find symbol`
(`CchCartFacade`, other packages). That is IntelliJ compiling one file
without the rest of the extension. RuntimeRocket then says **nothing to
reload**. Recompile the **module**.

## After you restart Hybris

Every new `hybrisserver` is a new JVM.

1. Wait until it is up.
2. **Tools | Attach RuntimeRocket** once.
3. Wait for `RR ● standard`.
4. Then compile.

The junction stays. You do not recreate it.

## Optional: `ant build` without IntelliJ compile

If you compile only with `ant`, put `runtimerocket.xml` on the extension
classpath and add `-javaagent:…runtimerocket-agent-…-all.jar=token=…`
to `tomcat.debugjavaoptions` in `local.properties`. Schema:
[runtimerocket-xml.md](runtimerocket-xml.md). For IntelliJ **Recompile**,
attach (above) is enough; you do not need the XML.

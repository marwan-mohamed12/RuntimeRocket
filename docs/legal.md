# Legal

RuntimeRocket is original work. It is not a fork of JRebel, HotswapAgent, or Spring Loaded.

## License matrix

| Component | License | How we use it | Notes |
| --- | --- | --- | --- |
| RuntimeRocket (this repository) | Apache-2.0 | All first-party code | `LICENSE` at the repo root. Every module inherits it. |
| IntelliJ Platform SDK | Apache-2.0 | Compile/runtime of `:plugin` | Marketplace ToS apply only if/when we publish. v1 does not use paid-plugin APIs. |
| ASM 9.x | BSD-3-Clause | Future compile/runtime of `:agent` | Compatible. Listed in `NOTICE`. Required ≥ 9.8 for class-file 69 (Java 25). |
| Kotlin | Apache-2.0 | `:plugin` | Compatible. |
| Gradle, Shadow (`com.gradleup.shadow`) | Apache-2.0 | Build | Compatible. |
| JUnit 5 | EPL-2.0 | Tests | Compatible. |
| Spring Framework / Spring Boot | Apache-2.0 | `compileOnly` in `:frameworks:spring` | Not bundled. Runtime reflection against the user's Spring. |
| Byte Buddy | Apache-2.0 | Optional/later; `byte-buddy-agent` **test-only** | Compatible. Not on the agent bootstrap path in v1. |
| HotswapAgent | **GPL-2.0** | **Do not copy, shade, or link** | Public website behavior and JBR VM flags may be implemented independently. |
| JRebel | Proprietary | Public FAQ / manuals / talks only | **No decompilation, no binary study, no `rebel.xml` XSD from their JAR.** Our schema is `runtimerocket.xml`. |
| Spring Loaded | Apache-2.0 | Prior art only | May be *read*; do not paste substantial code. |
| JBR / DCEVM | GPL-2.0 with Classpath Exception | Detect and pass a documented VM flag | We do not distribute a JDK. |

## Contributions

Contributions are accepted under the Apache License 2.0.

This project uses the [Developer Certificate of Origin (DCO) 1.1](https://developercertificate.org/). There is **no CLA**.

Every commit must include a sign-off:

```
Signed-off-by: Your Name <you@example.com>
```

Git can add this for you:

```powershell
git commit -s -m "your message"
```

By signing off, you certify:

Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.

Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.

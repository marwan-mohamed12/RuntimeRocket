# `runtimerocket.xml`

Standalone agent configuration. Write one file per module (or pass `config=`) so the agent
can watch output trees without the IDE. The IDE compile path sends class bytes over the
loopback protocol and does not require this file. `:fixtures:two-module` emits one file per
module from Gradle. The schema is independent of JRebel’s `rebel.xml`; this is not a copy of
that XSD.

## Schema (version 1)

Namespace: `https://runtimerocket.io/ns/config`

```
# RelaxNG compact — documentation only; the agent uses a DOM parser.
default namespace = "https://runtimerocket.io/ns/config"

element runtimerocket {
  attribute version { "1" },
  element id { text }?,
  element classpath { element dir { attribute name { text } }* }?,
  element resources { element dir { attribute name { text } }* }?,
  element packages {
    element include { text }*,
    element exclude { text }*
  }?
}
```

Example:

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
    <exclude>com.example.generated.**</exclude>
  </packages>
</runtimerocket>
```

## Paths

`dir/@name` may be absolute or relative. The agent resolves relative names against `user.dir`.

## Package globs

- Empty `<packages>` (or no includes) means every class from the listed directories.
- `com.example.**` matches `com.example` and every nested name.
- `com.example.*` matches one extra segment (`com.example.Foo`, not `com.example.sub.Bar`).
- A matching `<exclude>` always wins for that document.

## Discovery and multi-module union

The agent loads:

1. The file named by the `config=` agent option, if set.
2. Every `runtimerocket.xml` visible on the application classpath (`ClassLoader.getResources`).
3. `runtimerocket.xml` sitting in an extra `watchDir=` root.

Documents are unioned: classpath dirs, resource dirs, and package filters are merged. A class is
accepted when **any** document accepts it. `:fixtures:two-module` (lib + app) emits one file per
module so a running app sees both output trees and a change in lib reloads without an IDE client.

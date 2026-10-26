# TESTAR Evidence for Java

A standalone library that validates, saves, and reads portable GUI test evidence following [TESTAR Evidence specs](https://github.com/ferpasri/testar-evidence-specs).

Adapters supply captured records and oracle results. The library validates references and lifecycle rules before writing, and preserves the reported evidence for inspection or separate abstraction.

## Build and test

Use Java 17 or later and Maven 3.6.3 or later. From this repository:

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.evidence.tools.PrepareConformance" "-Dexec.classpathScope=test"
mvn verify
```

The JAR is written under `target/`, with coordinates and version from [pom.xml](pom.xml).

## Import the library

Install the tested library into the local Maven repository:

```powershell
mvn install
```

For Maven consumers, replace `LIBRARY_VERSION` with the selected version from `pom.xml`:

```xml
<dependency>
    <groupId>org.testar</groupId>
    <artifactId>testar-evidence</artifactId>
    <version>LIBRARY_VERSION</version>
</dependency>
```

Gradle consumers can enable `mavenLocal()` and declare `org.testar:testar-evidence:LIBRARY_VERSION`. Public classes are in `org.testar.evidence`.

## Recording and reading

`JsonlStore.create(directory, manifest)` starts a fresh evidence collection. Report `EvidenceEvent` values through `record`. `EvidenceReader.read(directory)` reads saved evidence; `JsonlStore.open(directory)` reopens it for continued recording. `ReconstructedEvidence` holds validated records and relationships for inspection.

See the [commented recording example](docs/examples.md) for a complete sequence with observations, available actions, an execution, and an oracle result. The [implementation specification](docs/SPEC.md) describes value handling, record access, and persistence.

## Shared conformance data

Preparation selects the newest published [specs release](https://github.com/ferpasri/testar-evidence-specs/releases), including prereleases, and downloads its cases into ignored `target/conformance/`. It verifies archive boundaries and records the selected release and checksums. Failed preparation preserves the previous selection. Tests use the verified cache offline; repeat preparation when checking a newer release.

Reader tests process each supplied trace. Producer tests record the same events through the public API and reopen the output. Both compare expected records, accepted payloads, rejection behavior, and artifacts. The specs own the [conformance contract](https://github.com/ferpasri/testar-evidence-specs/blob/main/conformance/README.md).

## Compatibility

[pom.xml](pom.xml) records the package version and build requirements. `SCHEMA_VERSION` in [src/main/java/org/testar/evidence/Manifest.java](src/main/java/org/testar/evidence/Manifest.java) identifies the supported evidence format. Related projects share their first two release numbers and advance patches independently; format compatibility is checked separately.

See [TODO](TODO.md) for remaining work. Shared meanings, including capture and metadata conventions, remain in the specs repository.

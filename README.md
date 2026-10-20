# TESTAR state model for Java

A standalone Java library for recording test execution evidence in the shared [TESTAR state model format](https://github.com/ferpasri/testar-state-model-specs).

Tool adapters supply runs, observed states, widgets, properties, discovered actions, executions, screenshots, and test oracle results. The library validates those records, saves portable model directories, and exposes queries over the execution history.

Every visit remains a separate observation. Completed executions connect their source and result observations in the recorded direction. Discovered candidates remain available even when never selected. Consumers can use the saved evidence to display traces or build derived models.

Captured properties and oracle evaluations are separate evidence. Check results identify what was evaluated, the rule, its outcome, and any reported reading.

## Release versions

Releases use `0.X.Y`. The `0.X` release series is shared with the specs and related state-model projects. Each project increments `Y` independently for fixes and improvements within that series. For example, library `0.2.1` and adapter `0.2.3` belong to the same `0.2` series as specs `0.2.0`.

## Build and test

Use Java 17 or later and Maven 3.6.3 or later. Run these commands from this repository.

Prepare the shared conformance cases:

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.statemodel.evidence.tools.PrepareConformance" "-Dexec.classpathScope=test"
```

Run the tests and build the JAR:

```powershell
mvn verify
```

To compile and package without running tests:

```powershell
mvn -DskipTests package
```

The library is built as `target/testar-state-model-0.2.0.jar`.

## Import the library

Install the tested library in your local Maven repository:

```powershell
mvn install
```

A consuming Maven project can then declare:

```xml
<dependency>
    <groupId>org.testar</groupId>
    <artifactId>testar-state-model</artifactId>
    <version>0.2.0</version>
</dependency>
```

A Gradle project can use:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.testar:testar-state-model:0.2.0'
}
```

Public API classes are in `org.testar.statemodel.evidence`.

## Example

[LocalModel.java](src/test/java/org/testar/statemodel/evidence/examples/LocalModel.java) records two demonstration observations, two candidate buttons, one completed action, and one property evaluation. Comments explain the recording steps.

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.statemodel.evidence.examples.LocalModel" "-Dexec.classpathScope=test"
```

Open the generated `target/example-model/` directory in the state model dashboard. Running the example again reuses the same event IDs, preserving the existing records without duplication.

## Shared conformance data

The preparation command queries the public [specs releases](https://github.com/ferpasri/testar-state-model-specs/releases). It selects the newest publication by `published_at`, including prereleases and excluding draft or unpublished entries. Ties use the release ID. Publication order determines the selection.

Preparation downloads the release archive over HTTPS and extracts its conformance files into the ignored `target/conformance/` directory. `current.json` records the selected release and locally computed SHA-256 checksums. Tests verify those checksums to detect changes to cached files.

Archive paths, extraction sizes, and case completeness are checked. Preparation reports a failure and leaves the previous prepared selection intact. A failed latest-release lookup is reported to the caller.

After successful preparation, tests use the cached cases offline. Running tests alone uses that prepared selection; repeat preparation to check compatibility with the latest release, including before tests in CI. Deleting `target/` removes both the cache and build output.

Tests discover every released case directory. Reader tests load supplied evidence. Producer tests record equivalent evidence through the public API and reopen it. Both compare the records and rejected-case expectations, preserve artifact bytes, and verify that rejection leaves the accepted evidence unchanged.

## Documents

- [Shared model semantics](https://github.com/ferpasri/testar-state-model-specs/blob/main/docs/model-semantics.md): the meaning of generic evidence.
- [Implementation specification](docs/SPEC.md): this library's API and implemented behavior.
- [TODO](TODO.md): planned work.

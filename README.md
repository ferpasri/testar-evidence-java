# TESTAR evidence for Java

A standalone Java library for validating and saving portable test execution evidence in the shared [TESTAR evidence format](https://github.com/ferpasri/testar-evidence-specs).

Tool adapters supply runs, observed states, widgets, properties, discovered actions, executions, screenshots, and test oracle results. The library checks that the records and their references are valid, then saves them as portable evidence files.

Every visit remains a separate observation. Each completed action connects the observation before the action to its resulting observation, forming a state-action-state execution trace. Discovered candidates remain available even when never selected. Consumers can use the saved evidence to display traces or build derived models.

Captured properties and oracle evaluations are separate evidence. Check results identify what was evaluated, the rule, its outcome, and any reported reading.

The library preserves supplied property names and values, capture descriptions, known omissions, and declared aliases. Adapters use common names when the source provides the documented property. See the shared [capture guidance](https://github.com/ferpasri/testar-evidence-specs/blob/main/docs/capture-evidence.md).

## Release versions

Releases use `0.X.Y`. The `0.X` release series is shared with the specs and related state-model projects. Each project increments `Y` independently for fixes and improvements within that series. Different patch versions can coexist within the same release series.

## Build and test

Use Java 17 or later and Maven 3.6.3 or later. Run these commands from this repository.

Prepare the shared conformance cases:

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.evidence.tools.PrepareConformance" "-Dexec.classpathScope=test"
```

Run the tests and build the JAR:

```powershell
mvn verify
```

To compile and package without running tests:

```powershell
mvn -DskipTests package
```

The library is built as `target/testar-evidence-0.2.2.jar`.

## Import the library

Install the tested library in your local Maven repository:

```powershell
mvn install
```

A consuming Maven project can then declare:

```xml
<dependency>
    <groupId>org.testar</groupId>
    <artifactId>testar-evidence</artifactId>
    <version>0.2.2</version>
</dependency>
```

A Gradle project can use:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.testar:testar-evidence:0.2.2'
}
```

Public API classes are in `org.testar.evidence`.

## Example

[LocalModel.java](src/test/java/org/testar/evidence/examples/LocalModel.java) records two demonstration observations, two candidate buttons, one completed action, and one property evaluation. Comments explain the recording steps.

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.evidence.examples.LocalModel" "-Dexec.classpathScope=test"
```

Each run creates a new directory under `target/traces/`, such as `demo_run_trace-2026-10-22_15-30-12-123/`. A name collision adds a numeric suffix. Model and run identities are distinct on each execution, so earlier evidence is preserved. Load the complete generated directory in the dashboard.

To supply the parent directory, SUT label, and test objective label, add `"-Dexec.args=target/traces demo submit-form"`. Labels are stored in run properties as supplied; their filename versions are sanitized and limited to 32 characters each. A fourth argument supplies an optional origin. Without a test objective, the filename uses `run` and the objective property is omitted.

## Run metadata

Run properties support optional origin, test objective, adapter name, and adapter version using the [shared run metadata names](https://github.com/ferpasri/testar-evidence-specs/blob/main/docs/run-metadata.md). Supplied descriptions keep their original text; omitted descriptions stay omitted. See [the implementation specification](docs/SPEC.md#run-metadata) for their use.

## Reading and inspecting saved traces

The library remembers earlier records while writing so it can check new ones. For example, a selected action must refer to an existing observation and one of its candidates. `EvidenceReader.read(directory)` reads a saved trace without changing it. `JsonlStore.open(directory)` reads existing records before continuing a recording. `observationsForRun`, `executionsForRun`, and `outgoing` provide simple lookups over those records. Reading and inspection support recording; consumers decide how to group visits into derived models.

## Shared conformance data

Each case supplies an input trace: `manifest.json`, `evidence.jsonl` (one recorded event per line), and referenced artifacts. The library reads the events to reconstruct a model in memory. Tests compare a summary of that model with `expected.json`, which describes the expected records or rejection. Tests also verify complete accepted event contents and artifact bytes. The reconstructed model preserves concrete evidence; it is separate from any later abstract or merged model.

See the shared [input trace, reconstructed model, and expected result explanation](https://github.com/ferpasri/testar-evidence-specs/blob/main/conformance/README.md#input-trace-reconstructed-model-and-expected-result) for the file roles, an event-sequence example, and reader/producer test behavior.

The preparation command queries the public [specs releases](https://github.com/ferpasri/testar-evidence-specs/releases). It selects the newest publication by `published_at`, including prereleases and excluding draft or unpublished entries. Ties use the release ID. Publication order determines the selection.

Preparation downloads the release archive over HTTPS and extracts its conformance files into the ignored `target/conformance/` directory. `current.json` records the selected release and locally computed SHA-256 checksums. Tests verify those checksums to detect changes to cached files.

Archive paths, extraction sizes, and case completeness are checked. Preparation reports a failure and leaves the previous prepared selection intact. A failed latest-release lookup is reported to the caller.

After successful preparation, tests use the cached cases offline. Running tests alone uses that prepared selection; repeat preparation to check compatibility with the latest release, including before tests in CI. Deleting `target/` removes both the cache and build output.

Tests discover every released case directory. Reader tests load supplied evidence. Producer tests record equivalent evidence through the public API and reopen it. Both compare the records and rejected-case expectations, preserve artifact bytes, and verify that rejection leaves the accepted evidence unchanged. Cases include capture limits and aliases, unselected candidates, scripted action parameters, rejected submissions, and temporal results reported during and after a run.

## Documents

- [Shared model semantics](https://github.com/ferpasri/testar-evidence-specs/blob/main/docs/model-semantics.md): the meaning of generic evidence.
- [Implementation specification](docs/SPEC.md): this library's API and implemented behavior.
- [TODO](TODO.md): planned work.

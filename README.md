# TESTAR state model for Java

A Java library for recording test execution evidence in the shared [TESTAR state model format](https://github.com/ferpasri/testar-state-model-specs).

Tool adapters supply runs, observed states, widgets, discovered actions, executions, screenshots, and test oracle results. The library validates those records and saves portable model directories that other tools can read.

## Build and test

Use Java 17 or later and Maven 3.6.3 or later. Run these commands from this repository.

Prepare the shared conformance cases from the newest published specs release, including prereleases:

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.statemodel.tools.PrepareConformance" "-Dexec.classpathScope=test"
```

Run the tests and build the JAR:

```powershell
mvn verify
```

Prepared cases are cached in `target/conformance/`, with release information and file checksums. Tests use that cache and can run offline after Maven dependencies have been downloaded. Run the preparation command again to check against the latest release. Deleting `target/` removes the cache and build output.

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

Public API classes are in `org.testar.statemodel`. Release series `0.2` follows the shared specs series; patch versions can advance independently.

## Example

[LocalModel.java](src/test/java/org/testar/statemodel/examples/LocalModel.java) records two demonstration observations, two candidate buttons, one completed action, and one property evaluation. Comments explain the recording steps.

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.statemodel.examples.LocalModel" "-Dexec.classpathScope=test"
```

Open the generated `target/example-model/` directory in the state model dashboard. Running the example again reuses the same event IDs, so it preserves the existing records without duplicating them.

See the [implementation specification](docs/SPEC.md) for record semantics and storage behavior, and [TODO](TODO.md) for planned work.

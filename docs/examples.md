# Recording example

Run these commands from the repository root.

The example writes demonstration evidence through the public API. It illustrates recording boundaries and metadata; the [implementation specification](SPEC.md) defines behavior.

[RecordEvidence.java](../src/test/java/org/testar/evidence/examples/RecordEvidence.java) records two demonstration observations, two available button actions, one completed action, and one property evaluation. Comments explain the recording steps.

```powershell
mvn test-compile exec:java "-Dexec.mainClass=org.testar.evidence.examples.RecordEvidence" "-Dexec.classpathScope=test"
```

Each run creates a new directory under `target/traces/`, such as `demo_run_trace-2026-10-22_15-30-12-123/`. A name collision adds a numeric suffix. Evidence, run, and sequence identities are distinct on each execution, so earlier evidence is preserved. Load the complete generated directory in the dashboard.

To supply the parent directory, SUT label, and test objective label, add `"-Dexec.args=target/traces demo submit-form"`. Labels are stored in run properties as supplied; their filename versions are sanitized and limited to 32 characters each. A fourth argument supplies an optional origin. Without a test objective, the filename uses `run` and the objective property is omitted.

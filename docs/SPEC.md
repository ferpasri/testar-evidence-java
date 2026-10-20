# Java state model specification

The library records what a testing tool observed, which actions it discovered, what it executed, and the outcomes reported by its test oracles. An adapter translates tool data into these generic records.

## Records and properties

| Type | What it represents |
| --- | --- |
| `Run` | One testing session and its producer information. |
| `Observation` | One visit, with its sequence position, timestamp, state, candidates, and artifact references. |
| `ConcreteState` | Captured state properties and an ordered widget hierarchy. |
| `Widget` | A captured interface element, its local ID, optional parent, and properties. |
| `ActionDefinition` | An action's stable ID, kind, and descriptive properties. |
| `ActionCandidate` | An action discovered at one observation, optionally bound to a widget. |
| `Execution` | One selection, including its source, candidate binding, and actual parameters. |
| `CheckResult` | The outcome and supporting information from one test oracle evaluation. |
| `ArtifactReference` | A local evidence file, its media type, SHA-256 hash, and optional dimensions. |
| `Properties` | Named JSON values, including nested data and explicit null values. |

Every visit has its own observation ID. Identical snapshot content can share a snapshot ID while the visits and execution history remain separate. Snapshot IDs retain fixed content.

Widget IDs are local to their snapshot. Parents refer to widgets in that snapshot, and the hierarchy is acyclic. Several root widgets are allowed. Capture scope records complete, filtered, or unavailable widget information. Candidate scope records generated, eligible, or unknown action information.

Candidates remain stored when they are never selected. A recorded candidate set describes what the producer reported at that observation. Comparisons between visits can use those candidates as evidence for exploration analysis.

IDs and property names are nonblank strings without control characters. Properties preserve missing values separately from explicit nulls. JSON integer values retain their precision. Observation sequence numbers and timestamps support the full unsigned 64-bit range through `BigInteger`; convenience constructors accept nonnegative `long` values.

Records are immutable. Lists are copied; properties and supplied JSON values are copied on entry and access.

## Events and execution order

`EventEnvelope` identifies a versioned event with a fixed event ID and payload. `EvidenceJson` reads and writes the shared JSON structure, checking record types and structural fields.

`StateModel.validate` checks an event without applying it. `apply` validates before updating the model. An identical event retry returns `false`. Different content using an existing event ID raises a conflict.

A run starts before its observations. Action definitions precede their candidates. Observation sequence numbers increase within each run.

Selecting an action requires a matching action/target pair in the latest observation. A run can have one execution awaiting submission or a result. A rejected submission retains its error and permits another selection. An accepted submission awaits a later observation in the same run; linking that observation completes the execution.

Finishing a run records the ending reason and marks outstanding selections or submissions incomplete. A completed execution produces an `ExecutionLink` from its source observation to its result. Submission acceptance alone does not establish that the application behaved correctly.

Queries expose runs, visits, snapshots, actions, candidates, executions, links, and check results. Visits within a run are ordered by sequence; executions, links, and evaluations follow recording order. General run, observation, action, and snapshot lists are sorted by ID.

`StateModel` is intended for use by one caller at a time. `snapshot()` returns an independent model with the same records.

## Test oracle results

A test oracle applies a rule to a target. The adapter records one evaluation as a `CheckResult`, with its name, rule, target, status, and supporting details.

Targets can identify a run, observation, state property, widget, widget property, execution, or inclusive observation sequence. Referenced records belong to the result's run. A widget must belong to its referenced observation. A property may be absent: its presence can itself be the condition being evaluated.

Passed and failed report whether the condition was satisfied. Pending means the evaluation had no definite result when recorded; unknown means the oracle could not determine an outcome. Error records an evaluation problem. Finishing a run retains these reported statuses. Results may arrive after the run finishes.

`ExpectedValue.unspecified()` omits an expectation. `ExpectedValue.of(null)` records an explicit JSON null. An actual reading can contain a value, established absence, or unknown information; omitting the reading means none was supplied. Captured properties do not imply evaluations.

An invalid target raises `ModelException` with code `invalid_check_target` and the affected field, such as `target.widget`. Rejection preserves the model and stored log.

## Storage

`JsonlStore` manages a directory containing `manifest.json`, `evidence.jsonl`, and referenced `artifacts/`. `create` creates a collection; `open` validates and replays it for continued recording. `EvidenceReader.read` replays a collection without opening a writer.

An exclusive file lock allows one writer per directory. Store operations are synchronized. `record` validates the event and its artifacts, appends one UTF-8 JSON line, then flushes it to storage before updating the in-memory model. An I/O failure during append requires closing and reopening the store. `model()` returns an independent snapshot so callers cannot change the stored projection directly.

`putArtifact` identifies supplied bytes by SHA-256 and reuses identical content. PNG paths end in `.png`, JPEG paths in `.jpeg`, and other media paths in `.bin`. The caller supplies the media type and optional dimensions. Referenced files are checked by hash when recording or reopening; relative paths keep evidence portable when the whole directory is moved. Evidence paths are checked for traversal and symbolic links.

Each JSONL record ends with a newline. Records and manifests are limited to 16 MiB; JSON nesting is limited to 64 levels. Invalid UTF-8, duplicate JSON keys, unknown structural fields, and trailing JSON values are rejected.

`recoverTail` acquires the writer lock, validates the complete preceding events and artifacts, and removes an unfinished final fragment. Complete invalid records cause recovery to fail without changing the log.

## Verification

The Java preparation tool downloads the newest published specs release, including prereleases, over HTTPS. It bounds archive sizes, checks extraction paths, and records the selected release and SHA-256 checksums under `target/conformance/`. A download failure is reported to the caller.

Conformance tests discover the downloaded cases and verify their cached checksums. Reader tests compare accepted records or rejected targets with the released expectations. Producer tests write the cases through the public API, verify field and artifact preservation, check identical retries and rejection behavior, and reopen the accepted evidence.

Unit tests exercise discovered candidates, repeated visits, lifecycle ordering, evaluation targets, numeric precision, immutable data, storage integrity, writer locking, and recovery.

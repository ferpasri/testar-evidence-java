# Java state model specification

The library records what a testing tool observed, which actions it discovered, what it executed, and the outcomes reported by its test oracles. An adapter translates tool data into these generic records.

The [shared model semantics](https://github.com/ferpasri/testar-state-model-specs/blob/main/docs/model-semantics.md) define the evidence meaning. This document describes the Java API in `org.testar.statemodel.evidence` and its implemented behavior.

## Records and properties

| Type | What it represents |
| --- | --- |
| `Run` | One testing session and its producer information. |
| `Observation` | One visit, with its sequence position, timestamp, state, candidates, and artifact references. |
| `ConcreteState` | Captured state properties and an ordered widget hierarchy. |
| `Widget` | A captured interface element, its local ID, optional parent, and properties. |
| `Properties` | Named JSON values, including nested data and explicit null values. |
| `ActionDefinition` | An action's stable ID, kind, and descriptive properties. |
| `ActionCandidate` | An action discovered at one observation, optionally bound to a widget. |
| `Execution` | One selection, including its source, candidate binding, and actual parameters. |
| `CheckResult` | The outcome and supporting information from one test oracle evaluation. |
| `CheckRule` | The condition's ID, optional version, and optional parameters. |
| `CheckTarget` | The run, visit, widget, property, execution, or sequence being evaluated. |
| `CheckReading` | A reported JSON value, established absence, or unknown information. |
| `ExpectedValue` | An omitted expectation or a supplied JSON value, including null. |
| `ArtifactReference` | A local evidence file, its media type, SHA-256 hash, and optional dimensions. |

Every visit has its own observation ID. Identical snapshot content can share a snapshot ID while the visits and execution history remain separate. Snapshot IDs retain fixed content.

Widget IDs are local to their snapshot. Parents refer to widgets in that snapshot, and the hierarchy is acyclic. Several root widgets are allowed. Capture scope records complete, filtered, or unavailable widget information. Properties preserve missing values separately from explicit nulls. Source-prefixed properties can retain tool-specific details.

IDs and property names are nonblank strings without control characters. The producer supplies IDs that distinguish its records across runs and sources.

## Discovered actions and exploration

An observation retains its candidates even when they are never selected. Candidate scope records whether the producer reports generated actions, actions marked eligible under its selection rules, or an unknown scope.

For example, a tool can report ten candidate actions and execute one. The observation keeps all ten candidates; the execution identifies the selected action and its widget binding.

Consumers can identify candidates with no recorded selection from that observation. Selection, accepted submission, and a resulting observation are separate stages. Comparing exploration across different visits requires a policy for matching observations and actions. Candidate lists describe what the producer reported, so their counts reflect that scope.

## Events and execution order

Each `EventEnvelope` identifies one versioned event with a fixed event ID and payload.

| Event | Recorded behavior |
| --- | --- |
| `run_started` | Registers the run and its origin. |
| `action_defined` | Registers an action definition whose ID retains the same content. |
| `observed` | Records a visit, with sequence numbers increasing within its run. |
| `action_selected` | Selects a matching action/target pair from the latest observation. |
| `action_submitted` | Records acceptance or rejection, with any rejection error. |
| `action_result` | Links an accepted execution to a later observation in the same run. |
| `checked` | Records one test oracle evaluation's outcome and supporting evidence. |
| `run_finished` | Records the ending reason and marks outstanding executions incomplete. |

A run starts before its observations, and action definitions precede their candidates. A run can have one execution awaiting submission or a result. Rejected submission permits another selection.

`StateModel.validate` checks an event without applying it. `StateModel.apply` validates before updating the model. An identical event retry returns `false`. Different content using an existing event ID raises a conflict.

`ExecutionRecord` exposes the selected action and its status: selected, submitted, rejected, observed, or incomplete. A completed execution produces an `ExecutionLink` from its source observation to its result. Submission acceptance records what happened at that stage; test oracle results describe evaluated conditions separately.

## Test oracle results

A test oracle applies a rule to a target. The adapter records one evaluation as a `CheckResult`, with its name, rule, target, status, and supporting details. Captured properties and recorded evaluations remain separate evidence.

Targets can identify a run, observation, state property, widget, widget property, execution, or inclusive observation sequence. Referenced records belong to the result's run. A widget belongs to its referenced observation. A property's presence can itself be evaluated, so the target may name an absent property.

Passed and failed report whether the condition was satisfied. Pending means the evaluation had no definite result when recorded; unknown means the oracle could not determine an outcome. Error records an evaluation problem. Finishing a run retains these reported statuses. Results may arrive after the run finishes.

`ExpectedValue.unspecified()` omits an expectation. `ExpectedValue.of(null)` records an explicit JSON null. An actual reading can contain a value, established absence, or unknown information; omitting the reading means none was supplied. Optional rule versions and parameter maps retain whether they were supplied.

An invalid target raises `ModelException` with code `invalid_check_target`, identifying the affected field, such as `target.widget`. Rejection preserves the model and stored log.

## Queries and data access

Queries expose runs, visits, snapshots, actions, candidates, executions, links, and check results. Visits within a run are ordered by sequence; executions and links follow selection order; evaluations follow recording order. General run, observation, action, and snapshot lists are sorted by ID.

`incoming` and `outgoing` find completed links for a visit. `observationsForSnapshot` finds the visits referencing a snapshot. `observationsForRun` returns a run's ordered visits.

Records are immutable. Lists are copied; properties and supplied JSON values are copied on entry and access. `StateModel` is intended for one caller at a time; `snapshot()` returns an independent model. Sequence numbers and timestamps use `BigInteger` for the full unsigned 64-bit range, with convenience constructors accepting nonnegative `long` values. JSON integer properties retain their precision.

## Storage

`JsonlStore` manages a directory containing `manifest.json`, `evidence.jsonl`, and referenced `artifacts/`. `create` creates a collection; `open` validates and replays it for continued recording. `EvidenceReader.read` replays a collection without opening a writer.

An exclusive file lock allows one writer per directory. `record` validates the event and its artifacts, appends one UTF-8 JSON line, then synchronizes it to storage before acknowledging the write. An I/O failure during append requires closing and reopening the store. Store operations are synchronized; `model()` returns an independent snapshot so callers cannot change the stored projection directly.

`putArtifact` identifies supplied bytes by SHA-256 and reuses identical content. PNG paths end in `.png`, JPEG paths in `.jpeg`, and other media paths in `.bin`. The caller supplies the media type and optional dimensions. Referenced files are checked by hash when recording or reopening. Relative artifact paths keep evidence portable when the whole directory is moved.

Each JSONL record ends with a newline. `recoverTail` acquires the writer lock, validates the complete preceding events and artifacts, and removes an unfinished final fragment. Complete invalid records cause recovery to fail without changing the log.

The Java reader rejects invalid UTF-8, duplicate JSON keys, unknown structural fields, and trailing JSON values. Records and manifests are limited to 16 MiB; JSON nesting is limited to 64 levels. Evidence paths are checked for traversal and symbolic links.

## Verification

The [README](../README.md#shared-conformance-data) describes preparation of the newest published specs cases and the local cache. Tests discover released cases and verify their cached checksums.

Reader tests compare accepted records or rejected targets with the released expectations. Producer tests write equivalent evidence through the public API, verify field and artifact preservation, check identical retries and rejection behavior, and reopen the accepted evidence.

Unit tests cover record validation, candidate preservation, repeated visits, execution ordering, evaluation targets, event retries, writer locking, artifact integrity, and recovery.

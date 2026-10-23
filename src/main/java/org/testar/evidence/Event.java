package org.testar.evidence;

public sealed interface Event {
    record TestRunStarted(TestRun data) implements Event {
        public TestRunStarted {
            Validation.present(data, "TestRun");
        }
    }

    record TestSequenceStarted(TestSequence data) implements Event {
        public TestSequenceStarted {
            Validation.present(data, "TestSequence");
        }
    }

    record ActionDescribed(ActionDescription data) implements Event {
        public ActionDescribed {
            Validation.present(data, "ActionDescription");
        }
    }

    record ObservationRecorded(Observation data) implements Event {
        public ObservationRecorded {
            Validation.present(data, "Observation");
        }
    }

    record ActionSelected(ActionExecution data) implements Event {
        public ActionSelected {
            Validation.present(data, "ActionExecution");
        }
    }

    record ActionExecutionReported(ActionExecutionResult data) implements Event {
        public ActionExecutionReported {
            Validation.present(data, "ActionExecutionResult");
        }
    }

    record OracleResultRecorded(OracleResult data) implements Event {
        public OracleResultRecorded {
            Validation.present(data, "OracleResult");
        }
    }

    record TransitionRecorded(String actionExecution, String resultingObservation) implements Event {
        public TransitionRecorded {
            Validation.text(actionExecution, "Action execution ID");
            Validation.text(resultingObservation, "Resulting observation");
        }
    }

    record TestSequenceFinished(String testSequence, String reason) implements Event {
        public TestSequenceFinished {
            Validation.text(testSequence, "Test sequence ID");
            Validation.present(reason, "Ending reason");
        }
    }

    record TestRunFinished(String testRun, String reason) implements Event {
        public TestRunFinished {
            Validation.text(testRun, "Test run ID");
            Validation.present(reason, "Ending reason");
        }
    }
}

package org.testar.statemodel.evidence;

public sealed interface Event {
    record RunStarted(Run data) implements Event {
        public RunStarted {
            Validation.present(data, "Run");
        }
    }

    record ActionDefined(ActionDefinition data) implements Event {
        public ActionDefined {
            Validation.present(data, "Action");
        }
    }

    record Observed(Observation data) implements Event {
        public Observed {
            Validation.present(data, "Observation");
        }
    }

    record ActionSelected(Execution data) implements Event {
        public ActionSelected {
            Validation.present(data, "Execution");
        }
    }

    record ActionSubmitted(String execution, boolean accepted, String error) implements Event {
        public ActionSubmitted {
            Validation.text(execution, "Execution ID");
            Validation.require(!accepted || error == null, "invalid_record", "Accepted submission cannot carry an error");
        }
    }

    record ActionResult(String execution, String observation) implements Event {
        public ActionResult {
            Validation.text(execution, "Execution ID");
            Validation.text(observation, "Result observation");
        }
    }

    record Checked(CheckResult data) implements Event {
        public Checked {
            Validation.present(data, "Check result");
        }
    }

    record RunFinished(String run, String reason) implements Event {
        public RunFinished {
            Validation.text(run, "Run ID");
            Validation.text(reason, "Ending reason");
        }
    }
}

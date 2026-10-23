package org.testar.evidence;

public record OracleResult(String testRun, String testSequence, String name, Oracle oracle, OracleTarget oracleTarget, OracleStatus oracleStatus, ExpectedValue expectedValue, ObservedValue observedValue, OracleSeverity oracleSeverity, Properties oracleDetails) {

    public OracleResult {
        Validation.text(testRun, "Test run ID");
        if (testSequence != null) {
            Validation.text(testSequence, "Test sequence ID");
        }
        Validation.text(name, "Oracle name");
        Validation.present(oracle, "Oracle");
        Validation.present(oracleTarget, "Oracle target");
        Validation.present(oracleStatus, "Oracle status");
        Validation.present(expectedValue, "Expected value");
        Validation.present(oracleDetails, "Oracle details");
    }
}

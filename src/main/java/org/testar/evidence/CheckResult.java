package org.testar.evidence;

public record CheckResult(String run, String name, CheckRule rule, CheckTarget target, CheckStatus status,
                          ExpectedValue expected, CheckReading actual, Properties details) {
    public CheckResult {
        Validation.text(run, "Run ID");
        Validation.text(name, "Evaluation name");
        Validation.present(rule, "Rule");
        Validation.present(target, "Target");
        Validation.present(status, "Check status");
        Validation.present(expected, "Expectation");
        Validation.present(details, "Details");
    }
}

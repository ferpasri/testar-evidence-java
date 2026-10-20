package org.testar.statemodel;

public record ActionCandidate(String action, String target) {
    public ActionCandidate {
        Validation.text(action, "Action ID");
        if (target != null) {
            Validation.text(target, "Target widget");
        }
    }
}

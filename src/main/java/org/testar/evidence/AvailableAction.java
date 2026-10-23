package org.testar.evidence;

public record AvailableAction(String actionDescription, String targetWidget) {

    public AvailableAction {
        Validation.text(actionDescription, "Action description ID");
        if (targetWidget != null) {
            Validation.text(targetWidget, "Target widget");
        }
    }
}

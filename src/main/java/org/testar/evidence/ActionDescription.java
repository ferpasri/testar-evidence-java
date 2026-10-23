package org.testar.evidence;

public record ActionDescription(String id, String actionType, Properties properties) {

    public ActionDescription {
        Validation.text(id, "Action description ID");
        Validation.text(actionType, "Action type");
        Validation.present(properties, "Properties");
    }
}

package org.testar.statemodel.evidence;

public record ActionDefinition(String id, String kind, Properties properties) {
    public ActionDefinition {
        Validation.text(id, "Action ID");
        Validation.text(kind, "Action kind");
        Validation.present(properties, "Properties");
    }
}

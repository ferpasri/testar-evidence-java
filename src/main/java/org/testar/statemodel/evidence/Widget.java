package org.testar.statemodel.evidence;

public record Widget(String id, String parent, Properties properties) {
    public Widget {
        Validation.text(id, "Widget ID");
        Validation.present(properties, "Properties");
        if (parent != null) {
            Validation.text(parent, "Parent widget");
            Validation.require(!id.equals(parent), "invalid_record", "Widget cannot be its own parent");
        }
    }
}

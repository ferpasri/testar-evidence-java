package org.testar.statemodel.evidence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ConcreteState(String id, Properties properties, List<Widget> widgets, CaptureScope capture) {
    public ConcreteState {
        Validation.text(id, "Snapshot ID");
        Validation.present(properties, "Properties");
        Validation.present(capture, "Capture scope");
        widgets = List.copyOf(Validation.present(widgets, "Widgets"));
        Validation.require(capture != CaptureScope.UNAVAILABLE || widgets.isEmpty(),
            "invalid_record", "Unavailable capture cannot contain widgets");
        Map<String, Widget> indexed = new HashMap<>();
        for (Widget widget : widgets) {
            Validation.require(indexed.put(widget.id(), widget) == null, "conflict", "Duplicate widget: " + widget.id());
        }
        Set<String> complete = new HashSet<>();
        for (Widget widget : widgets) {
            Set<String> ancestry = new HashSet<>();
            String current = widget.id();
            while (current != null && !complete.contains(current)) {
                Validation.require(ancestry.add(current), "invalid_record", "Widget hierarchy contains a cycle");
                Widget ancestor = indexed.get(current);
                Validation.require(ancestor != null, "unknown_reference", "Unknown parent widget: " + current);
                current = ancestor.parent();
            }
            complete.addAll(ancestry);
        }
    }
}

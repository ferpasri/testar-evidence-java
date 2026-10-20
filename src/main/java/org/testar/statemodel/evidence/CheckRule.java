package org.testar.statemodel.evidence;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckRule(String id, String version, Properties parameters) {
    public CheckRule {
        Validation.text(id, "Rule ID");
        if (version != null) {
            Validation.text(version, "Rule version");
        }
    }
}

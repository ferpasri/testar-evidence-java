package org.testar.evidence;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Oracle(String id, String version, Properties parameters) {
    public Oracle {
        Validation.text(id, "Oracle ID");
        if (version != null) {
            Validation.text(version, "Oracle version");
        }
    }
}

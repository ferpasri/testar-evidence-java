package org.testar.evidence;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OracleSeverity(String label, BigDecimal value) {
}

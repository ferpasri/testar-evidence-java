package org.testar.evidence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Set;

public final class EvidenceJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64)
            .maxStringLength(16 * 1024 * 1024).build()).build())
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .build();

    private EvidenceJson() {
    }

    public static JsonNode parse(String json) {
        try {
            JsonNode result = MAPPER.readTree(json);
            Validation.require(result != null && !result.isMissingNode(), "invalid_record", "JSON value is required");
            return result;
        } catch (IOException error) {
            throw invalid(error);
        }
    }

    public static JsonNode tree(Object value) {
        if (value instanceof OracleResult result) {
            return oracleResultTree(result);
        }
        return normalize(MAPPER.valueToTree(value));
    }

    static JsonNode normalize(JsonNode value) {
        validateNumbers(value);
        return parse(write(value));
    }

    private static void validateNumbers(JsonNode value) {
        if (value.isFloatingPointNumber()) {
            Validation.require(!value.isDouble() && !value.isFloat() || Double.isFinite(value.doubleValue()),
                "invalid_record", "JSON numbers must be finite");
        }
        if (value.isContainerNode()) {
            value.forEach(EvidenceJson::validateNumbers);
        }
    }

    public static String write(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException error) {
            throw invalid(error);
        }
    }

    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    private static <T> T convert(JsonNode value, Class<T> type) {
        try {
            return MAPPER.treeToValue(value, type);
        } catch (IOException error) {
            throw invalid(error);
        }
    }

    public static Manifest readManifest(String json) {
        JsonNode value = parse(json);
        fields(value, Set.of("schema_version", "evidence_id"));
        return convert(value, Manifest.class);
    }

    public static String writeManifest(Manifest manifest) {
        return write(tree(manifest));
    }

    public static EvidenceEvent readEvent(String json) {
        JsonNode root = parse(json);
        fields(root, Set.of("schema_version", "id", "event"));
        JsonNode wrapped = root.get("event");
        fields(wrapped, Set.of("event_type", "data"));
        String eventType = requiredText(wrapped, "event_type");
        JsonNode data = wrapped.get("data");
        Validation.require(data != null && data.isObject(), "invalid_record", "Event data must be an object");
        Event event = switch (eventType) {
            case "test_run_started" -> new Event.TestRunStarted(convert(data, TestRun.class));
            case "test_sequence_started" -> new Event.TestSequenceStarted(convert(data, TestSequence.class));
            case "action_described" -> new Event.ActionDescribed(convert(data, ActionDescription.class));
            case "observation_recorded" -> new Event.ObservationRecorded(readObservation(data));
            case "action_selected" -> {
                Validation.require(data.has("target_widget"), "invalid_record", "target_widget is required and can be null");
                yield new Event.ActionSelected(convert(data, ActionExecution.class));
            }
            case "action_execution_reported" -> {
                Validation.require(data.has("error"), "invalid_record", "error is required and can be null");
                yield new Event.ActionExecutionReported(convert(data, ActionExecutionResult.class));
            }
            case "transition_recorded" -> convert(data, Event.TransitionRecorded.class);
            case "oracle_result_recorded" -> new Event.OracleResultRecorded(readOracleResult(data));
            case "test_sequence_finished" -> convert(data, Event.TestSequenceFinished.class);
            case "test_run_finished" -> convert(data, Event.TestRunFinished.class);
            default -> throw new EvidenceException("invalid_record", "Unknown event type: " + eventType);
        };
        JsonNode version = root.get("schema_version");
        Validation.require(version != null && version.isIntegralNumber() && version.canConvertToInt(),
            "invalid_record", "Schema version must be an integer");
        return new EvidenceEvent(version.intValue(), requiredText(root, "id"), event);
    }

    private static Observation readObservation(JsonNode data) {
        requiredText(data, "available_action_scope");
        JsonNode state = data.get("recorded_state");
        Validation.require(state != null && state.isObject(), "invalid_record", "recorded_state is required");
        JsonNode widgets = state.get("widgets");
        Validation.require(widgets != null && widgets.isArray(), "invalid_record", "widgets must be an array");
        for (JsonNode widget : widgets) {
            Validation.require(widget.has("parent"), "invalid_record", "Widget parent is required and can be null");
        }
        JsonNode available = data.get("available_actions");
        Validation.require(available != null && available.isArray(), "invalid_record", "available_actions must be an array");
        for (JsonNode action : available) {
            Validation.require(action.has("target_widget"), "invalid_record", "Action target_widget is required and can be null");
        }
        return convert(data, Observation.class);
    }

    private static OracleResult readOracleResult(JsonNode data) {
        fields(data, Set.of("test_run", "name", "oracle", "oracle_target", "oracle_status", "oracle_details"),
            Set.of("test_sequence", "expected_value", "observed_value", "oracle_severity"));
        JsonNode oracle = data.get("oracle");
        fields(oracle, Set.of("id"), Set.of("version", "parameters"));
        if (oracle.has("version")) {
            requiredText(oracle, "version");
        }
        if (oracle.has("parameters")) {
            Validation.require(oracle.get("parameters").isObject(), "invalid_record", "Oracle parameters must be an object");
        }
        ObservedValue observed = null;
        if (data.has("observed_value")) {
            Validation.require(data.get("observed_value").isObject(), "invalid_record", "Observed value must be an object");
            observed = convert(data.get("observed_value"), ObservedValue.class);
        }
        OracleSeverity severity = null;
        if (data.has("oracle_severity")) {
            JsonNode value = data.get("oracle_severity");
            fields(value, Set.of("label"), Set.of("value"));
            Validation.require(value.get("label").isTextual(), "invalid_record", "Severity label must be text");
            if (value.has("value")) {
                Validation.require(value.get("value").isNumber(), "invalid_record", "Severity value must be numeric");
            }
            severity = convert(value, OracleSeverity.class);
        }
        ExpectedValue expected = data.has("expected_value")
            ? new ExpectedValue(true, data.get("expected_value")) : ExpectedValue.unspecified();
        return new OracleResult(requiredText(data, "test_run"), data.has("test_sequence") ? requiredText(data, "test_sequence") : null,
            requiredText(data, "name"), convert(oracle, Oracle.class), convert(data.get("oracle_target"), OracleTarget.class),
            convert(data.get("oracle_status"), OracleStatus.class), expected, observed, severity, new Properties(data.get("oracle_details")));
    }

    public static String writeEvent(EvidenceEvent envelope) {
        ObjectNode root = object();
        root.put("schema_version", envelope.schemaVersion());
        root.put("id", envelope.id());
        ObjectNode wrapped = root.putObject("event");
        Event event = envelope.event();
        String eventType;
        JsonNode data;
        if (event instanceof Event.TestRunStarted value) {
            eventType = "test_run_started";
            data = tree(value.data());
        } else if (event instanceof Event.TestSequenceStarted value) {
            eventType = "test_sequence_started";
            data = tree(value.data());
        } else if (event instanceof Event.ActionDescribed value) {
            eventType = "action_described";
            data = tree(value.data());
        } else if (event instanceof Event.ObservationRecorded value) {
            eventType = "observation_recorded";
            data = tree(value.data());
        } else if (event instanceof Event.ActionSelected value) {
            eventType = "action_selected";
            data = tree(value.data());
        } else if (event instanceof Event.ActionExecutionReported value) {
            eventType = "action_execution_reported";
            data = tree(value.data());
        } else if (event instanceof Event.TransitionRecorded value) {
            eventType = "transition_recorded";
            data = tree(value);
        } else if (event instanceof Event.OracleResultRecorded value) {
            eventType = "oracle_result_recorded";
            data = oracleResultTree(value.data());
        } else if (event instanceof Event.TestSequenceFinished value) {
            eventType = "test_sequence_finished";
            data = tree(value);
        } else if (event instanceof Event.TestRunFinished value) {
            eventType = "test_run_finished";
            data = tree(value);
        } else {
            throw new EvidenceException("invalid_record", "Unknown event");
        }
        wrapped.put("event_type", eventType);
        wrapped.set("data", data);
        return write(root);
    }

    public static JsonNode oracleResultTree(OracleResult result) {
        ObjectNode data = object();
        data.put("test_run", result.testRun());
        if (result.testSequence() != null) {
            data.put("test_sequence", result.testSequence());
        }
        data.put("name", result.name());
        data.set("oracle", tree(result.oracle()));
        data.set("oracle_target", tree(result.oracleTarget()));
        data.put("oracle_status", result.oracleStatus().wireName());
        if (result.expectedValue().supplied()) {
            data.set("expected_value", result.expectedValue().value());
        }
        if (result.observedValue() != null) {
            data.set("observed_value", tree(result.observedValue()));
        }
        if (result.oracleSeverity() != null) {
            data.set("oracle_severity", tree(result.oracleSeverity()));
        }
        data.set("oracle_details", result.oracleDetails().asJson());
        return data;
    }

    private static String requiredText(JsonNode data, String field) {
        JsonNode value = data.get(field);
        Validation.require(value != null && value.isTextual(), "invalid_record", field + " must be text");
        return Validation.text(value.textValue(), field);
    }

    private static void fields(JsonNode value, Set<String> required) {
        fields(value, required, Set.of());
    }

    private static void fields(JsonNode value, Set<String> required, Set<String> optional) {
        Validation.require(value != null && value.isObject(), "invalid_record", "Expected a JSON object");
        value.fieldNames().forEachRemaining(field -> Validation.require(required.contains(field) || optional.contains(field),
            "invalid_record", "Unknown field: " + field));
        for (String field : required) {
            Validation.require(value.has(field), "invalid_record", "Missing field: " + field);
        }
    }

    private static EvidenceException invalid(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof EvidenceException evidenceError) {
            return evidenceError;
        }
        return new EvidenceException("invalid_record", "Invalid JSON record: " + error.getMessage());
    }
}

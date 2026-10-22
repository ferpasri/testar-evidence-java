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
        return convert(parse(json), Manifest.class);
    }

    public static String writeManifest(Manifest manifest) {
        return write(tree(manifest));
    }

    public static EventEnvelope readEvent(String json) {
        JsonNode root = parse(json);
        fields(root, Set.of("schema_version", "id", "event"));
        JsonNode wrapped = root.get("event");
        fields(wrapped, Set.of("kind", "data"));
        String kind = requiredText(wrapped, "kind");
        JsonNode data = wrapped.get("data");
        Validation.require(data != null && data.isObject(), "invalid_record", "Event data must be an object");
        Event event = switch (kind) {
            case "run_started" -> new Event.RunStarted(convert(data, Run.class));
            case "action_defined" -> new Event.ActionDefined(convert(data, ActionDefinition.class));
            case "observed" -> new Event.Observed(readObservation(data));
            case "action_selected" -> new Event.ActionSelected(convert(data, Execution.class));
            case "action_submitted" -> {
                Validation.require(data.has("accepted") && data.get("accepted").isBoolean(),
                    "invalid_record", "Submission accepted must be a boolean");
                yield convert(data, Event.ActionSubmitted.class);
            }
            case "action_result" -> convert(data, Event.ActionResult.class);
            case "checked" -> new Event.Checked(readCheck(data));
            case "run_finished" -> convert(data, Event.RunFinished.class);
            default -> throw new ModelException("invalid_record", "Unknown event kind: " + kind);
        };
        JsonNode version = root.get("schema_version");
        Validation.require(version != null && version.isIntegralNumber() && version.canConvertToInt(),
            "invalid_record", "Schema version must be an integer");
        return new EventEnvelope(version.intValue(), requiredText(root, "id"), event);
    }

    private static Observation readObservation(JsonNode data) {
        if (data.has("candidate_scope")) {
            requiredText(data, "candidate_scope");
        }
        return convert(data, Observation.class);
    }

    private static CheckResult readCheck(JsonNode data) {
        fields(data, Set.of("run", "name", "rule", "target", "status", "expected", "actual", "details"));
        JsonNode rule = data.get("rule");
        Validation.require(rule != null && rule.isObject(), "invalid_record", "Rule must be an object");
        if (rule.has("version")) {
            requiredText(rule, "version");
        }
        if (rule.has("parameters")) {
            Validation.require(rule.get("parameters").isObject(), "invalid_record", "Rule parameters must be an object");
        }
        CheckReading actual = null;
        if (data.has("actual")) {
            Validation.require(data.get("actual").isObject(), "invalid_record", "Actual reading must be an object");
            actual = convert(data.get("actual"), CheckReading.class);
        }
        ExpectedValue expected = data.has("expected")
            ? new ExpectedValue(true, data.get("expected")) : ExpectedValue.unspecified();
        Properties details = data.has("details") ? new Properties(data.get("details")) : Properties.empty();
        return new CheckResult(requiredText(data, "run"), requiredText(data, "name"), convert(rule, CheckRule.class),
            convert(data.get("target"), CheckTarget.class), convert(data.get("status"), CheckStatus.class),
            expected, actual, details);
    }

    public static String writeEvent(EventEnvelope envelope) {
        ObjectNode root = object();
        root.put("schema_version", envelope.schemaVersion());
        root.put("id", envelope.id());
        ObjectNode wrapped = root.putObject("event");
        Event event = envelope.event();
        String kind;
        JsonNode data;
        if (event instanceof Event.RunStarted value) {
            kind = "run_started";
            data = tree(value.data());
        } else if (event instanceof Event.ActionDefined value) {
            kind = "action_defined";
            data = tree(value.data());
        } else if (event instanceof Event.Observed value) {
            kind = "observed";
            data = tree(value.data());
        } else if (event instanceof Event.ActionSelected value) {
            kind = "action_selected";
            data = tree(value.data());
        } else if (event instanceof Event.ActionSubmitted value) {
            kind = "action_submitted";
            data = tree(value);
        } else if (event instanceof Event.ActionResult value) {
            kind = "action_result";
            data = tree(value);
        } else if (event instanceof Event.Checked value) {
            kind = "checked";
            data = checkTree(value.data());
        } else if (event instanceof Event.RunFinished value) {
            kind = "run_finished";
            data = tree(value);
        } else {
            throw new ModelException("invalid_record", "Unknown event");
        }
        wrapped.put("kind", kind);
        wrapped.set("data", data);
        return write(root);
    }

    public static JsonNode checkTree(CheckResult check) {
        ObjectNode data = object();
        data.put("run", check.run());
        data.put("name", check.name());
        data.set("rule", tree(check.rule()));
        data.set("target", tree(check.target()));
        data.put("status", check.status().wireName());
        if (check.expected().supplied()) {
            data.set("expected", check.expected().value());
        }
        if (check.actual() != null) {
            data.set("actual", tree(check.actual()));
        }
        data.set("details", check.details().asJson());
        return data;
    }

    private static String requiredText(JsonNode data, String field) {
        JsonNode value = data.get(field);
        Validation.require(value != null && value.isTextual(), "invalid_record", field + " must be text");
        return Validation.text(value.textValue(), field);
    }

    private static void fields(JsonNode value, Set<String> allowed) {
        Validation.require(value != null && value.isObject(), "invalid_record", "Expected a JSON object");
        value.fieldNames().forEachRemaining(field -> Validation.require(allowed.contains(field),
            "invalid_record", "Unknown field: " + field));
    }

    private static ModelException invalid(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ModelException modelError) {
            return modelError;
        }
        return new ModelException("invalid_record", "Invalid JSON record: " + error.getMessage());
    }
}

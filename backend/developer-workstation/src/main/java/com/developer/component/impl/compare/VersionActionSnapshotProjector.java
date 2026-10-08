package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Compare-only Action projection. Never rewrites saved snapshots or consults current design. */
final class VersionActionSnapshotProjector {
    static final String OPAQUE_PREFIX = "!HIDDEN_ACTION_PAYLOAD:";
    private final ObjectMapper mapper;

    VersionActionSnapshotProjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    JsonNode project(JsonNode source) {
        ObjectNode snapshot = source.deepCopy();
        JsonNode actions = snapshot.has("actions") ? snapshot.path("actions") : snapshot.path("actionDefinitions");
        Map<String, String> names = namesById(actions);
        for (JsonNode action : actions) {
            if (!(action.path("configJson") instanceof ObjectNode config)) continue;
            if ("COMPOSITE".equals(action.path("actionType").asText()) && config.path("subActions").isArray()) {
                ArrayNode references = mapper.createArrayNode();
                config.path("subActions").forEach(id -> references.add(
                        names.getOrDefault(id.asText(), "unresolved:" + id.asText())));
                config.set("subActions", references);
            }
            if ("API_CALL".equals(action.path("actionType").asText())) {
                for (String field : new String[]{"headers", "body"}) {
                    if (config.hasNonNull(field)) config.set(field, safePayload(config.get(field)));
                }
            }
        }
        return snapshot;
    }

    /** Old Compare returns whole values, so keep IDs/encoding and never expose internal digests. */
    JsonNode projectForLegacyCompare(JsonNode source) {
        ObjectNode snapshot = source.deepCopy();
        JsonNode actions = snapshot.has("actions") ? snapshot.path("actions") : snapshot.path("actionDefinitions");
        for (JsonNode action : actions) {
            if (!(action instanceof ObjectNode object) || !"API_CALL".equals(action.path("actionType").asText())) continue;
            JsonNode saved = action.path("configJson");
            boolean encoded = saved.isTextual();
            try {
                JsonNode parsed = encoded ? mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(saved.asText()) : saved;
                if (!(parsed instanceof ObjectNode config)) {
                    if (encoded) object.put("configJson", "[hidden configuration]");
                    continue;
                }
                config = config.deepCopy();
                for (String field : new String[]{"headers", "body"}) {
                    if (!config.hasNonNull(field)) continue;
                    JsonNode safe = safePayload(config.get(field));
                    config.set(field, safe.isTextual() && safe.asText().startsWith(OPAQUE_PREFIX)
                            ? mapper.getNodeFactory().textNode("[hidden configuration]") : safe);
                }
                JsonNode scrubbed = SnapshotSensitiveDataFilter.redact(config);
                object.set("configJson", encoded ? mapper.getNodeFactory().textNode(mapper.writeValueAsString(scrubbed)) : scrubbed);
            } catch (JsonProcessingException e) {
                // POLICY(privacy): malformed legacy configuration is opaque, not a raw secret-bearing preview.
                object.put("configJson", "[hidden configuration]");
            }
        }
        return snapshot;
    }

    static Map<String, String> namesById(JsonNode actions) {
        Map<String, String> names = new HashMap<>();
        for (JsonNode action : actions) {
            if (!action.hasNonNull("actionId")) continue;
            String id = action.path("actionId").asText();
            if (names.putIfAbsent(id, action.path("actionName").asText()) != null) {
                throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                        "A saved version contains duplicate Action identity");
            }
        }
        return names;
    }

    private JsonNode safePayload(JsonNode value) {
        if (!value.isTextual()) {
            try {
                return scrubNested(SnapshotSensitiveDataFilter.redact(value));
            } catch (JsonProcessingException e) {
                return mapper.getNodeFactory().textNode(OPAQUE_PREFIX + digest(value.toString()));
            }
        }
        String text = value.asText();
        if (text.isBlank()) return value;
        try {
            JsonNode parsed = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            if (parsed != null && (parsed.isObject() || parsed.isArray())) {
                return mapper.getNodeFactory().textNode(mapper.writeValueAsString(
                        scrubNested(SnapshotSensitiveDataFilter.redact(parsed))));
            }
        } catch (JsonProcessingException e) {
            // POLICY(privacy): designers allow arbitrary request text, not only JSON.
            // Non-JSON/scalar bodies cannot be safely scrubbed by key; compare an opaque digest.
            // No raw text, parser exception or digest is returned in either diff representation.
        }
        return mapper.getNodeFactory().textNode(OPAQUE_PREFIX + digest(text));
    }

    private JsonNode scrubNested(JsonNode node) throws JsonProcessingException {
        if (node instanceof ObjectNode object) {
            ObjectNode result = mapper.createObjectNode();
            for (var entry : object.properties()) result.set(entry.getKey(), scrubNested(entry.getValue()));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (JsonNode item : node) result.add(scrubNested(item));
            return result;
        }
        if (node.isTextual()) {
            String text = node.asText().stripLeading();
            if ("[REDACTED]".equals(text)) return node;
            if (text.startsWith("{") || text.startsWith("[")) {
                JsonNode embedded = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
                return mapper.getNodeFactory().textNode(mapper.writeValueAsString(
                        scrubNested(SnapshotSensitiveDataFilter.redact(embedded))));
            }
        }
        return node;
    }

    private String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.platform.common.jdbc.SubTableRowIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable semantic position, independent of executions/activations and of mutable display values. */
public final class AssignmentSlotIdentity {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private AssignmentSlotIdentity() { }

    public static Position position(String definition, String node, List<Map<String,Object>> calls,
            List<Map<String,Object>> multiInstances) {
        if (definition == null || definition.isBlank() || node == null || node.isBlank()) {
            throw new IllegalArgumentException("Missing frozen task position");
        }
        Map<String,Object> descriptor = Map.of("version", 1, "definition", definition, "node", node,
                "calls", calls, "multiInstances", multiInstances);
        try {
            String encoded = JSON.writeValueAsString(descriptor);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
            return new Position(hash, encoded);
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode assignment position", e);
        }
    }

    /** Prefer the platform identity; otherwise preserve every configured primary-key component. */
    public static Map<String,Object> rowKey(Map<?,?> row, List<String> primaryKeys) {
        Object platformId = row.get(SubTableRowIdentity.CANONICAL_FIELD);
        if (platformId instanceof String id && !id.isBlank()) {
            return Map.of(SubTableRowIdentity.CANONICAL_FIELD, id);
        }
        if (primaryKeys == null || primaryKeys.isEmpty()) throw new IllegalStateException("MI item has no frozen row identity");
        Map<?,?> values = row.get("rowKey") instanceof Map<?,?> nested ? nested : row;
        Map<String,Object> key = new LinkedHashMap<>();
        for (String field : primaryKeys) {
            Object value = values.get(field);
            if (values != row && row.containsKey(field) && !java.util.Objects.equals(row.get(field),value)) {
                throw new IllegalStateException("MI item has contradictory primary-key values");
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)
                    || value instanceof String text && text.isBlank()) {
                throw new IllegalStateException("MI item has an incomplete primary key");
            }
            key.put(field, value);
        }
        return Map.copyOf(key);
    }

    public record Position(String key, String descriptor) { }
}

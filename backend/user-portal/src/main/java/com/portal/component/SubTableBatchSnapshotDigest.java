package com.portal.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Integrity checksum, not an authorization token or a cryptographic signature. */
final class SubTableBatchSnapshotDigest {
    private SubTableBatchSnapshotDigest() { }

    static String compute(Map<String, Object> snapshot, ObjectMapper mapper) {
        Map<String, Object> definition = new LinkedHashMap<>(snapshot);
        definition.remove("definitionDigest");
        try {
            byte[] canonical = mapper.writeValueAsBytes(normalize(mapper.valueToTree(definition)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot compute batch snapshot definition digest", ex);
        }
    }

    private static Object normalize(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(field -> sorted.put(field.getKey(), normalize(field.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            var values = new ArrayList<>();
            node.forEach(value -> values.add(normalize(value)));
            return values;
        }
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros();
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNull()) return null;
        throw new IllegalArgumentException("Unsupported batch snapshot JSON value");
    }
}

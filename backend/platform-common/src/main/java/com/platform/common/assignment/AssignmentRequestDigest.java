package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Canonical request identity: object key order is insignificant; array order and explicit JSON null are significant. */
public final class AssignmentRequestDigest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AssignmentRequestDigest() { }

    public static String of(Object request) {
        if (request == null) throw new IllegalArgumentException("Missing assignment request");
        try {
            byte[] bytes = JSON.writeValueAsBytes(canonical(JSON.valueToTree(request)));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot digest assignment request", e);
        }
    }

    private static JsonNode canonical(JsonNode value) {
        if (value instanceof ObjectNode object) {
            ObjectNode ordered = JSON.createObjectNode();
            var keys = new java.util.TreeSet<String>();
            object.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> ordered.set(key, canonical(object.get(key))));
            return ordered;
        }
        if (value instanceof ArrayNode array) {
            ArrayNode ordered = JSON.createArrayNode();
            array.forEach(item -> ordered.add(canonical(item)));
            return ordered;
        }
        return value;
    }
}

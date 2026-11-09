package com.admin.audit;

import com.admin.entity.AuditLog;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Secrets never shown in the Admin operation audit (D-21: every Technical Lead and Auditor reads it): password hashes,
 * passwords, tokens, keys, and the value of a config flagged {@code encrypted}. One rule for both directions: snapshots
 * are masked when written, and rows written before this rule are masked when read (stored rows are not changed).
 */
public final class AuditSecrets {

    static final String MASK = "***";

    private static final Set<String> SECRET_FIELDS = Set.of(
            "password", "passwordHash", "newPassword", "oldPassword", "initialPassword", "confirmPassword",
            "secret", "token", "accessToken", "refreshToken", "apiKey");

    private AuditSecrets() {
    }

    /** The node with every secret value replaced, at any depth. */
    public static JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            boolean encryptedConfig = object.path("encrypted").asBoolean(false);
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                boolean secret = SECRET_FIELDS.contains(field.getKey())
                        || (encryptedConfig && ("configValue".equals(field.getKey())
                        || "defaultValue".equals(field.getKey())));
                if (secret && !field.getValue().isNull()) {
                    field.setValue(TextNode.valueOf(MASK));
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(AuditSecrets::mask);
        }
        return node;
    }

    /**
     * A stored snapshot with its secrets masked. Only a whole JSON object or array is a snapshot; any other text
     * (including text that merely starts with a JSON literal, such as "3 users created") is returned as it is.
     */
    public static String maskJson(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        JsonNode node;
        try {
            node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
        } catch (Exception notJson) {
            return json;
        }
        if (node == null || !node.isContainerNode()) {
            return json;
        }
        try {
            return mapper.writeValueAsString(mask(node));
        } catch (Exception unwritable) {
            throw new IllegalStateException("Cannot write a masked audit snapshot", unwritable);
        }
    }

    /** A copy of the row for readers: the managed entity is never changed. */
    public static AuditLog forReaders(ObjectMapper mapper, AuditLog log) {
        return log.toBuilder()
                .oldValue(maskJson(mapper, log.getOldValue()))
                .newValue(maskJson(mapper, log.getNewValue()))
                .changeDetails(maskJson(mapper, log.getChangeDetails()))
                .build();
    }
}

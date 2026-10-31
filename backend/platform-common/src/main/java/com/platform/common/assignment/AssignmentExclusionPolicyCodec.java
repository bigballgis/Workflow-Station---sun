package com.platform.common.assignment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict V1 decoder; absent properties are handled by the caller, damaged properties never disable rules. */
public final class AssignmentExclusionPolicyCodec {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> FIELDS = Set.of("schemaVersion", "excludeInitiator", "excludePreviousHandlers",
            "excludeSelectedNodeHandlers", "selectedNodeKeys", "backup");
    private static final Set<String> BACKUP_FIELDS = Set.of("enabled", "mode", "buSource", "businessUnitCode", "roleCodes", "userRefs");

    private AssignmentExclusionPolicyCodec() { }

    /** Decode a present policy, rejecting malformed JSON, unknown fields and unsupported versions. */
    public static AssignmentExclusionPolicy decode(String value) {
        if (value == null || value.isBlank() || value.length() > 32768) throw invalid("Missing or oversized policy");
        final JsonNode node;
        try {
            node = JSON.readTree(value);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid assignment exclusion JSON", e);
        }
        object(node, FIELDS);
        if (!node.path("schemaVersion").isIntegralNumber() || !node.path("schemaVersion").canConvertToInt()
                || node.path("schemaVersion").intValue() != 1) throw invalid("Unsupported schemaVersion");
        boolean initiator = bool(node, "excludeInitiator");
        boolean previous = bool(node, "excludePreviousHandlers");
        boolean selected = bool(node, "excludeSelectedNodeHandlers");
        var keys = strings(node.get("selectedNodeKeys"), "selectedNodeKeys");
        if (selected && keys.isEmpty()) throw invalid("Selected nodes must not be empty");
        if (!selected && !keys.isEmpty()) throw invalid("Disabled selected-node rule must have no references");
        var backup = backup(node.get("backup"));
        var policy = new AssignmentExclusionPolicy(1, initiator, previous, selected, keys, backup);
        if (backup.enabled() && !policy.enabled()) throw invalid("Backup requires an exclusion rule");
        return policy;
    }

    private static AssignmentExclusionPolicy.Backup backup(JsonNode node) {
        object(node, BACKUP_FIELDS);
        boolean enabled = bool(node, "enabled");
        if (!enabled) {
            // R12: inactive backup fields are not executable configuration.
            return new AssignmentExclusionPolicy.Backup(false, null, null, null, List.of(), List.of());
        }
        var mode = enumeration(node, "mode", AssignmentExclusionPolicy.Mode.class);
        if (mode == AssignmentExclusionPolicy.Mode.USERS) {
            var users = strings(node.get("userRefs"), "userRefs");
            if (users.isEmpty()) throw invalid("Backup users must not be empty");
            forbid(node, "buSource", "businessUnitCode", "roleCodes");
            return new AssignmentExclusionPolicy.Backup(true, mode, null, null, List.of(), users);
        }
        forbid(node, "userRefs");
        var source = enumeration(node, "buSource", AssignmentExclusionPolicy.BuSource.class);
        var roles = strings(node.get("roleCodes"), "roleCodes");
        if (roles.isEmpty()) throw invalid("Backup roles must not be empty");
        String bu = null;
        if (source == AssignmentExclusionPolicy.BuSource.FIXED_BU) bu = text(node, "businessUnitCode");
        else forbid(node, "businessUnitCode");
        return new AssignmentExclusionPolicy.Backup(true, mode, source, bu, roles, List.of());
    }

    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid("Expected a policy object");
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid("Unknown policy field: " + field);
        });
    }

    private static boolean bool(JsonNode node, String name) {
        if (!node.path(name).isBoolean()) throw invalid("Expected boolean: " + name);
        return node.get(name).booleanValue();
    }

    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual() || node.get(name).textValue().isBlank()
                || node.get(name).textValue().length() > 255) throw invalid("Expected bounded reference: " + name);
        return node.get(name).textValue().strip();
    }

    private static List<String> strings(JsonNode node, String name) {
        if (node == null || !node.isArray() || node.size() > 1000) throw invalid("Expected bounded list: " + name);
        var out = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 255) {
                throw invalid("Invalid reference in " + name);
            }
            String value = item.textValue().strip();
            if (!seen.add(value)) throw invalid("Duplicate reference in " + name);
            out.add(value);
        }
        return List.copyOf(out);
    }

    private static <T extends Enum<T>> T enumeration(JsonNode node, String name, Class<T> type) {
        try {
            return Enum.valueOf(type, text(node, name));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid assignment policy option: " + name, e);
        }
    }

    private static void forbid(JsonNode node, String... names) {
        for (String name : names) if (node.has(name)) throw invalid("Inactive backup source: " + name);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid assignment exclusion policy: " + message);
    }
}

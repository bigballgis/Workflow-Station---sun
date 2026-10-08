package com.developer.component.impl.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pair-local control identities, independent of editable field bindings and sibling order. */
final class VersionFormControlMatcher {
    static final String KEY = "_compareControlKey";
    private VersionFormControlMatcher() {}

    static boolean align(JsonNode before, JsonNode after) {
        boolean partial = siblings(before.isArray() ? before : before.path("rule"), after.isArray() ? after : after.path("rule"));
        Set<String> subForms = new HashSet<>();
        before.path("subForms").fieldNames().forEachRemaining(subForms::add);
        after.path("subForms").fieldNames().forEachRemaining(subForms::add);
        for (String key : subForms) partial |= align(before.path("subForms").path(key), after.path("subForms").path(key));
        return partial;
    }

    private static boolean siblings(JsonNode before, JsonNode after) {
        var old = entries(before); var next = entries(after);
        var pair = VersionRecordMatcher.align(old, next, true);
        boolean partial = pair.partial() || old.stream().anyMatch(e -> e.id().isBlank() && e.alternateId().isBlank())
                || next.stream().anyMatch(e -> e.id().isBlank() && e.alternateId().isBlank());
        Set<String> keys = new HashSet<>();
        pair.before().fieldNames().forEachRemaining(keys::add); pair.after().fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            JsonNode a = pair.before().path(key); JsonNode b = pair.after().path(key);
            annotate(a, key); annotate(b, key);
            partial |= siblings(a.path("children"), b.path("children"));
            partial |= siblings(a.path("rule"), b.path("rule"));
        }
        return partial;
    }

    private static List<VersionRecordMatcher.Entry> entries(JsonNode rules) {
        List<VersionRecordMatcher.Entry> result = new ArrayList<>();
        if (!rules.isArray()) return result;
        for (JsonNode rule : rules) {
            if (!rule.isObject()) continue;
            ObjectNode canonical = ((ObjectNode) rule).deepCopy(); canonical.remove("_fc_id");
            result.add(new VersionRecordMatcher.Entry(rule.path("name").asText(""), rule.path("_fc_id").asText(""),
                    rule.path("field").asText(""), rule, canonical.toString()));
        }
        return result;
    }

    private static void annotate(JsonNode rule, String key) {
        if (rule.isObject()) ((ObjectNode) rule).put(KEY, key);
    }
}

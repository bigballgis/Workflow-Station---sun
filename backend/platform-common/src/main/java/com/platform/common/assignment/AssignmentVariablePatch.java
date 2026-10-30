package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Accepted minimal JSON writes with their original content baseline; array order and row content are significant. */
public final class AssignmentVariablePatch {
    private AssignmentVariablePatch() { }

    public static List<Entry> between(ObjectNode baseline, ObjectNode accepted) {
        if (baseline == null || accepted == null) throw new IllegalArgumentException("Missing assignment effect baseline");
        var result = new ArrayList<Entry>();
        differences(List.of(), baseline, accepted, result);
        return List.copyOf(result);
    }

    public static ObjectNode apply(ObjectNode current, List<Entry> entries) {
        if (current == null || entries == null) throw new IllegalArgumentException("Missing assignment effect");
        ObjectNode updated = current.deepCopy();
        var paths = new java.util.HashSet<List<String>>();
        for (var entry : entries) {
            if (!paths.add(entry.path())) throw new IllegalArgumentException("Duplicate assignment effect path");
        }
        for (var path : paths) {
            for (int size=1; size<path.size(); size++) {
                if (paths.contains(path.subList(0, size))) throw new IllegalArgumentException("Overlapping assignment effect paths");
            }
        }
        for (var entry : entries) {
            ObjectNode parent = parent(updated, entry.path());
            String field = entry.path().get(entry.path().size() - 1);
            JsonNode value = parent.get(field);
            if (!same(value, entry.expectedPresent(), entry.expectedValue())) {
                // An independently committed identical value needs no overwrite.
                if (same(value, entry.resultPresent(), entry.resultValue())) continue;
                throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
            }
            if (entry.resultPresent()) parent.set(field, entry.resultValue().deepCopy());
            else parent.remove(field);
        }
        return updated;
    }

    private static void differences(List<String> prefix, ObjectNode before, ObjectNode after, List<Entry> entries) {
        var keys = new TreeSet<String>();
        before.fieldNames().forEachRemaining(keys::add);
        after.fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            var path = new ArrayList<>(prefix); path.add(key);
            JsonNode previous = before.get(key), next = after.get(key);
            if (java.util.Objects.equals(previous, next)) continue;
            if (previous instanceof ObjectNode oldObject && next instanceof ObjectNode newObject) {
                differences(List.copyOf(path), oldObject, newObject, entries);
            } else entries.add(new Entry(path, previous != null, previous, next != null, next));
        }
    }

    private static ObjectNode parent(ObjectNode root, List<String> path) {
        ObjectNode node = root;
        for (int i = 0; i < path.size() - 1; i++) {
            JsonNode next = node.get(path.get(i));
            if (!(next instanceof ObjectNode object)) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
            node = object;
        }
        return node;
    }

    private static boolean same(JsonNode current, boolean present, JsonNode value) {
        return present ? current != null && current.equals(value) : current == null;
    }

    public record Entry(List<String> path, boolean expectedPresent, JsonNode expectedValue,
            boolean resultPresent, JsonNode resultValue) {
        public Entry {
            if (path == null || path.isEmpty() || path.stream().anyMatch(java.util.Objects::isNull)
                    || expectedPresent && expectedValue == null || resultPresent && resultValue == null) {
                throw new IllegalArgumentException("Invalid assignment effect entry");
            }
            path = List.copyOf(path);
            expectedValue = expectedValue == null ? null : expectedValue.deepCopy();
            resultValue = resultValue == null ? null : resultValue.deepCopy();
        }
    }
}

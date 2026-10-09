package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Shared, bounded object-level comparison for module-specific semantic projections. */
final class VersionSemanticDiffSupport {

    private static final int MAX_ITEMS = 300;
    private static final int MAX_FIELDS_PER_ITEM = 30;
    private static final int MAX_PREVIEW = 240;

    record DesignObject(String type, String key, String label, String scope, JsonNode properties) {}

    private VersionSemanticDiffSupport() {}

    static VersionCompareResponse.SemanticDiff compare(String status, String scope,
            Map<String, DesignObject> before, Map<String, DesignObject> after) {
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<VersionCompareResponse.SemanticChange> items = new ArrayList<>();
        int added = 0;
        int modified = 0;
        int removed = 0;
        boolean truncated = false;
        for (String key : keys) {
            DesignObject old = before.get(key);
            DesignObject next = after.get(key);
            if (old != null && next != null && old.properties().equals(next.properties())) continue;
            String type = old == null ? "ADDED" : next == null ? "REMOVED" : "MODIFIED";
            if (old == null) added++;
            else if (next == null) removed++;
            else modified++;
            DesignObject chosen = next == null ? old : next;
            List<VersionCompareResponse.FieldChange> fields = new ArrayList<>();
            if ("DOCUMENT".equals(chosen.type())) {
                var field = VersionDocumentPreview.field(old == null ? null : old.properties(), next == null ? null : next.properties());
                fields.add(field);
                truncated |= field.textContext() != null && field.textContext().omittedChanges();
            } else fields(old == null ? null : old.properties(), next == null ? null : next.properties(), "", fields);
            if (fields.size() > MAX_FIELDS_PER_ITEM) {
                fields = new ArrayList<>(fields.subList(0, MAX_FIELDS_PER_ITEM));
                truncated = true;
            }
            if (items.size() < MAX_ITEMS) {
                items.add(new VersionCompareResponse.SemanticChange(chosen.type(), preview(key),
                        preview(chosen.label()), type, chosen.scope(), List.copyOf(fields)));
            } else {
                truncated = true;
            }
        }
        return new VersionCompareResponse.SemanticDiff(status, scope,
                new VersionCompareResponse.Counts(added, modified, removed), truncated, List.copyOf(items));
    }

    static VersionCompareResponse.SemanticDiff unavailable(String status, String scope) {
        return new VersionCompareResponse.SemanticDiff(status, scope,
                new VersionCompareResponse.Counts(0, 0, 0), false, List.of());
    }

    private static void fields(JsonNode old, JsonNode next, String path,
            List<VersionCompareResponse.FieldChange> result) {
        if (result.size() > MAX_FIELDS_PER_ITEM) return;
        if (old == null && next == null) return;
        if (old != null && old.equals(next)) return;
        if ((old == null || old.isObject()) && (next == null || next.isObject())) {
            Set<String> names = new TreeSet<>();
            if (old != null) old.fieldNames().forEachRemaining(names::add);
            if (next != null) next.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                fields(old == null ? null : old.get(name), next == null ? null : next.get(name),
                        path.isEmpty() ? name : path + "." + name, result);
            }
            return;
        }
        if ((old == null || old.isArray()) && (next == null || next.isArray())
                && (old != null || next != null)) {
            int oldSize = old == null ? 0 : old.size();
            int nextSize = next == null ? 0 : next.size();
            int length = Math.max(oldSize, nextSize);
            if (length == 0) {
                result.add(new VersionCompareResponse.FieldChange(preview(path), preview(old), preview(next)));
                return;
            }
            for (int index = 0; index < length && result.size() <= MAX_FIELDS_PER_ITEM; index++) {
                fields(index < oldSize ? old.get(index) : null,
                        index < nextSize ? next.get(index) : null,
                        path + "[" + index + "]", result);
            }
            return;
        }
        boolean hiddenConfiguration = path.endsWith("Fingerprint");
        result.add(new VersionCompareResponse.FieldChange(preview(path.isEmpty() ? "content" : path),
                hiddenConfiguration ? hidden(old) : preview(old),
                hiddenConfiguration ? hidden(next) : preview(next)));
    }

    private static String hidden(JsonNode value) {
        return value == null || value.isNull() ? null : "[hidden configuration]";
    }

    private static String preview(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isObject()) return "{…}";
        if (value.isArray()) return "[" + value.size() + " items]";
        String rendered = value.isTextual() ? value.asText() : value.toString();
        if (rendered.startsWith(VersionActionSnapshotProjector.OPAQUE_PREFIX)) return "[hidden configuration]";
        return preview(rendered);
    }

    private static String preview(String value) {
        return value.length() > MAX_PREVIEW ? value.substring(0, MAX_PREVIEW) + "…" : value;
    }
}

package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saved Form IDs recognize renames; child identities are scoped to the matched form. */
final class VersionFormMatcher {
    record Pair(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after) {}
    private VersionFormMatcher() {}

    static Pair align(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after,
                      boolean sameFu) {
        var old = entries(before, sameFu ? after : null); var next = entries(after, sameFu ? before : null);
        var pair = VersionRecordMatcher.align(old, next, sameFu);
        boolean partial = pair.partial() || old.stream().anyMatch(e -> e.id().isBlank())
                || next.stream().anyMatch(e -> e.id().isBlank());
        Set<String> keys = new HashSet<>();
        pair.before().fieldNames().forEachRemaining(keys::add); pair.after().fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            partial |= VersionFormControlMatcher.align(pair.before().path(key).path("configJson"), pair.after().path(key).path("configJson"));
        }
        return new Pair(replace(before, pair.before(), partial), replace(after, pair.after(), partial));
    }

    private static List<VersionRecordMatcher.Entry> entries(VersionSnapshotNormalizer.Normalized source,
                                                            VersionSnapshotNormalizer.Normalized paired) {
        List<VersionRecordMatcher.Entry> result = new ArrayList<>();
        JsonNode forms = source.sanitizedSource().path(source.modern() ? "forms" : "formDefinitions");
        Map<String, JsonNode> peers = new LinkedHashMap<>();
        if (paired != null) paired.sanitizedSource().path(paired.modern() ? "forms" : "formDefinitions").forEach(form -> {
            if (form.hasNonNull("formId")) peers.put(form.path("formId").asText(), form);
        });
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < forms.size(); i++) {
            JsonNode raw = forms.get(i);
            String id = savedId(raw.path("formId"));
            if (!id.isBlank() && !ids.add(id)) throw invalidId();
            JsonNode value = localReferences(source.modules().get(VersionSnapshotNormalizer.FORMS)
                    .path("forms").path("saved-" + i), raw.path("formName").asText(),
                    localBindingReferences(raw, peers.get(id)));
            result.add(new VersionRecordMatcher.Entry(id, raw.path("formName").asText(), value, value.toString()));
        }
        return result;
    }

    private static Set<String> localBindingReferences(JsonNode form, JsonNode peer) {
        Set<String> references = new HashSet<>();
        collectBindingReferences(form.path("tableBindings"), form.path("formName").asText(), references);
        if (peer != null) collectBindingReferences(peer.path("tableBindings"), form.path("formName").asText(), references);
        return references;
    }

    private static void collectBindingReferences(JsonNode bindings, String formName, Set<String> references) {
        bindings.forEach(binding -> references.add(VersionReferenceResolver.bindingReference(formName, binding)));
    }

    private static JsonNode localReferences(JsonNode value, String formName, Set<String> localBindings) {
        if (value.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(localReferences(item, formName, localBindings))); return result;
        }
        if (!value.isObject()) return value.deepCopy();
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        value.properties().forEach(entry -> {
            String key = entry.getKey(); JsonNode item = entry.getValue();
            if ("subForms".equals(key) && item.isObject()) {
                ObjectNode subForms = result.putObject(key);
                item.properties().forEach(sub -> {
                    String localKey = local(sub.getKey(), formName);
                    if (subForms.has(localKey)) throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                            "A saved version contains duplicate local sub-form identities");
                    subForms.set(localKey, localReferences(sub.getValue(), formName, localBindings));
                });
            } else if (Set.of("bindingRef", "targetBinding").contains(key) && item.isTextual()
                    && localBindings.contains(item.asText())) {
                result.put(key, local(item.asText(), formName));
            } else result.set(key, localReferences(item, formName, localBindings));
        });
        return result;
    }

    private static String local(String reference, String formName) {
        String prefix = formName + "/";
        return reference.startsWith(prefix) ? reference.substring(prefix.length()) : reference;
    }

    private static String savedId(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) throw invalidId();
        return Long.toString(value.asLong());
    }

    private static DeveloperBusinessException invalidId() {
        return new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID", "A saved version has invalid Form record IDs");
    }

    private static VersionSnapshotNormalizer.Normalized replace(VersionSnapshotNormalizer.Normalized source,
                                                               ObjectNode forms, boolean partial) {
        Map<String, JsonNode> modules = new LinkedHashMap<>(source.modules());
        ObjectNode group = ((ObjectNode) modules.get(VersionSnapshotNormalizer.FORMS)).deepCopy(); group.set("forms", forms);
        modules.put(VersionSnapshotNormalizer.FORMS, group);
        ObjectNode snapshot = ((ObjectNode) source.sanitizedSource()).deepCopy();
        snapshot.put("_formComparePartial", partial || snapshot.path("_formComparePartial").asBoolean());
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), snapshot, source.modern());
    }
}

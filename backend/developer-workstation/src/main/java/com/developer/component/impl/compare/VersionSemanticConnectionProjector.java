package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.developer.component.impl.compare.VersionSemanticDiffSupport.DesignObject;

/** Connection UID is stable for a saved connection; monitor ruleUid is not and is never used here. */
final class VersionSemanticConnectionProjector {

    record Pair(Map<String, DesignObject> before, Map<String, DesignObject> after) {}

    Pair project(VersionSnapshotNormalizer.Normalized base,
                 VersionSnapshotNormalizer.Normalized target) {
        JsonNode oldItems = base.modules().get(VersionSnapshotNormalizer.CONNECTIONS).path("connections");
        JsonNode newItems = target.modules().get(VersionSnapshotNormalizer.CONNECTIONS).path("connections");
        Map<String, String> oldUids = uidsByName(base.sanitizedSource().path("connections"));
        Map<String, String> newUids = uidsByName(target.sanitizedSource().path("connections"));
        invert(oldUids);
        Map<String, String> newNamesByUid = invert(newUids);
        Set<String> matchedNewNames = new HashSet<>();
        Map<String, DesignObject> before = new LinkedHashMap<>();
        Map<String, DesignObject> after = new LinkedHashMap<>();
        oldItems.properties().forEach(entry -> {
            String oldName = entry.getKey();
            String uid = oldUids.get(oldName);
            String targetName = uid == null ? null : newNamesByUid.get(uid);
            String sameNameNewUid = newUids.get(oldName);
            boolean nameFallbackAllowed = uid == null || sameNameNewUid == null;
            if (targetName == null && nameFallbackAllowed
                    && newItems.has(oldName) && !matchedNewNames.contains(oldName)) {
                targetName = oldName;
            }
            String key = "connection/" + oldName;
            before.put(key, object(key, oldName, entry.getValue()));
            if (targetName != null && matchedNewNames.add(targetName)) {
                after.put(key, object(key, targetName, newItems.get(targetName)));
            }
        });
        newItems.properties().forEach(entry -> {
            if (matchedNewNames.add(entry.getKey())) {
                String key = "connection/" + entry.getKey();
                if (before.containsKey(key)) key = "connection/new/" + entry.getKey();
                after.put(key, object(key, entry.getKey(), entry.getValue()));
            }
        });
        return new Pair(before, after);
    }

    private DesignObject object(String key, String name, JsonNode value) {
        return new DesignObject("CONNECTION", key, name, "DESIGN", value);
    }

    private Map<String, String> uidsByName(JsonNode items) {
        Map<String, String> result = new HashMap<>();
        if (!items.isArray()) return result;
        for (JsonNode item : items) {
            String name = item.path("name").asText("");
            String uid = item.path("connectionUid").asText("");
            if (!name.isBlank() && !uid.isBlank()) result.put(name, uid);
        }
        return result;
    }

    private Map<String, String> invert(Map<String, String> values) {
        Map<String, String> result = new HashMap<>();
        values.forEach((name, uid) -> {
            if (result.putIfAbsent(uid, name) != null) {
                throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                        "A saved version contains duplicate connection identity");
            }
        });
        return result;
    }
}

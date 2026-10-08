package com.developer.component.impl.compare;

import com.developer.entity.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pair-local identity: saved IDs recognize renames within one FU; names survive ID regeneration. */
final class VersionActionMatcher {
    record Pair(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after) {}

    private VersionActionMatcher() {}

    static Pair align(Version base, Version target, VersionSnapshotNormalizer.Normalized before,
                      VersionSnapshotNormalizer.Normalized after) {
        if (base.getFunctionUnit() == null || target.getFunctionUnit() == null
                || base.getFunctionUnit().getId() == null
                || !base.getFunctionUnit().getId().equals(target.getFunctionUnit().getId())) {
            return new Pair(before, after);
        }
        Map<String, String> oldIds = savedIds(before);
        Map<String, String> newIds = savedIds(after);
        Map<String, String> oldKeys = new LinkedHashMap<>();
        Map<String, String> newKeys = new LinkedHashMap<>();
        oldIds.forEach((id, oldName) -> {
            String newName = newIds.get(id);
            if (newName == null) return;
            // Identity keys contain names, never database IDs. Prefixes separate a new object
            // reusing a renamed object's old name from that object's matched pair.
            oldKeys.put(oldName, "matched/" + oldName);
            newKeys.put(newName, "matched/" + oldName);
        });
        boolean rename = oldIds.entrySet().stream().anyMatch(entry -> newIds.containsKey(entry.getKey())
                && !entry.getValue().equals(newIds.get(entry.getKey())));
        if (!rename) return new Pair(before, after);
        return new Pair(rekey(before, oldKeys), rekey(after, newKeys));
    }

    private static Map<String, String> savedIds(VersionSnapshotNormalizer.Normalized snapshot) {
        return VersionActionSnapshotProjector.namesById(snapshot.sanitizedSource()
                .path(snapshot.modern() ? "actions" : "actionDefinitions"));
    }

    private static VersionSnapshotNormalizer.Normalized rekey(VersionSnapshotNormalizer.Normalized source,
                                                             Map<String, String> keys) {
        Map<String, JsonNode> modules = new LinkedHashMap<>(source.modules());
        ObjectNode module = ((ObjectNode) modules.get(VersionSnapshotNormalizer.ACTIONS)).deepCopy();
        ObjectNode actions = module.objectNode();
        module.path("actions").properties().forEach(entry -> actions.set(
                keys.getOrDefault(entry.getKey(), "unmatched/" + entry.getKey()), entry.getValue()));
        module.set("actions", actions);
        modules.put(VersionSnapshotNormalizer.ACTIONS, module);
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), source.sanitizedSource(), source.modern());
    }
}

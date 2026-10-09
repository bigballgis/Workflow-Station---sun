package com.developer.component.impl.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;

/** Legacy metadata is unknown, not empty. Compare only fields captured by both versions. */
final class VersionBasicMatcher {
    private VersionBasicMatcher() {}

    static VersionActionMatcher.Pair align(VersionSnapshotNormalizer.Normalized before,
                                          VersionSnapshotNormalizer.Normalized after) {
        var old = ((ObjectNode) before.modules().get(VersionSnapshotNormalizer.BASIC)).deepCopy();
        var next = ((ObjectNode) after.modules().get(VersionSnapshotNormalizer.BASIC)).deepCopy();
        for (String field : List.of("tags", "icon")) {
            if (!before.sanitizedSource().has(field) || !after.sanitizedSource().has(field)) {
                old.remove(field); next.remove(field);
            }
        }
        return new VersionActionMatcher.Pair(replace(before, old), replace(after, next));
    }

    static boolean partial(VersionSnapshotNormalizer.Normalized snapshot) {
        return !snapshot.sanitizedSource().has("tags") || !snapshot.sanitizedSource().has("icon");
    }

    private static VersionSnapshotNormalizer.Normalized replace(VersionSnapshotNormalizer.Normalized source, JsonNode basic) {
        var modules = new LinkedHashMap<>(source.modules());
        modules.put(VersionSnapshotNormalizer.BASIC, basic);
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), source.sanitizedSource(), source.modern());
    }
}

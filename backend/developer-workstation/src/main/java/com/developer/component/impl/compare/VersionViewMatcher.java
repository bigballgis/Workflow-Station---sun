package com.developer.component.impl.compare;

import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Snapshot-only View identity. Names are not unique and historic missing IDs are never invented. */
final class VersionViewMatcher {
    record Pair(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after) {}
    private VersionViewMatcher() {}

    static Pair align(Version base, Version target, VersionSnapshotNormalizer.Normalized before,
                      VersionSnapshotNormalizer.Normalized after) {
        var old = entries(before); var next = entries(after);
        boolean missingIds = old.stream().anyMatch(e -> e.id().isBlank()) || next.stream().anyMatch(e -> e.id().isBlank());
        var pair = VersionRecordMatcher.align(old, next, sameFunctionUnit(base, target));
        boolean partial = missingIds || pair.partial();
        return new Pair(replace(before, pair.before(), partial), replace(after, pair.after(), partial));
    }

    static boolean sameFunctionUnit(Version base, Version target) {
        return base.getFunctionUnit() != null && target.getFunctionUnit() != null
                && base.getFunctionUnit().getId() != null
                && base.getFunctionUnit().getId().equals(target.getFunctionUnit().getId());
    }

    private static List<VersionRecordMatcher.Entry> entries(VersionSnapshotNormalizer.Normalized snapshot) {
        List<VersionRecordMatcher.Entry> result = new ArrayList<>();
        JsonNode values = snapshot.sanitizedSource().path("mainTableViews");
        if (!snapshot.modern() || values.isMissingNode()) return result;
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            JsonNode raw = values.get(i);
            String id = savedId(raw.path("viewId"));
            if (!id.isBlank() && !ids.add(id)) throw invalid();
            JsonNode value = snapshot.modules().get(VersionSnapshotNormalizer.VIEWS).path("mainTableViews").path("saved-" + i);
            ObjectNode design = ((ObjectNode) value).deepCopy(); design.remove("status");
            String table = raw.path("mainTableName").asText(); String name = raw.path("viewName").asText();
            result.add(new VersionRecordMatcher.Entry(id, table.length() + ":" + table + name.length() + ":" + name,
                    value, design.toString()));
        }
        result.sort(Comparator.comparing(VersionRecordMatcher.Entry::name)
                .thenComparing(VersionRecordMatcher.Entry::canonical).thenComparing(VersionRecordMatcher.Entry::id));
        return result;
    }

    private static String savedId(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) throw invalid();
        return Long.toString(value.asLong());
    }

    private static VersionSnapshotNormalizer.Normalized replace(VersionSnapshotNormalizer.Normalized source,
                                                               ObjectNode values, boolean partial) {
        Map<String, JsonNode> modules = new LinkedHashMap<>(source.modules());
        ObjectNode group = JsonNodeFactory.instance.objectNode(); group.set("mainTableViews", values);
        modules.put(VersionSnapshotNormalizer.VIEWS, group);
        ObjectNode snapshot = ((ObjectNode) source.sanitizedSource()).deepCopy(); snapshot.put("_viewComparePartial", partial);
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), snapshot, source.modern());
    }

    private static DeveloperBusinessException invalid() {
        return new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID", "A saved version has invalid View record IDs");
    }
}

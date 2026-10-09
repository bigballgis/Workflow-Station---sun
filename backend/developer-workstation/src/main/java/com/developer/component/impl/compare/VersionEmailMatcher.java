package com.developer.component.impl.compare;

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

/** Pair-local identity for saved email records. Never reads live designs or rewrites snapshots. */
final class VersionEmailMatcher {
    record Pair(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after) {}
    private VersionEmailMatcher() {}

    static Pair align(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after,
                      boolean sameFu) {
        for (boolean monitor : new boolean[]{false, true}) {
            var old = entries(before, monitor); var next = entries(after, monitor);
            var matched = VersionRecordMatcher.align(old, next, sameFu, true);
            boolean partial = matched.partial() || old.stream().anyMatch(VersionEmailMatcher::missingId)
                    || next.stream().anyMatch(VersionEmailMatcher::missingId);
            before = replace(before, matched.before(), monitor, partial);
            after = replace(after, matched.after(), monitor, partial);
        }
        return new Pair(before, after);
    }

    private static boolean missingId(VersionRecordMatcher.Entry e) { return e.id().isBlank() && e.alternateId().isBlank(); }

    private static List<VersionRecordMatcher.Entry> entries(VersionSnapshotNormalizer.Normalized snapshot, boolean monitor) {
        String collection = monitor ? "emailMonitors" : "emailTemplates";
        String module = monitor ? VersionSnapshotNormalizer.EMAIL_MONITORS : VersionSnapshotNormalizer.EMAIL_TEMPLATES;
        List<VersionRecordMatcher.Entry> result = new ArrayList<>();
        JsonNode values = snapshot.sanitizedSource().path(collection);
        if (!snapshot.modern() || values.isMissingNode()) return result;
        HashSet<String> ids = new HashSet<>(); HashSet<String> uids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            JsonNode raw = values.get(i);
            String id = savedId(raw.path(monitor ? "ruleId" : "templateId"));
            String uid = monitor ? savedUid(raw.path("ruleUid")) : "";
            if (!id.isBlank() && !ids.add(id) || !uid.isBlank() && !uids.add(uid)) throw invalid();
            JsonNode value = snapshot.modules().get(module).path(collection).path("saved-" + i);
            String name = raw.path("name").asText(); String event = monitor ? raw.path("startEventId").asText("") : "";
            String fallback = name.length() + ":" + name + event.length() + ":" + event;
            result.add(new VersionRecordMatcher.Entry(monitor ? uid : id, monitor ? id : "", fallback, value, value.toString()));
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

    private static String savedUid(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual() || value.asText().isBlank()) throw invalid();
        return value.asText();
    }

    private static VersionSnapshotNormalizer.Normalized replace(VersionSnapshotNormalizer.Normalized source,
                                                               ObjectNode values, boolean monitor, boolean partial) {
        Map<String, JsonNode> modules = new LinkedHashMap<>(source.modules());
        ObjectNode group = JsonNodeFactory.instance.objectNode(); group.set(monitor ? "emailMonitors" : "emailTemplates", values);
        modules.put(monitor ? VersionSnapshotNormalizer.EMAIL_MONITORS : VersionSnapshotNormalizer.EMAIL_TEMPLATES, group);
        ObjectNode raw = ((ObjectNode) source.sanitizedSource()).deepCopy();
        raw.put(monitor ? "_emailMonitorsComparePartial" : "_emailTemplatesComparePartial", partial);
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), raw, source.modern());
    }

    private static DeveloperBusinessException invalid() {
        return new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID", "A saved version has invalid email record IDs");
    }
}

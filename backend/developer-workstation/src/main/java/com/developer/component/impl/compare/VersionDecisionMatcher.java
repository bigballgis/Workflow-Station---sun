package com.developer.component.impl.compare;

import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Snapshot-only Decision matching. Never consult current design to invent historical metadata. */
final class VersionDecisionMatcher {
    private static final VersionXmlNormalizer XML = new VersionXmlNormalizer();
    private static final List<String> METADATA = List.of("decisionKey", "decisionName", "description", "hitPolicy");
    record Entry(JsonNode source, boolean captured, String id, String key, String xml, String canonical, String xmlId,
                 int occurrence) {}
    record Pair(VersionSnapshotNormalizer.Normalized before, VersionSnapshotNormalizer.Normalized after) {}

    private VersionDecisionMatcher() {}

    static List<Entry> entries(JsonNode snapshot, boolean modern) {
        boolean captured = snapshot.has("decisionDefinitions");
        JsonNode values = snapshot.path(captured ? "decisionDefinitions" : modern ? "decisions" : "decisionDefinitions");
        if (values.isMissingNode()) return List.of();
        if (!values.isArray()) throw invalid();
        List<Entry> result = new ArrayList<>();
        java.util.Set<String> capturedKeys = new java.util.HashSet<>();
        for (JsonNode item : values) {
            if (captured && !item.isObject() || !captured && !item.isTextual()) throw invalid();
            if (captured) {
                for (String field : List.of("decisionKey", "decisionName", "description", "hitPolicy", "dmnXml")) {
                    JsonNode value = item.path(field);
                    if (!value.isMissingNode() && !value.isNull() && !value.isTextual()) throw invalid();
                }
            }
            String xml = captured ? item.path("dmnXml").asText("") : item.asText();
            String xmlId = XML.stableDecisionKey(xml);
            String key = captured ? item.path("decisionKey").asText("") : xmlId;
            if (captured && key.isBlank()) throw invalid();
            if (captured && !capturedKeys.add(key)) throw invalid();
            String id = captured ? item.path("decisionId").asText(item.path("id").asText("")) : "";
            result.add(new Entry(item, captured, id, key, xml, XML.normalize(xml), xmlId, result.size()));
        }
        result.sort(Comparator.comparing(Entry::key).thenComparing(Entry::canonical).thenComparing(Entry::id));
        return result;
    }

    static ObjectNode initial(JsonNode snapshot, boolean modern) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        int index = 0;
        for (Entry entry : entries(snapshot, modern)) result.set("saved-" + index++, value(entry, entry.captured()));
        return result;
    }

    static Pair align(Version base, Version target, VersionSnapshotNormalizer.Normalized before,
                      VersionSnapshotNormalizer.Normalized after) {
        List<Entry> old = new ArrayList<>(entries(before.sanitizedSource(), before.modern()));
        List<Entry> next = new ArrayList<>(entries(after.sanitizedSource(), after.modern()));
        List<Entry> oldRemaining = new ArrayList<>(old);
        List<Entry> newRemaining = new ArrayList<>(next);
        // Occurrence preserves identical XML copies in memory only; it is not a business identity.
        List<Matched> matched = new ArrayList<>();
        boolean sameFu = base.getFunctionUnit() != null && target.getFunctionUnit() != null
                && base.getFunctionUnit().getId() != null
                && base.getFunctionUnit().getId().equals(target.getFunctionUnit().getId());
        if (sameFu) matchUnique(oldRemaining, newRemaining, e -> e.captured() ? e.id() : "", matched, false);
        matchUnique(oldRemaining, newRemaining, e -> e.captured() ? e.key() : "", matched, false);
        // FALLBACK(migration): #1681/#1683 XML-only history has no record identities.
        // Exact XML preserves duplicate copies; uncertain matches remain added/removed with PARTIAL scope.
        // Remove this path only when XML-only historical versions are no longer supported.
        matchExactLegacy(oldRemaining, newRemaining, matched);
        matchUnique(oldRemaining, newRemaining, Entry::xmlId, matched, true);
        // FALLBACK(migration): preserve the existing bounded text diff for a single
        // unidentified legacy XML document per side. Semantic status remains UNPARSEABLE;
        // this is not an inferred business identity and does not apply to duplicate IDs.
        if (oldRemaining.size() == 1 && newRemaining.size() == 1
                && !oldRemaining.get(0).captured() && !newRemaining.get(0).captured()
                && oldRemaining.get(0).xmlId().isBlank() && newRemaining.get(0).xmlId().isBlank()) {
            matched.add(new Matched(oldRemaining.remove(0), newRemaining.remove(0)));
        }
        boolean partial = !before.sanitizedSource().has("decisionDefinitions")
                || !after.sanitizedSource().has("decisionDefinitions");
        ObjectNode oldValues = JsonNodeFactory.instance.objectNode();
        ObjectNode newValues = JsonNodeFactory.instance.objectNode();
        ArrayNode oldProjection = JsonNodeFactory.instance.arrayNode();
        ArrayNode newProjection = JsonNodeFactory.instance.arrayNode();
        Map<String, Integer> occurrences = new HashMap<>();
        for (Matched pair : matched) {
            Entry a = pair.before(); Entry b = pair.after();
            String low = a.key().compareTo(b.key()) <= 0 ? a.key() : b.key();
            String high = a.key().compareTo(b.key()) <= 0 ? b.key() : a.key();
            String key = unique("matched/" + low + "/" + high, occurrences);
            boolean metadata = a.captured() && b.captured();
            add(oldValues, oldProjection, key, a, metadata);
            add(newValues, newProjection, key, b, metadata);
        }
        for (Entry entry : oldRemaining) add(oldValues, oldProjection,
                unique(unmatchedKey(entry), occurrences), entry, entry.captured());
        // Separate occurrence counters preserve equivalent unmatched keys in reverse comparison.
        Map<String, Integer> newOccurrences = new HashMap<>();
        for (Entry entry : newRemaining) add(newValues, newProjection,
                unique(unmatchedKey(entry), newOccurrences), entry, entry.captured());
        return new Pair(replace(before, oldValues, oldProjection, partial),
                replace(after, newValues, newProjection, partial));
    }

    private record Matched(Entry before, Entry after) {}

    private static void matchUnique(List<Entry> before, List<Entry> after, Function<Entry, String> identity,
                                    List<Matched> matched, boolean legacyXmlIdentity) {
        Map<String, List<Entry>> a = group(before, identity); Map<String, List<Entry>> b = group(after, identity);
        java.util.Set<Entry> oldMatched = new java.util.HashSet<>();
        java.util.Set<Entry> newMatched = new java.util.HashSet<>();
        for (var entry : a.entrySet()) {
            List<Entry> next = b.get(entry.getKey());
            if (entry.getKey().isBlank() || entry.getValue().size() != 1 || next == null || next.size() != 1) continue;
            Entry old = entry.getValue().get(0); Entry target = next.get(0);
            // An XML ID is not an identity across two independently captured records.
            if (legacyXmlIdentity && old.captured() && target.captured()) continue;
            matched.add(new Matched(old, target)); oldMatched.add(old); newMatched.add(target);
        }
        before.removeIf(oldMatched::contains); after.removeIf(newMatched::contains);
    }

    private static Map<String, List<Entry>> group(List<Entry> entries, Function<Entry, String> key) {
        Map<String, List<Entry>> result = new LinkedHashMap<>();
        for (Entry entry : entries) result.computeIfAbsent(key.apply(entry), ignored -> new ArrayList<>()).add(entry);
        return result;
    }

    private static void matchExactLegacy(List<Entry> before, List<Entry> after, List<Matched> matched) {
        if (before.stream().allMatch(Entry::captured) && after.stream().allMatch(Entry::captured)) return;
        Map<String, List<Entry>> available = group(after, Entry::canonical);
        java.util.Set<Entry> oldMatched = new java.util.HashSet<>();
        java.util.Set<Entry> newMatched = new java.util.HashSet<>();
        for (Entry old : before) {
            if (old.xml().isBlank()) continue;
            List<Entry> candidates = available.get(old.canonical());
            if (candidates == null) continue;
            Entry next = candidates.stream().filter(e -> !old.captured() || !e.captured()).findFirst().orElse(null);
            if (next != null) {
                matched.add(new Matched(old, next)); oldMatched.add(old); newMatched.add(next); candidates.remove(next);
            }
        }
        before.removeIf(oldMatched::contains); after.removeIf(newMatched::contains);
    }

    private static String unique(String key, Map<String, Integer> occurrences) {
        return key + "/instance-" + occurrences.merge(key, 1, Integer::sum);
    }

    private static String unmatchedKey(Entry entry) {
        // A duplicate legacy XML ID must not accidentally align two uncertain records.
        // Content distinguishes unmatched objects only; it is never a saved-record identity.
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    value(entry, entry.captured()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "unmatched/" + entry.key() + "/" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static ObjectNode value(Entry entry, boolean metadata) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("dmnXml", entry.canonical());
        if (metadata) {
            for (String field : METADATA) value.set(field, entry.source().path(field).isMissingNode()
                    ? JsonNodeFactory.instance.nullNode() : entry.source().get(field));
            value.put("dmnConfigured", !entry.xml().isBlank());
        }
        return value;
    }

    private static void add(ObjectNode values, ArrayNode projection, String key, Entry entry, boolean metadata) {
        // Legacy XML-only generic values retain their original whole-document change count.
        values.set(key, metadata ? value(entry, true) : JsonNodeFactory.instance.textNode(entry.canonical()));
        ObjectNode item = projection.addObject().put("compareKey", key).put("dmnXml", entry.xml())
                .put("xmlIdentityAvailable", !entry.xmlId().isBlank());
        if (metadata) {
            ObjectNode properties = value(entry, true); properties.remove("dmnXml");
            item.set("metadata", properties);
        }
    }

    private static VersionSnapshotNormalizer.Normalized replace(VersionSnapshotNormalizer.Normalized source,
            ObjectNode module, ArrayNode projection, boolean partial) {
        Map<String, JsonNode> modules = new LinkedHashMap<>(source.modules());
        modules.put(VersionSnapshotNormalizer.DECISIONS, module);
        ObjectNode snapshot = ((ObjectNode) source.sanitizedSource()).deepCopy();
        snapshot.set("_compareDecisions", projection);
        snapshot.put("_decisionComparePartial", partial);
        return new VersionSnapshotNormalizer.Normalized(modules, source.notCaptured(), snapshot, source.modern());
    }

    private static DeveloperBusinessException invalid() {
        return new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID", "A saved version has invalid Decision records");
    }
}

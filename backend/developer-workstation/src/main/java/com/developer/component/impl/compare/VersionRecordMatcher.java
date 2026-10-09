package com.developer.component.impl.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pair-local multiset matching shared by saved View, Form and control projections. */
final class VersionRecordMatcher {
    record Entry(String id, String alternateId, String name, JsonNode value, String canonical) {
        Entry(String id, String name, JsonNode value, String canonical) { this(id, "", name, value, canonical); }
    }
    private record Matched(Entry before, Entry after) {}
    record Pair(ObjectNode before, ObjectNode after, boolean partial) {}

    private VersionRecordMatcher() {}

    static Pair align(List<Entry> before, List<Entry> after, boolean useIds) {
        return align(before, after, useIds, false);
    }

    /** Strict saved identities are opt-in; existing portable Form/View matching is unchanged. */
    static Pair align(List<Entry> before, List<Entry> after, boolean useIds, boolean strictIds) {
        List<Entry> old = new ArrayList<>(before);
        List<Entry> next = new ArrayList<>(after);
        List<Matched> matched = new ArrayList<>();
        boolean strict = useIds && strictIds;
        if (useIds) matchUnique(old, next, Entry::id, matched, false);
        if (useIds) matchUnique(old, next, Entry::alternateId, matched, strict);
        // FALLBACK(migration): historic missing IDs and import/rollback ID regeneration.
        // Unique names are usable; ambiguous names must never be arbitrarily zipped.
        matchUnique(old, next, Entry::name, matched, strict);
        matchExact(old, next, matched, strict);
        matchUnique(old, next, Entry::name, matched, strict);
        boolean partial = ambiguous(old) || ambiguous(next);
        ObjectNode a = JsonNodeFactory.instance.objectNode();
        ObjectNode b = JsonNodeFactory.instance.objectNode();
        Map<String, Integer> occurrences = new HashMap<>();
        matched.sort(Comparator.comparing(VersionRecordMatcher::pairKey)
                .thenComparing(p -> ordered(p.before().id(), p.after().id())));
        for (Matched pair : matched) {
            String key = unique(pairKey(pair), occurrences);
            a.set(key, pair.before().value()); b.set(key, pair.after().value());
        }
        addUnmatched(a, old, strict); addUnmatched(b, next, strict);
        return new Pair(a, b, partial);
    }

    private static void matchUnique(List<Entry> before, List<Entry> after, Function<Entry, String> key,
                                    List<Matched> matched, boolean strict) {
        Map<String, List<Entry>> a = group(before, key);
        Map<String, List<Entry>> b = group(after, key);
        for (var entry : a.entrySet()) {
            List<Entry> targets = b.get(entry.getKey());
            if (entry.getKey().isBlank() || entry.getValue().size() != 1 || targets == null || targets.size() != 1) continue;
            Entry old = entry.getValue().get(0); Entry next = targets.get(0);
            if (strict && conflictingIds(old, next)) continue;
            matched.add(new Matched(old, next)); before.remove(old); after.remove(next);
        }
    }

    private static void matchExact(List<Entry> before, List<Entry> after, List<Matched> matched, boolean strict) {
        Map<String, List<Entry>> available = group(after, Entry::canonical);
        List<Entry> oldMatched = new ArrayList<>();
        for (Entry old : before) {
            List<Entry> candidates = available.get(old.canonical());
            if (candidates == null || candidates.isEmpty()) continue;
            Entry next = candidates.stream().filter(e -> !strict || !conflictingIds(old, e)).findFirst().orElse(null);
            if (next == null) continue;
            candidates.remove(next);
            matched.add(new Matched(old, next)); oldMatched.add(old); after.remove(next);
        }
        before.removeAll(oldMatched);
    }

    private static Map<String, List<Entry>> group(List<Entry> entries, Function<Entry, String> key) {
        Map<String, List<Entry>> result = new LinkedHashMap<>();
        for (Entry entry : entries) result.computeIfAbsent(key.apply(entry), ignored -> new ArrayList<>()).add(entry);
        return result;
    }

    private static boolean ambiguous(List<Entry> entries) {
        return group(entries, Entry::name).values().stream().anyMatch(v -> v.size() > 1);
    }

    private static String pairKey(Matched pair) {
        return "matched/" + digest(ordered(pair.before().canonical(), pair.after().canonical()));
    }

    private static String ordered(String a, String b) {
        return a.compareTo(b) <= 0 ? encoded(a, b) : encoded(b, a);
    }

    private static String encoded(String a, String b) { return a.length() + ":" + a + b.length() + ":" + b; }
    private static String unique(String key, Map<String, Integer> counts) {
        return key + "/instance-" + counts.merge(key, 1, Integer::sum);
    }

    private static void addUnmatched(ObjectNode values, List<Entry> remaining, boolean strict) {
        Map<String, Integer> counts = new HashMap<>();
        remaining.stream().sorted(Comparator.comparing(Entry::canonical).thenComparing(Entry::id))
                .forEach(entry -> values.set(unique("unmatched/" + digest(strict
                        ? encoded(encoded(entry.id(), entry.alternateId()), entry.canonical())
                        : entry.canonical()), counts), entry.value()));
    }

    private static boolean conflictingIds(Entry a, Entry b) {
        return conflicts(a.id(), b.id()) || conflicts(a.alternateId(), b.alternateId());
    }

    private static boolean conflicts(String a, String b) { return !a.isBlank() && !b.isBlank() && !a.equals(b); }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}

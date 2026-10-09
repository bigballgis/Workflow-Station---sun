package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Optional replay of the exact saved manual QA snapshots, without authentication or writes. */
@EnabledIfSystemProperty(named = "email.compare.evidence", matches = "true")
class VersionEmailHistoricalCompareTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @TestFactory Stream<DynamicTest> savedManualMatrix() throws Exception {
        Path dir = evidenceDirectory();
        JsonNode state = mapper.readTree(Files.readAllBytes(dir.resolve("state.json")));
        Map<Long, byte[]> snapshots = new HashMap<>();
        for (var entry : state.path("versions").properties()) {
            snapshots.put(entry.getValue().asLong(), Files.readAllBytes(dir.resolve("snapshot-" + entry.getKey() + ".json")));
        }
        return java.util.stream.StreamSupport.stream(state.path("results").spliterator(), false)
                .map(row -> DynamicTest.dynamicTest(row.path("label").asText(), () -> verify(row, state, snapshots)));
    }

    private void verify(JsonNode row, JsonNode state, Map<Long, byte[]> snapshots) throws Exception {
        long a = row.path("from").asLong(); long b = row.path("to").asLong();
        long fu = state.path("versionFu").path(Long.toString(a)).asLong(state.path("fuId").asLong());
        var forward = engine.compare(version(a, fu, snapshots), version(b, fu, snapshots));
        var reverse = engine.compare(version(b, fu, snapshots), version(a, fu, snapshots));
        String selected = row.path("module").asText();
        var expected = mapper.treeToValue(row.path("expected"), VersionCompareResponse.Counts.class);
        for (var module : forward.modules()) {
            var f = module.semantic();
            var r = reverse.modules().stream().filter(m -> m.key().equals(module.key())).findFirst().orElseThrow().semantic();
            assertEquals(module.key().equals(selected) ? expected : new VersionCompareResponse.Counts(0, 0, 0), f.counts(), module.key());
            assertEquals(new VersionCompareResponse.Counts(f.counts().removed(), f.counts().modified(), f.counts().added()), r.counts());
            for (var item : f.items()) {
                var back = r.items().stream().filter(i -> i.objectKey().equals(item.objectKey())).findFirst().orElseThrow();
                assertEquals(item.type().equals("ADDED") ? "REMOVED" : item.type().equals("REMOVED") ? "ADDED" : "MODIFIED", back.type());
                for (var leaf : item.fields()) {
                    var swapped = back.fields().stream().filter(l -> l.field().equals(leaf.field())).findFirst().orElseThrow();
                    assertEquals(leaf.oldValue(), swapped.newValue()); assertEquals(leaf.newValue(), swapped.oldValue());
                }
            }
        }
        if (row.path("label").asText().equals("api-monitor-fields-order")) {
            assertTrue(forward.modules().stream().filter(m -> m.key().equals(selected))
                    .flatMap(m -> m.semantic().items().stream()).flatMap(i -> i.fields().stream())
                    .anyMatch(f -> f.field().equals("extractionRules.fields[0].target") && !f.oldValue().equals(f.newValue())));
        }
    }

    private Version version(long id, long fu, Map<Long, byte[]> snapshots) {
        assertNotNull(snapshots.get(id), "Missing saved QA snapshot " + id);
        return Version.builder().id(id).functionUnit(FunctionUnit.builder().id(fu).build())
                .versionNumber("QA-" + id).snapshotData(snapshots.get(id)).build();
    }

    private Path evidenceDirectory() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null) {
            Path candidate = root.resolve("docs/test-evidence/email-design-2026-10-08");
            if (Files.exists(candidate.resolve("state.json"))) return candidate;
            root = root.getParent();
        }
        throw new IllegalStateException("Saved email QA evidence directory is required for this opt-in test");
    }
}

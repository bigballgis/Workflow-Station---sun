package com.developer.component.impl.compare;

import com.developer.entity.Version;
import com.developer.dto.VersionCompareResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VersionBasicDocumentCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test void tagsAndIconChangesAreVisibleAndReversible() throws Exception {
        var before = snapshot(); before.put("tags", List.of("one")); before.put("icon", null);
        var after = snapshot(); after.put("tags", List.of("two"));
        after.put("icon", Map.of("name", "QA", "category", "GENERAL", "svgContent", "<svg>new</svg>"));
        var forward = module(before, after, "BASIC");
        assertEquals("FULL", forward.semantic().scope());
        assertEquals(1, forward.semantic().counts().modified());
        assertTrue(forward.semantic().items().get(0).fields().stream().anyMatch(f -> f.field().equals("tags[0]")
                && "one".equals(f.oldValue()) && "two".equals(f.newValue())));
        var reverse = module(after, before, "BASIC");
        for (var f : forward.semantic().items().get(0).fields()) {
            var r = reverse.semantic().items().get(0).fields().stream().filter(x -> x.field().equals(f.field())).findFirst().orElseThrow();
            assertEquals(f.oldValue(), r.newValue()); assertEquals(f.newValue(), r.oldValue());
        }
    }

    @Test void missingHistoricalMetadataIsPartialAndDoesNotInventAdditions() throws Exception {
        var old = snapshot(); old.remove("tags"); old.remove("icon");
        var next = snapshot(); next.put("tags", List.of("new")); next.put("name", "Renamed");
        var result = module(old, next, "BASIC");
        assertEquals("PARTIAL", result.semantic().scope());
        assertEquals(1, result.semantic().items().get(0).fields().size());
        assertEquals("name", result.semantic().items().get(0).fields().get(0).field());
        assertEquals(0, result.counts().added());
    }

    @Test void bothDocumentTypesExposeTheChangedTailInBothDirections() throws Exception {
        for (String type : List.of("REQUIREMENTS", "DESIGN")) {
            String prefix = "# Long\n\n" + "x".repeat(280) + "\nTAIL ";
            var old = snapshot(); old.put("documents", Map.of(type, prefix + "alpha\n"));
            var next = snapshot(); next.put("documents", Map.of(type, prefix + "beta\n"));
            var forward = module(old, next, "DOCUMENTS"); var reverse = module(next, old, "DOCUMENTS");
            var f = forward.semantic().items().get(0).fields().get(0);
            var r = reverse.semantic().items().get(0).fields().get(0);
            assertTrue(f.oldValue().contains("TAIL alpha")); assertTrue(f.newValue().contains("TAIL beta"));
            assertNotEquals(f.oldValue(), f.newValue());
            assertEquals(f.oldValue(), r.newValue()); assertEquals(f.newValue(), r.oldValue());
            assertTrue(forward.changes().get(0).oldValue().contains("TAIL alpha"));
            assertEquals(1, forward.semantic().counts().modified());
        }
    }

    private Map<String, Object> snapshot() {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("snapshotSchemaVersion", 2); value.put("name", "FU");
        for (String key : List.of("tables", "forms", "actions", "decisions", "tags")) value.put(key, List.of());
        value.put("icon", null); value.put("documents", Map.of()); return value;
    }

    private VersionCompareResponse.ModuleDiff module(Map<String, Object> before, Map<String, Object> after, String key) throws Exception {
        var result = engine.compare(Version.builder().id(1L).snapshotData(mapper.writeValueAsBytes(before)).build(),
                Version.builder().id(2L).snapshotData(mapper.writeValueAsBytes(after)).build());
        return result.modules().stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
    }
}

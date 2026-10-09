package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionDecisionCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test void persistedDraftIsAddedAndRemovedWithMetadata() throws Exception {
        ObjectNode before = snapshot();
        ObjectNode after = snapshot();
        definitions(after).add(record(41, "approval", "Approval", null));
        var added = decisions(compare(before, after));
        assertEquals(1, added.semantic().counts().added());
        assertTrue(added.semantic().items().get(0).fields().stream()
                .anyMatch(f -> f.field().equals("decisionKey") && "approval".equals(f.newValue())));
        assertEquals(1, decisions(compare(after, before)).semantic().counts().removed());
    }

    @Test void metadataRenameAndClearMatchSavedIdInsteadOfDeleteAdd() throws Exception {
        ObjectNode before = snapshot();
        ObjectNode after = snapshot();
        definitions(before).add(record(41, "old_key", "Old", dmn("one", "One", false)));
        ObjectNode changed = record(41, "new_key", "New", dmn("one", "One", false));
        changed.putNull("description");
        definitions(after).add(changed);
        var diff = decisions(compare(before, after)).semantic();
        assertEquals(0, diff.counts().added() + diff.counts().removed());
        assertEquals(1, diff.counts().modified());
        assertTrue(diff.items().stream().flatMap(i -> i.fields().stream())
                .anyMatch(f -> f.field().equals("description") && "Description".equals(f.oldValue()) && f.newValue() == null));
    }

    @Test void oldDuplicateXmlIdsDoNotBlockOtherModulesOrDropDecisions() throws Exception {
        ObjectNode before = snapshot();
        ObjectNode after = snapshot();
        ((ArrayNode) after.get("decisions")).add(dmn("one", "First", false)).add(dmn("one", "Second", false));
        var diff = decisions(compare(before, after));
        assertEquals(2, diff.counts().added());
        assertEquals(2, diff.semantic().items().stream().filter(i -> i.objectType().equals("DMN_DECISION")).count());
        assertEquals("PARTIAL", diff.semantic().scope());
        ObjectNode reversed = after.deepCopy();
        ArrayNode reordered = mapper.createArrayNode().add(after.get("decisions").get(1)).add(after.get("decisions").get(0));
        reversed.set("decisions", reordered);
        assertEquals(0, decisions(compare(after, reversed)).semantic().counts().modified());
        assertEquals(0, decisions(compare(after, reversed)).semantic().counts().added());
    }

    @Test void capturedSameNamesAndXmlIdsRemainSeparate() throws Exception {
        ObjectNode after = snapshot();
        definitions(after).add(record(1, "first", "Same", dmn("one", "Same", false)));
        definitions(after).add(record(2, "second", "Same", dmn("one", "Same", false)));
        var diff = decisions(compare(snapshot(), after));
        assertEquals(2, diff.semantic().items().stream().filter(i -> i.objectType().equals("DECISION_DEFINITION")).count());
    }

    @Test void uncertainLegacyDuplicatesAreNotFalseMatchedByOccurrence() throws Exception {
        ObjectNode before = snapshot(); ObjectNode after = snapshot();
        ((ArrayNode) before.get("decisions")).add(dmn("one", "Old A", false)).add(dmn("one", "Old B", false));
        ((ArrayNode) after.get("decisions")).add(dmn("one", "New A", false)).add(dmn("one", "New B", false));
        var forward = decisions(compare(before, after));
        var reverse = decisions(compare(after, before));
        assertEquals(2, forward.counts().added());
        assertEquals(2, forward.counts().removed());
        assertEquals(0, forward.counts().modified());
        assertEquals(forward.counts().added(), reverse.counts().removed());
        assertEquals(forward.semantic().items().stream().map(VersionCompareResponse.SemanticChange::objectKey).collect(java.util.stream.Collectors.toSet()),
                reverse.semantic().items().stream().map(VersionCompareResponse.SemanticChange::objectKey).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void ruleOrderIsModifiedNotDeleted() throws Exception {
        assertOrder("ruleOrder", dmn("one", "Same", false), dmn("one", "Same", true));
    }

    @Test void inputOrderAndOutputOrderAreBusinessFields() throws Exception {
        String old = dmn("one", "Same", false);
        String next = old.replace("<input id='i1'/><input id='i2'/>", "<input id='i2'/><input id='i1'/>")
                .replace("<output id='o1'/><output id='o2'/>", "<output id='o2'/><output id='o1'/>");
        assertOrder("inputOrder", old, next);
        assertOrder("outputOrder", old, next);
    }

    @Test void regeneratedRecordIdsAndFormattingAreNotChanges() throws Exception {
        ObjectNode before = snapshot();
        ObjectNode after = snapshot();
        definitions(before).add(record(41, "approval", "Approval", dmn("one", "One", false)));
        definitions(after).add(record(91, "approval", "Approval", dmn("one", "One", false).replace("><", ">\n  <")));
        var diff = decisions(compare(before, after));
        assertEquals(0, diff.semantic().counts().added() + diff.semantic().counts().modified() + diff.semantic().counts().removed());
    }

    @Test void oldXmlToFullRecordDoesNotInventHistoricalMetadata() throws Exception {
        ObjectNode before = snapshot();
        String xml = dmn("one", "One", false);
        ((ArrayNode) before.get("decisions")).add(xml);
        ObjectNode after = snapshot();
        definitions(after).add(record(41, "external_key", "List name", xml));
        var diff = decisions(compare(before, after));
        assertEquals(0, diff.semantic().counts().added() + diff.semantic().counts().modified() + diff.semantic().counts().removed());
        assertEquals("PARTIAL", diff.semantic().scope());
    }

    private void assertOrder(String field, String old, String next) throws Exception {
        ObjectNode before = snapshot(); ObjectNode after = snapshot();
        ((ArrayNode) before.get("decisions")).add(old);
        ((ArrayNode) after.get("decisions")).add(next);
        var diff = decisions(compare(before, after)).semantic();
        assertEquals(0, diff.counts().added() + diff.counts().removed());
        assertTrue(diff.items().stream().flatMap(i -> i.fields().stream()).anyMatch(f -> f.field().startsWith(field)));
    }

    private ObjectNode snapshot() {
        ObjectNode s = mapper.createObjectNode().put("snapshotSchemaVersion", 2);
        for (String key : new String[]{"tables", "forms", "actions", "decisions"}) s.putArray(key);
        return s;
    }
    private ArrayNode definitions(ObjectNode s) {
        return s.has("decisionDefinitions") ? (ArrayNode) s.get("decisionDefinitions") : s.putArray("decisionDefinitions");
    }
    private ObjectNode record(long id, String key, String name, String xml) {
        return mapper.createObjectNode().put("decisionId", id).put("decisionKey", key)
                .put("decisionName", name).put("description", "Description").put("hitPolicy", "FIRST").put("dmnXml", xml);
    }
    private String dmn(String id, String name, boolean reversed) {
        String a = "<rule id='r1'><inputEntry id='e1'><text>1</text></inputEntry></rule>";
        String b = "<rule id='r2'><inputEntry id='e2'><text>2</text></inputEntry></rule>";
        return "<definitions xmlns='https://www.omg.org/spec/DMN/20191111/MODEL/' id='defs'><decision id='" + id
                + "' name='" + name + "'><decisionTable id='table' hitPolicy='FIRST'><input id='i1'/><input id='i2'/>"
                + "<output id='o1'/><output id='o2'/>" + (reversed ? b + a : a + b) + "</decisionTable></decision></definitions>";
    }
    private VersionCompareResponse compare(ObjectNode a, ObjectNode b) throws Exception {
        FunctionUnit fu = FunctionUnit.builder().id(42L).build();
        return engine.compare(Version.builder().id(1L).functionUnit(fu).snapshotData(mapper.writeValueAsBytes(a)).build(),
                Version.builder().id(2L).functionUnit(fu).snapshotData(mapper.writeValueAsBytes(b)).build());
    }
    private VersionCompareResponse.ModuleDiff decisions(VersionCompareResponse response) {
        return response.modules().stream().filter(m -> m.key().equals("DECISIONS")).findFirst().orElseThrow();
    }
}

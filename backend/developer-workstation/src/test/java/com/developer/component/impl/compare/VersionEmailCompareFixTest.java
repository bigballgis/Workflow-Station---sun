package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VersionEmailCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test void templateRenameIsOneModificationWithHumanLabelAndReversibleValues() throws Exception {
        ObjectNode a = snapshot(); email(a, false, 7, "Old");
        ObjectNode b = a.deepCopy(); record(b, false).put("name", "New");
        assertRename(a, b, false);
    }

    @Test void monitorRenameUsesSavedIdentity() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Old");
        ObjectNode b = a.deepCopy(); record(b, true).put("name", "New");
        assertRename(a, b, true);
    }

    @Test void sameNameNewTemplateIdIsReplacementNotNoChanges() throws Exception {
        ObjectNode a = snapshot(); email(a, false, 7, "Same");
        ObjectNode b = a.deepCopy(); record(b, false).put("templateId", 8);
        assertReplacement(a, b, false);
    }

    @Test void sameNameNewRuleIdAndUidIsReplacement() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Same");
        ObjectNode b = a.deepCopy(); record(b, true).put("ruleId", 8).put("ruleUid", "uid-8");
        assertReplacement(a, b, true);
    }

    @Test void stableRuleUidAllowsRegeneratedNumericIdButConflictingUidIsNotMatched() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Old");
        ObjectNode b = a.deepCopy(); record(b, true).put("ruleId", 8).put("name", "New");
        assertRename(a, b, true);
        record(b, true).put("ruleId", 7).put("ruleUid", "replacement").put("name", "Old");
        assertReplacement(a, b, true);
    }

    @Test void validMainExtractionMappingsPreserveEveryValueAndOrder() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Map");
        ObjectNode b = a.deepCopy(); ArrayNode fields = extraction(b).putArray("fields");
        fields.addObject().put("target", "title").put("source", "SUBJECT").put("type", "DIRECT").put("required", true);
        fields.addObject().put("target", "status").put("source", "TEXT").put("type", "CONST").put("value", "Ready");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b, true).counts());
        var leaf = field(diff(a, b, true), "extractionRules.fields[0].target");
        assertNull(leaf.oldValue()); assertEquals("title", leaf.newValue());
        ObjectNode c = b.deepCopy(); ArrayNode reordered = extraction(c).putArray("fields");
        reordered.add(fields.get(1)).add(fields.get(0));
        var change = field(diff(b, c, true), "extractionRules.fields[0].target");
        assertEquals("title", change.oldValue()); assertEquals("status", change.newValue());
        var reverse = field(diff(c, b, true), change.field());
        assertEquals(change.newValue(), reverse.oldValue()); assertEquals(change.oldValue(), reverse.newValue());
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(c, a, true).counts());
    }

    @Test void absentNullAndEmptyMappingCollectionsRemainDistinct() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Map"); extraction(a).remove("fields");
        ObjectNode b = a.deepCopy(); extraction(b).putNull("fields");
        ObjectNode c = b.deepCopy(); extraction(c).putArray("fields");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b, true).counts());
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(b, c, true).counts());
    }

    @Test void extractionContextDoesNotRelaxTableFieldValidation() {
        ObjectNode a = snapshot(); ((ArrayNode) a.path("tables")).addObject().put("tableName", "main")
                .putArray("fields").addObject().put("target", "invalid");
        assertThrows(DeveloperBusinessException.class, () -> diff(a, snapshot(), true));
    }

    @Test void duplicateNamesPreserveMultiplicityAndIdsDuringNameSwap() throws Exception {
        for (boolean monitor : new boolean[]{false, true}) {
            ObjectNode a = snapshot(); email(a, monitor, 7, "A"); email(a, monitor, 8, "B");
            ObjectNode b = a.deepCopy(); record(b, monitor).put("name", "B");
            assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b, monitor).counts());
            ((ObjectNode) b.path(collection(monitor)).get(1)).put("name", "A");
            assertEquals(new VersionCompareResponse.Counts(0, 2, 0), diff(a, b, monitor).counts());
            email(b, monitor, 9, "A");
            assertEquals(new VersionCompareResponse.Counts(1, 2, 0), diff(a, b, monitor).counts());
        }
    }

    @Test void duplicateCapturedIdsAreRejectedWithoutEchoingRecordNames() {
        for (boolean monitor : new boolean[]{false, true}) {
            ObjectNode a = snapshot(); email(a, monitor, 7, "Private A"); email(a, monitor, 7, "Private B");
            var error = assertThrows(DeveloperBusinessException.class, () -> diff(a, snapshot(), monitor));
            assertFalse(error.getMessage().contains("Private"));
        }
    }

    @Test void legacyMissingIdsArePartialAndDoNotInventRenameIdentity() throws Exception {
        for (boolean monitor : new boolean[]{false, true}) {
            ObjectNode a = snapshot(); email(a, monitor, 0, "Old");
            ObjectNode b = a.deepCopy(); record(b, monitor).put("name", "New");
            assertReplacement(a, b, monitor); assertEquals("PARTIAL", diff(a, b, monitor).scope());
            record(b, monitor).put("name", "Old").put("enabled", false);
            assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b, monitor).counts());
        }
    }

    @Test void crossFuIdsAreNotBorrowedAndPortableSameDesignStillMatches() throws Exception {
        ObjectNode a = snapshot(); email(a, false, 7, "Old");
        ObjectNode b = a.deepCopy(); record(b, false).put("name", "New");
        var response = engine.compare(version(1, a, 1), version(2, b, 2));
        assertEquals(new VersionCompareResponse.Counts(1, 0, 1), module(response, false).counts());
        record(b, false).put("name", "Old").put("templateId", 9);
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0),
                module(engine.compare(version(1, a, 1), version(2, b, 2)), false).counts());
    }

    @Test void credentialsAndIdentityMetadataNeverEnterPublicValues() throws Exception {
        ObjectNode a = snapshot(); email(a, true, 7, "Old");
        extraction(a).put("password", "FAKE_PRIVATE_SENTINEL");
        ObjectNode b = a.deepCopy(); record(b, true).put("name", "New");
        extraction(b).put("password", "OTHER_FAKE_PRIVATE_SENTINEL");
        String response = mapper.writeValueAsString(engine.compare(version(1, a, 1), version(2, b, 1)));
        assertFalse(response.contains("PRIVATE_SENTINEL")); assertFalse(response.contains("uid-7"));
        assertFalse(response.contains("ruleUid")); assertFalse(response.contains("ruleId"));
        assertEquals(1, diff(a, b, true).items().get(0).fields().size());
    }

    private void assertRename(ObjectNode a, ObjectNode b, boolean monitor) throws Exception {
        var f = diff(a, b, monitor); var r = diff(b, a, monitor);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), f.counts());
        assertEquals("New", f.items().get(0).label());
        assertEquals(f.items().get(0).objectKey(), r.items().get(0).objectKey());
        assertEquals("Old", field(f, "name").oldValue()); assertEquals("New", field(f, "name").newValue());
        assertEquals("New", field(r, "name").oldValue()); assertEquals("Old", field(r, "name").newValue());
    }

    private void assertReplacement(ObjectNode a, ObjectNode b, boolean monitor) throws Exception {
        var f = diff(a, b, monitor); var r = diff(b, a, monitor);
        assertEquals(new VersionCompareResponse.Counts(1, 0, 1), f.counts()); assertEquals(f.counts(), r.counts());
        for (var item : f.items()) {
            var back = r.items().stream().filter(i -> i.objectKey().equals(item.objectKey())).findFirst().orElseThrow();
            assertNotEquals(item.type(), back.type());
        }
    }

    private VersionCompareResponse.FieldChange field(VersionCompareResponse.SemanticDiff d, String name) {
        return d.items().stream().flatMap(i -> i.fields().stream()).filter(f -> f.field().equals(name)).findFirst().orElseThrow();
    }
    private VersionCompareResponse.SemanticDiff diff(ObjectNode a, ObjectNode b, boolean monitor) throws Exception {
        return module(engine.compare(version(1, a, 1), version(2, b, 1)), monitor);
    }
    private VersionCompareResponse.SemanticDiff module(VersionCompareResponse r, boolean monitor) {
        return r.modules().stream().filter(m -> m.key().equals(monitor ? "EMAIL_MONITORS" : "EMAIL_TEMPLATES"))
                .findFirst().orElseThrow().semantic();
    }
    private Version version(long id, ObjectNode snapshot, long fu) {
        return Version.builder().id(id).versionNumber("1.0." + id).functionUnit(FunctionUnit.builder().id(fu).build())
                .snapshotData(snapshot.toString().getBytes(StandardCharsets.UTF_8)).build();
    }
    private ObjectNode snapshot() {
        ObjectNode root = mapper.createObjectNode().put("snapshotSchemaVersion", 2);
        for (String key : new String[]{"tables", "forms", "actions", "decisions", "mainTableViews", "emailTemplates", "emailMonitors"}) root.putArray(key);
        return root;
    }
    private String collection(boolean monitor) { return monitor ? "emailMonitors" : "emailTemplates"; }
    private ObjectNode record(ObjectNode root, boolean monitor) { return (ObjectNode) root.path(collection(monitor)).get(0); }
    private ObjectNode extraction(ObjectNode root) { return (ObjectNode) record(root, true).path("extractionRules"); }
    private void email(ObjectNode root, boolean monitor, long id, String name) {
        ObjectNode item = ((ArrayNode) root.path(collection(monitor))).addObject().put("name", name).put("enabled", true);
        if (id > 0) { item.put(monitor ? "ruleId" : "templateId", id); if (monitor) item.put("ruleUid", "uid-" + id); }
        if (monitor) { ObjectNode rules = item.putObject("extractionRules"); rules.putArray("fields"); rules.putArray("subTables"); }
        else item.put("subject", "Subject").put("bodyHtml", "<p>Body</p>");
    }
}

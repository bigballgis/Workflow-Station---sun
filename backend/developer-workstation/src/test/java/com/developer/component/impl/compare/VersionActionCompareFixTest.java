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

/** Regression contracts for #1671, #1674, #1677, #1678 and #1679. */
class VersionActionCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test
    void globalBindingsAreReadableInBothDesignerNamespacesAndDirections() throws Exception {
        for (String ns : new String[]{"http://workflow.platform/schema/custom", "http://custom.bpmn.io/schema"}) {
            for (String tag : new String[]{"property", "values"}) {
                ObjectNode before = snapshot();
                before.put("process", "<definitions xmlns:c='" + ns + "'><process id='P'/></definitions>");
                ObjectNode after = before.deepCopy();
                after.put("process", "<definitions xmlns:c='" + ns + "'><process id='P'><extensionElements>"
                        + "<c:properties><c:" + tag + " name='globalActionIds' value='[7]'/><c:" + tag
                        + " name='globalActionNames' value='[&quot;Approve&quot;]'/></c:properties>"
                        + "</extensionElements></process></definitions>");
                var forward = module(compare(before, after), "PROCESS").semantic().items().get(0);
                assertEquals("[7]", field(forward, "config.globalActionIds").newValue());
                assertEquals("[\"Approve\"]", field(forward, "config.globalActionNames").newValue());
                assertFalse(forward.fields().stream().anyMatch(f -> f.field().endsWith("Fingerprint")));
                var reverse = module(compare(after, before), "PROCESS").semantic().items().get(0);
                assertEquals("[7]", field(reverse, "config.globalActionIds").oldValue());
            }
        }
    }

    @Test
    void sameIdRenameIsModifiedWithOldAndNewNames() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "Old", "APPROVE");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0)).put("actionName", "New");
        var diff = module(compare(before, after), "ACTIONS").semantic();
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff.counts());
        assertEquals("New", diff.items().get(0).label());
        assertEquals("Old", field(diff.items().get(0), "actionName").oldValue());
        assertEquals("New", field(diff.items().get(0), "actionName").newValue());
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), module(compare(after, before), "ACTIONS").semantic().counts());
    }

    @Test
    void sameIdWinsOverNameWhenTwoActionsSwapNames() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "A", "APPROVE");
        action(before, 8, "B", "APPROVE");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0)).put("actionName", "B");
        ((ObjectNode) after.path("actions").get(1)).put("actionName", "A");
        assertEquals(new VersionCompareResponse.Counts(0, 2, 0), module(compare(before, after), "ACTIONS").semantic().counts());
    }

    @Test
    void regeneratedIdsMatchNamesAndDoNotChangeCompositeReferences() throws Exception {
        ObjectNode before = composite(7, 8);
        ObjectNode after = composite(70, 80);
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), module(compare(before, after), "ACTIONS").semantic().counts());
    }

    @Test
    void sameNumericIdAcrossDifferentFunctionUnitsDoesNotMeanRename() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "A", "APPROVE");
        ObjectNode after = snapshot();
        action(after, 7, "B", "APPROVE");
        var result = engine.compare(version(1, before, 1), version(2, after, 2));
        assertEquals(new VersionCompareResponse.Counts(1, 0, 1), module(result, "ACTIONS").semantic().counts());
    }

    @Test
    void renameAndNewActionUsingOldNameRemainSeparateObjects() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "A", "APPROVE");
        ObjectNode after = snapshot();
        action(after, 7, "B", "APPROVE");
        action(after, 8, "A", "APPROVE");
        assertEquals(new VersionCompareResponse.Counts(1, 1, 0), module(compare(before, after), "ACTIONS").semantic().counts());
    }

    @Test
    void compositeReorderShowsNamesFromEachHistoricalSnapshot() throws Exception {
        ObjectNode before = composite(7, 8);
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(2).path("configJson")).set("subActions", mapper.readTree("[8,7]"));
        var item = item(compare(before, after), "Combo");
        assertEquals("Approve", field(item, "configJson.subActions[0]").oldValue());
        assertEquals("Reject", field(item, "configJson.subActions[0]").newValue());
        assertEquals("Approve", field(item, "configJson.subActions[1]").newValue());
    }

    @Test
    void missingCompositeChildHasExplicitUnresolvedReference() throws Exception {
        ObjectNode before = composite(7, 8);
        ObjectNode after = before.deepCopy();
        ((ArrayNode) after.path("actions")).remove(1);
        assertEquals("unresolved:8", field(item(compare(before, after), "Combo"), "configJson.subActions[1]").newValue());
    }

    @Test
    void duplicateSavedIdsAreRejectedRatherThanArbitrarilyMatched() throws Exception {
        ObjectNode snapshot = snapshot();
        action(snapshot, 7, "A", "APPROVE");
        action(snapshot, 7, "B", "APPROVE");
        assertThrows(DeveloperBusinessException.class, () -> compare(snapshot, snapshot));
    }

    @Test
    void jsonTextHeadersAndBodyRedactSecretsInBothResponseRepresentations() throws Exception {
        ObjectNode before = snapshot();
        ObjectNode cfg = action(before, 7, "API", "API_CALL").putObject("configJson");
        cfg.put("headers", "{\"X-Case\":\"alpha\"}");
        cfg.put("body", "{\"phase\":\"alpha\"}");
        ObjectNode after = before.deepCopy();
        ObjectNode next = (ObjectNode) after.path("actions").get(0).path("configJson");
        String canary = "QA_SYNTHETIC_NOT_A_REAL_SECRET";
        next.put("headers", "{\"Authorization\":\"Bearer " + canary + "\",\"Cookie\":\"" + canary
                + "\",\"X-API-Key\":\"" + canary + "\",\"X-Case\":\"beta\"}");
        next.put("body", "{\"phase\":\"beta\",\"nested\":{\"password\":\"" + canary + "\"}}");
        String response = mapper.writeValueAsString(compare(before, after));
        assertFalse(response.contains(canary));
        assertTrue(response.contains("[REDACTED]"));
        assertTrue(response.contains("beta"));
        assertFalse(mapper.writeValueAsString(compare(after, before)).contains(canary));
    }

    @Test
    void unstructuredPayloadChangesAreDetectedButNeverPreviewed() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "API", "API_CALL").putObject("configJson").put("body", "password=QA_RAW_BEFORE");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0).path("configJson")).put("body", "password=QA_RAW_AFTER");
        var result = compare(before, after);
        assertEquals(1, module(result, "ACTIONS").semantic().counts().modified());
        assertEquals("[hidden configuration]", field(item(result, "API"), "configJson.body").oldValue());
        assertFalse(mapper.writeValueAsString(result).contains("QA_RAW_"));
    }

    @Test
    void trailingJsonTextIsOpaqueInsteadOfPartiallyParsed() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "API", "API_CALL").putObject("configJson").put("body", "");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0).path("configJson")).put("body", "{\"phase\":\"ok\"} QA_RAW_SECRET");
        var result = compare(before, after);
        assertEquals("[hidden configuration]", field(item(result, "API"), "configJson.body").newValue());
        assertFalse(mapper.writeValueAsString(result).contains("QA_RAW_SECRET"));
    }

    @Test
    void jsonEncodedInsidePayloadStringsIsAlsoScrubbed() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "API", "API_CALL").putObject("configJson").put("body", "");
        ObjectNode after = before.deepCopy();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("nested", "{\"password\":\"QA_DOUBLE_ENCODED_SECRET\"}");
        ((ObjectNode) after.path("actions").get(0).path("configJson")).put("body", mapper.writeValueAsString(payload));
        assertFalse(mapper.writeValueAsString(compare(before, after)).contains("QA_DOUBLE_ENCODED_SECRET"));
    }

    @Test
    void expandedConditionArraysKeepThirtyFieldLimitAndTruncationNotice() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "Approve", "APPROVE").putObject("configJson");
        ObjectNode after = before.deepCopy();
        ArrayNode conditions = ((ObjectNode) after.path("actions").get(0).path("configJson")).putArray("visibilityCondition");
        for (int i = 0; i < 40; i++) conditions.addObject().put("field", "qa" + i);
        var semantic = module(compare(before, after), "ACTIONS").semantic();
        assertEquals(30, semantic.items().get(0).fields().size());
        assertTrue(semantic.truncated());
    }

    @Test
    void addedAndClearedConditionArraysShowEveryLeafAndReverseDirection() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "Approve", "APPROVE").putObject("configJson");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0).path("configJson")).set("visibilityCondition", mapper.readTree(
                "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":\"100\",\"logic\":\"AND\"}]"));
        var added = item(compare(before, after), "Approve");
        assertEquals("amount", field(added, "configJson.visibilityCondition[0].field").newValue());
        assertEquals("100", field(added, "configJson.visibilityCondition[0].value").newValue());
        var removed = item(compare(after, before), "Approve");
        assertEquals("100", field(removed, "configJson.visibilityCondition[0].value").oldValue());
        assertNull(field(removed, "configJson.visibilityCondition[0].value").newValue());
    }

    @Test
    void absentAndEmptyArrayRemainDistinguishable() throws Exception {
        ObjectNode before = snapshot();
        action(before, 7, "Approve", "APPROVE").putObject("configJson");
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("actions").get(0).path("configJson")).putArray("visibilityCondition");
        assertEquals("[0 items]", field(item(compare(before, after), "Approve"), "configJson.visibilityCondition").newValue());
    }

    @Test
    void legacyCompositeSnapshotUsesNamesWithoutGeneratedIds() throws Exception {
        ObjectNode before = composite(7, 8);
        ObjectNode after = composite(70, 80);
        for (ObjectNode node : new ObjectNode[]{before, after}) {
            node.remove("snapshotSchemaVersion");
            node.set("tableDefinitions", node.remove("tables"));
            node.set("formDefinitions", node.remove("forms"));
            node.set("actionDefinitions", node.remove("actions"));
            node.set("decisionDefinitions", node.remove("decisions"));
        }
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), module(compare(before, after), "ACTIONS").semantic().counts());
    }

    private ObjectNode snapshot() {
        ObjectNode node = mapper.createObjectNode();
        node.put("snapshotSchemaVersion", 2);
        for (String key : new String[]{"tables", "forms", "actions", "decisions"}) node.putArray(key);
        return node;
    }

    private ObjectNode action(ObjectNode snapshot, long id, String name, String type) {
        ObjectNode action = ((ArrayNode) snapshot.path("actions")).addObject();
        action.put("actionId", id).put("actionName", name).put("actionType", type);
        return action;
    }

    private ObjectNode composite(long first, long second) {
        ObjectNode node = snapshot();
        action(node, first, "Approve", "APPROVE");
        action(node, second, "Reject", "REJECT");
        action(node, 9, "Combo", "COMPOSITE").putObject("configJson").putArray("subActions").add(first).add(second);
        return node;
    }

    private Version version(long id, ObjectNode snapshot, long fuId) throws Exception {
        return Version.builder().id(id).functionUnit(FunctionUnit.builder().id(fuId).build())
                .versionNumber("1.0." + id).snapshotData(mapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8)).build();
    }

    private VersionCompareResponse compare(ObjectNode before, ObjectNode after) throws Exception {
        return engine.compare(version(1, before, 1), version(2, after, 1));
    }

    private VersionCompareResponse.ModuleDiff module(VersionCompareResponse result, String key) {
        return result.modules().stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
    }

    private VersionCompareResponse.SemanticChange item(VersionCompareResponse result, String name) {
        return module(result, "ACTIONS").semantic().items().stream().filter(i -> i.label().equals(name)).findFirst().orElseThrow();
    }

    private VersionCompareResponse.FieldChange field(VersionCompareResponse.SemanticChange item, String name) {
        return item.fields().stream().filter(f -> f.field().equals(name)).findFirst().orElseThrow();
    }
}

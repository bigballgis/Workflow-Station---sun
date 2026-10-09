package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test
    void comparesPortableConfigurationWithoutDatabaseIdOrListOrderNoise() throws Exception {
        String before = """
                {"snapshotSchemaVersion":2,"name":"FU","tables":[
                  {"tableId":1,"tableName":"case","fields":[
                    {"fieldName":"id","nullable":false},{"fieldName":"status","nullable":true}]}
                ],"forms":[{"formId":4,"formName":"caseForm","tableBindings":[
                  {"bindingId":11,"tableName":"child","bindingType":"SUB","filterFkFieldName":"parent_id",
                   "fkFillSources":[{"fieldName":"parent_id","kind":"PARENT"}]}]}],
                "mainTableViews":[{"viewName":"Cases","mainTableName":"case","fields":[
                  {"fieldName":"status","selectDisplay":"value"}]}],
                "actions":[],"connections":[{"connectionId":7,"name":"Mail","passwordEnvKey":"MAIL_OLD",
                  "oauthAccessTokenEncrypted":"old-secret"}],"emailTemplates":[],"emailMonitors":[],
                "decisions":[],"documents":{"DESIGN":"first"}}
                """;
        String after = """
                {"snapshotSchemaVersion":2,"name":"FU","tables":[
                  {"tableId":99,"tableName":"case","fields":[
                    {"fieldName":"status","nullable":false},{"fieldName":"id","nullable":false}]}
                ],"forms":[{"formId":40,"formName":"caseForm","tableBindings":[
                  {"bindingId":111,"tableName":"child","bindingType":"SUB","filterFkFieldName":"parent_id",
                   "fkFillSources":[{"fieldName":"parent_id","kind":"PRIMARY"}]}]}],
                "mainTableViews":[{"viewName":"Cases","mainTableName":"case","fields":[
                  {"fieldName":"status","selectDisplay":"label"}]}],
                "actions":[],"connections":[{"connectionId":70,"name":"Mail","passwordEnvKey":"MAIL_NEW",
                  "oauthAccessTokenEncrypted":"new-secret"}],"emailTemplates":[],"emailMonitors":[],
                "decisions":[],"documents":{"DESIGN":"second"}}
                """;
        VersionCompareResponse result = engine.compare(version(1, before), version(2, after));

        assertEquals(0, result.totals().added());
        assertEquals(0, result.totals().removed());
        assertTrue(result.totals().modified() >= 4);
        assertTrue(module(result, "TABLES").changes().stream()
                .anyMatch(change -> change.path().contains("status/nullable")));
        assertTrue(module(result, "VIEWS").changes().stream()
                .anyMatch(change -> change.path().contains("selectDisplay")));
        assertTrue(module(result, "CONNECTIONS").changes().stream()
                .anyMatch(change -> change.path().contains("passwordEnvKey")));
        assertFalse(mapper.writeValueAsString(result).contains("old-secret"));
        assertFalse(mapper.writeValueAsString(result).contains("new-secret"));
    }

    @Test
    void missingDocumentsAndAutomationBodyAreNotReportedAsDeletions() {
        String before = """
                {"snapshotSchemaVersion":2,"name":"FU","tables":[],"forms":[],"actions":[],"decisions":[]}
                """;
        String after = """
                {"snapshotSchemaVersion":2,"name":"FU","tables":[],"forms":[],"actions":[],
                 "decisions":[],"documents":{"REQUIREMENTS":"hello"}}
                """;
        VersionCompareResponse result = engine.compare(version(1, before), version(2, after));

        assertEquals("NOT_CAPTURED", module(result, "DOCUMENTS").status());
        assertEquals("NOT_SNAPSHOTTED", module(result, "AUTOMATION").status());
        assertEquals(0, result.totals().removed());
    }

    @Test
    void invalidXmlFailsWithoutParsingExternalEntities() {
        String snapshot = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],"decisions":[],
                 "process":"<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><foo>&xxe;</foo>"}
                """;
        assertThrows(DeveloperBusinessException.class,
                () -> engine.compare(version(1, snapshot), version(2, snapshot)));
    }

    @Test
    void equivalentXmlAttributeOrderDoesNotCreateChange() {
        String base = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],"decisions":[],
                 "process":"<definitions><task id='one' name='A'/></definitions>"}
                """;
        String target = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],"decisions":[],
                 "process":"<definitions><task name='A' id='one'/></definitions>"}
                """;
        VersionCompareResponse result = engine.compare(version(1, base), version(2, target));
        assertEquals(0, module(result, "PROCESS").counts().modified());
    }

    @Test
    void malformedHistoricalDmnFallsBackToSafeTextComparison() throws Exception {
        String base = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],
                 "decisions":["<definitions><decision id='one'></definitions>"]}
                """;
        String target = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],
                 "decisions":["<definitions><decision id='one' name='updated'></definitions>"]}
                """;
        VersionCompareResponse result = engine.compare(version(1, base), version(2, target));
        assertEquals(1, module(result, "DECISIONS").counts().modified());
        assertFalse(mapper.writeValueAsString(result).contains("<definitions>"));
    }

    @Test
    void automationReferenceIsComparedInsideProcessOnly() {
        String before = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],"decisions":[],
                 "process":"<definitions xmlns:ap='urn:activepieces'><task ap:flowKey='flow-A'/></definitions>"}
                """;
        String after = before.replace("flow-A", "flow-B");
        VersionCompareResponse result = engine.compare(version(1, before), version(2, after));
        assertEquals(1, module(result, "PROCESS").counts().modified());
        assertEquals("NOT_SNAPSHOTTED", module(result, "AUTOMATION").status());
    }

    @Test
    void remappedFormBindingAndEmailReferencesDoNotCreateFalseChanges() {
        String before = """
                {"snapshotSchemaVersion":2,"tables":[],"actions":[],"decisions":[],
                 "forms":[{"formId":1,"formName":"TaskForm","configJson":"{\\"bindingId\\":10}",
                   "tableBindings":[{"bindingId":10,"tableName":"child","bindingType":"SUB"}]}],
                 "connections":[{"connectionId":2,"connectionUid":"u1","name":"Mailbox","connectionType":"SMTP"}],
                 "emailTemplates":[],"emailMonitors":[
                   {"ruleId":5,"name":"template"},
                   {"ruleId":6,"name":"bound","startEventId":"start","sourceRuleId":5,
                    "targetFormId":1,"targetBindingId":10,"connectionUid":"u1"}]}
                """;
        String after = """
                {"snapshotSchemaVersion":2,"tables":[],"actions":[],"decisions":[],
                 "forms":[{"formId":9,"formName":"TaskForm","configJson":"{\\"bindingId\\":99}",
                   "tableBindings":[{"bindingId":99,"tableName":"child","bindingType":"SUB"}]}],
                 "connections":[{"connectionId":20,"connectionUid":"u2","name":"Mailbox","connectionType":"SMTP"}],
                 "emailTemplates":[],"emailMonitors":[
                   {"ruleId":50,"name":"template"},
                   {"ruleId":60,"name":"bound","startEventId":"start","sourceRuleId":50,
                    "targetFormId":9,"targetBindingId":99,"connectionUid":"u2"}]}
                """;
        VersionCompareResponse result = engine.compare(version(1, before), version(2, after));
        assertEquals(0, result.totals().added() + result.totals().modified() + result.totals().removed());
    }

    @Test
    void incompleteModernSnapshotIsRejectedRatherThanReportedUnchanged() {
        String incomplete = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[]}
                """;
        assertThrows(DeveloperBusinessException.class,
                () -> engine.compare(version(1, incomplete), version(2, incomplete)));
    }

    @Test
    void blankConfigJsonIsTreatedAsEmptyOptionalConfiguration() {
        String snapshot = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[{"formName":"Form","configJson":" "}],
                 "actions":[],"decisions":[]}
                """;
        VersionCompareResponse result = engine.compare(version(1, snapshot), version(2, snapshot));
        assertEquals(0, result.totals().added() + result.totals().modified() + result.totals().removed());
    }

    @Test
    void relatedBindingWithoutTableNameUsesRelationNameOrPosition() {
        String snapshot = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[{"formName":"Form","tableBindings":[
                  {"bindingId":1,"bindingType":"RELATED","tableName":null,"relationTableId":10,"sortOrder":2},
                  {"bindingId":2,"bindingType":"RELATED","tableName":null,"relationTableId":-1,"sortOrder":3}
                ]}],"relationTables":[{"relationTableId":10,"tableName":"people"}],
                 "actions":[],"decisions":[]}
                """;
        VersionCompareResponse result = engine.compare(version(1, snapshot), version(2, snapshot));
        assertEquals(0, result.totals().added() + result.totals().modified() + result.totals().removed());
    }

    @Test
    void absentAndExplicitEmptyFieldDefaultsAreEquivalent() {
        String before = """
                {"snapshotSchemaVersion":2,"tables":[{"tableName":"main","fields":[{"fieldName":"state"}]}],
                 "forms":[],"actions":[],"decisions":[]}
                """;
        String after = """
                {"snapshotSchemaVersion":2,"tables":[{"tableName":"main","fields":[
                  {"fieldName":"state","isComputed":false,"computedField":null,"lookupConfig":null}]}],
                 "forms":[],"actions":[],"decisions":[]}
                """;
        VersionCompareResponse result = engine.compare(version(1, before), version(2, after));
        assertEquals(0, module(result, "TABLES").counts().added()
                + module(result, "TABLES").counts().modified()
                + module(result, "TABLES").counts().removed());
    }

    @Test
    void capsVisibleChangesAtThreeHundredWithoutLosingTotalCount() {
        String base = """
                {"snapshotSchemaVersion":2,"tables":[],"forms":[],"actions":[],"decisions":[]}
                """;
        StringBuilder target = new StringBuilder(
                "{\"snapshotSchemaVersion\":2,\"tables\":[],\"forms\":[],\"actions\":[");
        for (int i = 0; i < 301; i++) {
            if (i > 0) target.append(',');
            target.append("{\"actionName\":\"qaAction").append(i).append("\"}");
        }
        target.append("],\"decisions\":[]}");

        VersionCompareResponse.ModuleDiff actions = module(
                engine.compare(version(1, base), version(2, target.toString())), "ACTIONS");
        assertEquals(301, actions.counts().added());
        assertEquals(300, actions.changes().size());
        assertTrue(actions.truncated());
    }

    private VersionCompareResponse.ModuleDiff module(VersionCompareResponse response, String key) {
        return response.modules().stream().filter(module -> module.key().equals(key)).findFirst().orElseThrow();
    }

    private Version version(long id, String snapshot) {
        return Version.builder().id(id).versionNumber("1.0." + id)
                .snapshotData(snapshot.getBytes(StandardCharsets.UTF_8)).build();
    }
}

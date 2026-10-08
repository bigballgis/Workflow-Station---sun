package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSemanticCompareEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test
    void connectionRenameUsesSavedUidWithoutReturningUidOrCredential() throws Exception {
        String before = snapshot("""
                "connections":[{"connectionUid":"stable-uid","name":"Old mailbox",
                  "connectionType":"IMAP","credentialEncrypted":"SECRET_BEFORE"}]
                """);
        String after = snapshot("""
                "connections":[{"connectionUid":"stable-uid","name":"Finance mailbox",
                  "connectionType":"IMAP","credentialEncrypted":"SECRET_AFTER"}]
                """);
        VersionCompareResponse response = compare(before, after);
        var semantic = module(response, "CONNECTIONS").semantic();

        assertEquals(1, semantic.counts().modified());
        assertEquals(0, semantic.counts().added() + semantic.counts().removed());
        assertEquals("name", semantic.items().get(0).fields().get(0).field());
        assertEquals("Old mailbox", semantic.items().get(0).fields().get(0).oldValue());
        assertEquals("Finance mailbox", semantic.items().get(0).fields().get(0).newValue());
        String json = mapper.writeValueAsString(response);
        assertFalse(json.contains("stable-uid"));
        assertFalse(json.contains("SECRET_BEFORE"));
        assertFalse(json.contains("SECRET_AFTER"));
    }

    @Test
    void recreatedConnectionWithSameNameButDifferentUidsIsAddedAndRemoved() {
        String before = snapshot("\"connections\":[{\"connectionUid\":\"old-uid\","
                + "\"name\":\"Inbox\",\"connectionType\":\"IMAP\"}]");
        String after = snapshot("\"connections\":[{\"connectionUid\":\"new-uid\","
                + "\"name\":\"Inbox\",\"connectionType\":\"IMAP\"}]");
        var semantic = module(compare(before, after), "CONNECTIONS").semantic();

        assertEquals(1, semantic.counts().added());
        assertEquals(0, semantic.counts().modified());
        assertEquals(1, semantic.counts().removed());
    }

    @Test
    void regeneratedMonitorUidAfterRollbackIsNotAChange() {
        String before = snapshot("""
                "emailMonitors":[{"ruleUid":"uid-before","name":"Expense inbox",
                  "startEventId":"startMail","folderLabel":"INBOX"}]
                """);
        String after = before.replace("uid-before", "uid-after");
        var semantic = module(compare(before, after), "EMAIL_MONITORS").semantic();

        assertEquals(0, count(semantic.counts()));
    }

    @Test
    void monitorBusinessRuleChangeIsModifiedDespiteRegeneratedUid() {
        String before = snapshot("""
                "emailMonitors":[{"ruleUid":"uid-before","name":"Expense inbox",
                  "startEventId":"startMail","folderLabel":"INBOX","filterSubject":"Expense"}]
                """);
        String after = before.replace("uid-before", "uid-after")
                .replace("\"folderLabel\":\"INBOX\"", "\"folderLabel\":\"REVIEW\"");
        var semantic = module(compare(before, after), "EMAIL_MONITORS").semantic();

        assertEquals(1, semantic.counts().modified());
        assertEquals("folderLabel", semantic.items().get(0).fields().get(0).field());
    }

    @Test
    void tableFieldFormControlAndViewAccessAreSeparateDesignObjects() {
        String before = snapshot("""
                "tables":[{"tableName":"expense","fields":[
                  {"fieldName":"amount","dataType":"VARCHAR"}]}],
                "forms":[{"formName":"Expense form","configJson":{"rule":[
                  {"field":"amount","title":"Amount","type":"input","props":{"readonly":false}}]}}],
                "mainTableViews":[{"mainTableName":"expense","viewName":"All","accessRules":[
                  {"targetType":"BUSINESS_UNIT","targetCode":"bu-a"},
                  {"targetType":"ROLE","targetCode":"role-a"}]}]
                """);
        String after = before.replace("VARCHAR", "DECIMAL")
                .replace("\"readonly\":false", "\"readonly\":true")
                .replace("role-a", "role-b");
        VersionCompareResponse response = compare(before, after);

        assertTrue(module(response, "TABLES").semantic().items().stream()
                .anyMatch(item -> item.objectType().equals("FIELD") && item.fields().stream()
                        .anyMatch(field -> field.field().equals("dataType"))));
        assertTrue(module(response, "FORMS").semantic().items().stream()
                .anyMatch(item -> item.objectType().equals("FORM_CONTROL") && item.fields().stream()
                        .anyMatch(field -> field.field().equals("props.readonly"))));
        assertEquals(1, module(response, "VIEWS").semantic().counts().added());
        assertEquals(1, module(response, "VIEWS").semantic().counts().removed());
    }

    @Test
    void publishingAViewWithoutChangingItsDesignIsNotAModification() {
        String before = snapshot("\"mainTableViews\":[{\"mainTableName\":\"expense\","
                + "\"viewName\":\"All\",\"status\":\"DRAFT\",\"fields\":[]}]");
        String after = before.replace("DRAFT", "PUBLISHED");

        var semantic = module(compare(before, after), "VIEWS").semantic();

        assertEquals(0, count(semantic.counts()));
    }

    @Test
    void repeatedFormFieldControlsRemainComparable() {
        String before = snapshot("\"forms\":[{\"formName\":\"Review\",\"configJson\":{\"rule\":["
                + "{\"field\":\"note\",\"title\":\"First\"},"
                + "{\"field\":\"note\",\"title\":\"Second\"}]}}]");
        String after = before.replace("Second", "Updated");
        var semantic = module(compare(before, after), "FORMS").semantic();

        assertEquals(1, semantic.counts().modified());
        assertEquals("FORM_CONTROL", semantic.items().get(0).objectType());
    }

    @Test
    void subFormRuleChangeShowsItsControlValuesInsteadOfIdenticalArraySizes() {
        String before = subFormSnapshot(41, false);
        String after = subFormSnapshot(41, true);
        var semantic = module(compare(before, after), "FORMS").semantic();

        assertEquals(1, semantic.counts().modified());
        var control = semantic.items().get(0);
        assertEquals("FORM_CONTROL", control.objectType());
        assertEquals("Note", control.label());
        assertEquals("props.readonly", control.fields().get(0).field());
        assertEquals("false", control.fields().get(0).oldValue());
        assertEquals("true", control.fields().get(0).newValue());
        assertFalse(control.fields().stream().anyMatch(field -> field.field().contains("subForms")));
    }

    @Test
    void regeneratedSubFormBindingIdDoesNotCreateFalseChanges() {
        var semantic = module(compare(subFormSnapshot(41, false),
                subFormSnapshot(42, false)), "FORMS").semantic();

        assertEquals(0, count(semantic.counts()));
    }

    @Test
    void relatedSubFormsWithDifferentSortOrdersKeepDistinctStableIdentities() {
        String before = snapshot("\"forms\":[{\"formName\":\"Case\",\"tableBindings\":["
                + "{\"bindingId\":41,\"bindingType\":\"RELATED\",\"sortOrder\":1},"
                + "{\"bindingId\":42,\"bindingType\":\"RELATED\",\"sortOrder\":2}],"
                + "\"configJson\":{\"rule\":[],\"subForms\":{"
                + "\"41\":{\"rule\":[{\"field\":\"first\",\"title\":\"First\"}]},"
                + "\"42\":{\"rule\":[{\"field\":\"second\",\"title\":\"Second\"}]}}}}]");
        String after = before.replace("\"bindingId\":41", "\"bindingId\":51")
                .replace("\"bindingId\":42", "\"bindingId\":52")
                .replace("\"41\":{", "\"51\":{")
                .replace("\"42\":{", "\"52\":{");
        var semantic = module(compare(before, after), "FORMS").semantic();

        assertEquals(0, count(semantic.counts()));
    }

    @Test
    void nestedSubFormControlArrayShowsChangedFieldInsteadOfIdenticalSizes() {
        String before = subFormSnapshot(41, false).replace("\"readonly\":false",
                "\"lookupConfig\":{\"filterConditions\":[{\"field\":\"status\","
                        + "\"value\":\"OLD\"},{\"field\":\"kind\",\"value\":\"KEEP\"}]}");
        String after = before.replace("\"value\":\"OLD\"", "\"value\":\"NEW\"");
        var semantic = module(compare(before, after), "FORMS").semantic();

        assertEquals(1, semantic.counts().modified());
        var field = semantic.items().get(0).fields().get(0);
        assertEquals("props.lookupConfig.filterConditions[0].value", field.field());
        assertEquals("OLD", field.oldValue());
        assertEquals("NEW", field.newValue());
    }

    @Test
    void nestedArrayPropertyNamedAsSecretDoesNotExposeItsValue() throws Exception {
        String before = subFormSnapshot(41, false).replace("\"readonly\":false",
                "\"properties\":[{\"name\":\"ap:password\",\"value\":\"SECRET_BEFORE\"}]");
        String after = before.replace("SECRET_BEFORE", "SECRET_AFTER");
        String json = mapper.writeValueAsString(compare(before, after));

        assertFalse(json.contains("SECRET_BEFORE"));
        assertFalse(json.contains("SECRET_AFTER"));
    }

    @Test
    void processAndAutomationReferenceAreSeparatedWithoutDoubleCounting() {
        String process = """
                <definitions xmlns:ap='urn:activepieces'><process id='process-a'>
                  <serviceTask id='send' name='Send' ap:flowKey='flow-a'/>
                  <task id='review' name='Review'/>
                  <sequenceFlow id='route' sourceRef='send' targetRef='review'/>
                </process></definitions>
                """.replace("\n", "");
        String before = snapshot("\"process\":" + quote(process));
        String after = snapshot("\"process\":" + quote(process.replace("Review", "Approve")
                .replace("flow-a", "flow-b")));
        VersionCompareResponse response = compare(before, after);

        var processDiff = module(response, "PROCESS").semantic();
        var automationDiff = module(response, "AUTOMATION").semantic();
        assertEquals(1, processDiff.counts().modified());
        assertEquals(1, automationDiff.counts().modified());
        assertEquals("REFERENCES_ONLY", automationDiff.scope());
        assertEquals("NOT_SNAPSHOTTED", module(response, "AUTOMATION").status());
        assertEquals(2, response.displayTotals().modified());
        assertFalse(processDiff.items().stream().flatMap(item -> item.fields().stream())
                .anyMatch(field -> field.field().contains("flowKey")));
    }

    @Test
    void flowableExtensionPropertyChangesOnlyAutomationReference() throws Exception {
        String process = """
                <definitions xmlns:flowable='http://flowable.org/bpmn'><process id='process-a'>
                  <serviceTask id='send' name='Send'><extensionElements><flowable:properties>
                    <flowable:property name='ap:flowKey' value='flow-a'/>
                    <flowable:property name='ap:password' value='SECRET_SENTINEL'/>
                  </flowable:properties></extensionElements></serviceTask>
                </process></definitions>
                """.replace("\n", "");
        VersionCompareResponse response = compare(snapshot("\"process\":" + quote(process)),
                snapshot("\"process\":" + quote(process.replace("flow-a", "flow-b"))));

        var processDiff = module(response, "PROCESS").semantic();
        var automationDiff = module(response, "AUTOMATION").semantic();
        assertEquals(0, count(processDiff.counts()));
        assertEquals(1, automationDiff.counts().modified());
        assertEquals("AUTOMATION_REFERENCE", automationDiff.items().get(0).objectType());
        assertEquals("flowKey", automationDiff.items().get(0).fields().get(0).field());
        assertEquals("flow-a", automationDiff.items().get(0).fields().get(0).oldValue());
        assertEquals("flow-b", automationDiff.items().get(0).fields().get(0).newValue());
        assertFalse(mapper.writeValueAsString(response).contains("SECRET_SENTINEL"));
    }

    @Test
    void legacyNestedFlowIdIsAnAutomationReference() {
        String process = """
                <definitions xmlns:c='urn:custom'><serviceTask id='send'>
                  <extensionElements><c:values name='ap:flowId' value='legacy-a'/></extensionElements>
                </serviceTask></definitions>
                """.replace("\n", "");
        VersionCompareResponse response = compare(snapshot("\"process\":" + quote(process)),
                snapshot("\"process\":" + quote(process.replace("legacy-a", "legacy-b"))));

        assertEquals(0, count(module(response, "PROCESS").semantic().counts()));
        assertEquals(1, module(response, "AUTOMATION").semantic().counts().modified());
        assertEquals("flowId", module(response, "AUTOMATION").semantic()
                .items().get(0).fields().get(0).field());
    }

    @Test
    void changingBpmnElementKindWithSameIdIsAProcessChange() {
        String task = "<definitions><task id='review' name='Review'/></definitions>";
        VersionCompareResponse response = compare(snapshot("\"process\":" + quote(task)),
                snapshot("\"process\":" + quote(task.replace("task", "userTask"))));

        var process = module(response, "PROCESS").semantic();
        assertEquals(1, process.counts().modified());
        assertTrue(process.items().get(0).fields().stream()
                .anyMatch(field -> field.field().equals("elementType")));
    }

    @Test
    void decisionRuleAndHitPolicyHaveIndependentSemanticChanges() {
        String dmn = """
                <definitions><decision id='decision-a' name='Approve'>
                <decisionTable id='table-a' hitPolicy='FIRST'>
                <rule id='rule-a'><inputEntry id='input-a'><text>1</text></inputEntry></rule>
                </decisionTable></decision></definitions>
                """.replace("\n", "");
        String before = snapshot("\"decisions\":[" + quote(dmn) + "]");
        String after = snapshot("\"decisions\":[" + quote(dmn.replace("FIRST", "UNIQUE")
                .replace("<text>1</text>", "<text>2</text>")) + "]");
        var semantic = module(compare(before, after), "DECISIONS").semantic();

        assertEquals(2, semantic.counts().modified());
        assertTrue(semantic.items().stream().anyMatch(item -> item.objectType().equals("DMN_TABLE")));
        assertTrue(semantic.items().stream().anyMatch(item -> item.objectType().equals("DMN_INPUT")));
    }

    @Test
    void nestedBpmnExtensionChangesAreDetectedWithoutReturningSecretBody() throws Exception {
        String xml = """
                <definitions xmlns:ap='urn:activepieces'><serviceTask id='send'>
                  <extensionElements><ap:setting><ap:password>SECRET_BEFORE</ap:password></ap:setting></extensionElements>
                </serviceTask></definitions>
                """.replace("\n", "");
        String before = snapshot("\"process\":" + quote(xml));
        String after = snapshot("\"process\":" + quote(xml.replace("SECRET_BEFORE", "SECRET_AFTER")));
        VersionCompareResponse result = compare(before, after);

        assertEquals(1, module(result, "PROCESS").semantic().counts().modified());
        var field = module(result, "PROCESS").semantic().items().get(0).fields().get(0);
        assertEquals("[hidden configuration]", field.oldValue());
        assertEquals("[hidden configuration]", field.newValue());
        String json = mapper.writeValueAsString(result);
        assertFalse(json.contains("SECRET_BEFORE"));
        assertFalse(json.contains("SECRET_AFTER"));
    }

    @Test
    void processExtensionBusinessFieldsShowBeforeAndAfterWithoutLeakingOpaqueSettings() throws Exception {
        String xml = """
                <definitions xmlns:custom='http://workflow.platform/schema/custom'
                  xmlns:legacy='http://custom.bpmn.io/schema' xmlns:ap='urn:activepieces'>
                  <userTask id='review' name='Review'><extensionElements>
                    <legacy:properties><legacy:values name='formId' value='100'/></legacy:properties>
                    <custom:properties>
                      <custom:property name='assigneeType' value='INITIATOR'/>
                      <custom:property name='formId' value='101'/>
                      <custom:property name='formName' value='Original form'/>
                      <custom:property name='actionIds' value='[10]'/>
                      <custom:property name='timeoutDuration' value='PT1H'/>
                      <custom:property name='ap:password' value='SECRET_SENTINEL'/>
                    </custom:properties>
                  </extensionElements></userTask>
                </definitions>
                """.replace("\n", "");
        String changed = xml.replace("INITIATOR", "MANUAL_ASSIGN")
                .replace("value='101'", "value='102'")
                .replace("Original form", "Updated form")
                .replace("value='[10]'", "value='[11]'")
                .replace("PT1H", "PT2H");
        VersionCompareResponse result = compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(changed)));
        var process = module(result, "PROCESS").semantic();

        assertEquals(1, process.counts().modified());
        var fields = process.items().get(0).fields();
        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.assigneeType")
                && "INITIATOR".equals(field.oldValue()) && "MANUAL_ASSIGN".equals(field.newValue())));
        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.formId")
                && "101".equals(field.oldValue()) && "102".equals(field.newValue())));
        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.formName")
                && "Original form".equals(field.oldValue()) && "Updated form".equals(field.newValue())));
        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.actionIds")
                && "[10]".equals(field.oldValue()) && "[11]".equals(field.newValue())));
        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.timeoutDuration")
                && "PT1H".equals(field.oldValue()) && "PT2H".equals(field.newValue())));
        assertFalse(fields.stream().anyMatch(field -> field.field().endsWith("Fingerprint")));
        assertFalse(mapper.writeValueAsString(result).contains("SECRET_SENTINEL"));
    }

    @Test
    void opaqueExtensionChangeStaysHiddenAlongsideReadableProcessField() throws Exception {
        String xml = """
                <definitions xmlns:custom='http://workflow.platform/schema/custom'>
                  <userTask id='review'><extensionElements><custom:properties>
                    <custom:property name='formId' value='101'/>
                    <custom:property name='password' value='SECRET_BEFORE'/>
                  </custom:properties></extensionElements></userTask>
                </definitions>
                """.replace("\n", "");
        var result = compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("value='101'", "value='102'")
                        .replace("SECRET_BEFORE", "SECRET_AFTER"))));
        var fields = module(result, "PROCESS").semantic().items().get(0).fields();

        assertTrue(fields.stream().anyMatch(field -> field.field().equals("config.formId")
                && "101".equals(field.oldValue()) && "102".equals(field.newValue())));
        assertTrue(fields.stream().anyMatch(field -> field.field().endsWith("Fingerprint")
                && "[hidden configuration]".equals(field.oldValue())
                && "[hidden configuration]".equals(field.newValue())));
        String json = mapper.writeValueAsString(result);
        assertFalse(json.contains("SECRET_BEFORE"));
        assertFalse(json.contains("SECRET_AFTER"));
    }

    @Test
    void legacyCustomValuesExposeSavedFormChange() {
        String xml = """
                <definitions xmlns:legacy='http://custom.bpmn.io/schema'>
                  <startEvent id='start'><extensionElements><legacy:properties>
                    <legacy:values name='formId' value='101'/>
                    <legacy:values name='formName' value='Original'/>
                  </legacy:properties></extensionElements></startEvent>
                </definitions>
                """.replace("\n", "");
        var process = module(compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("101", "102")
                        .replace("Original", "Updated")))), "PROCESS").semantic();

        assertEquals(1, process.counts().modified());
        assertTrue(process.items().get(0).fields().stream().anyMatch(field ->
                field.field().equals("config.formId") && "101".equals(field.oldValue())
                        && "102".equals(field.newValue())));
        assertFalse(process.items().get(0).fields().stream().anyMatch(field ->
                field.field().endsWith("Fingerprint")));
    }

    @Test
    void duplicatePropertyAndValuesFollowDesignerPrecedence() {
        String xml = """
                <definitions xmlns:legacy='http://custom.bpmn.io/schema'>
                  <startEvent id='start'><extensionElements><legacy:properties>
                    <legacy:values name='formId' value='101'/>
                    <legacy:property name='formId' value='shadowed'/>
                  </legacy:properties></extensionElements></startEvent>
                </definitions>
                """.replace("\n", "");
        var shadowChange = module(compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("shadowed", "ignored")))),
                "PROCESS").semantic();
        assertEquals(0, count(shadowChange.counts()));

        var visibleChange = module(compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("101", "102")))),
                "PROCESS").semantic();
        var field = visibleChange.items().get(0).fields().get(0);
        assertEquals("config.formId", field.field());
        assertEquals("101", field.oldValue());
        assertEquals("102", field.newValue());
    }

    @Test
    void directBpmnScriptTextIsComparedWithoutReturningItsBody() throws Exception {
        String xml = "<definitions><scriptTask id='run'><script>SECRET_BEFORE</script></scriptTask></definitions>";
        VersionCompareResponse result = compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("SECRET_BEFORE", "SECRET_AFTER"))));

        assertEquals(1, module(result, "PROCESS").semantic().counts().modified());
        String json = mapper.writeValueAsString(result);
        assertFalse(json.contains("SECRET_BEFORE"));
        assertFalse(json.contains("SECRET_AFTER"));
    }

    @Test
    void malformedHistoricalDmnRetainsGenericFallbackWithExplicitSemanticStatus() {
        String before = snapshot("\"decisions\":[\"<broken>\"]");
        String after = snapshot("\"decisions\":[\"<other>\"]");
        var decision = module(compare(before, after), "DECISIONS");

        assertEquals("UNPARSEABLE", decision.semantic().status());
        assertTrue(count(decision.counts()) > 0);
    }

    @Test
    void decisionWithoutStableIdUsesExplicitGenericFallback() {
        String before = snapshot("\"decisions\":[\"<definitions><decision name='Review'/></definitions>\"]");
        String after = before.replace("Review", "Approve");
        var decision = module(compare(before, after), "DECISIONS");

        assertEquals("UNPARSEABLE", decision.semantic().status());
        assertTrue(count(decision.counts()) > 0);
    }

    @Test
    void processLayoutChangeIsLabeledWithoutBusinessNodeChange() {
        String xml = """
                <definitions xmlns:bpmndi='http://www.omg.org/spec/BPMN/20100524/DI'
                  xmlns:dc='http://www.omg.org/spec/DD/20100524/DC'>
                  <task id='review' name='Review'/>
                  <bpmndi:BPMNShape id='review-di' bpmnElement='review'>
                    <dc:Bounds x='100' y='20'/>
                  </bpmndi:BPMNShape>
                </definitions>
                """.replace("\n", "");
        VersionCompareResponse response = compare(snapshot("\"process\":" + quote(xml)),
                snapshot("\"process\":" + quote(xml.replace("x='100'", "x='200'"))));

        var process = module(response, "PROCESS").semantic();
        assertEquals(1, process.counts().modified());
        assertEquals("LAYOUT_ONLY", process.items().get(0).scope());
        assertEquals(0, count(module(response, "AUTOMATION").semantic().counts()));
    }

    @Test
    void actionEmailTemplateAndDocumentChangesUseSeparateSemanticObjects() {
        String before = snapshot("""
                "actions":[{"actionName":"Approve","configJson":{"buttonColor":"blue"}}],
                "emailTemplates":[{"name":"Receipt","subject":"Old","bodyHtml":"<p>Before</p>"}],
                "documents":{"REQUIREMENTS":"Original requirements"}
                """);
        String after = before.replace("blue", "green")
                .replace("\"subject\":\"Old\"", "\"subject\":\"New\"")
                .replace("Original requirements", "Updated requirements");
        VersionCompareResponse response = compare(before, after);

        assertEquals("ACTION", module(response, "ACTIONS").semantic().items().get(0).objectType());
        assertEquals("EMAIL_TEMPLATE", module(response, "EMAIL_TEMPLATES").semantic().items().get(0).objectType());
        assertEquals("DOCUMENT", module(response, "DOCUMENTS").semantic().items().get(0).objectType());
        assertEquals(3, response.displayTotals().modified());
        assertEquals(0, count(module(compare(after, after), "DOCUMENTS").semantic().counts()));
    }

    @Test
    void semanticAddedAndRemovedCountsSwapWhenDirectionReverses() {
        String before = snapshot("""
                "tables":[{"tableName":"old","fields":[{"fieldName":"id"}]}]
                """);
        String after = snapshot("""
                "tables":[{"tableName":"new","fields":[{"fieldName":"id"}]}]
                """);
        var forward = module(compare(before, after), "TABLES").semantic().counts();
        var reverse = module(compare(after, before), "TABLES").semantic().counts();

        assertEquals(forward.added(), reverse.removed());
        assertEquals(forward.removed(), reverse.added());
        assertEquals(forward.modified(), reverse.modified());
    }

    @Test
    void limitsSemanticItemsWithoutLosingCounts() {
        StringBuilder actions = new StringBuilder("\"actions\":[");
        for (int index = 0; index < 301; index++) {
            if (index > 0) actions.append(',');
            actions.append("{\"actionName\":\"action-").append(index).append("\"}");
        }
        actions.append(']');
        var semantic = module(compare(snapshot(""), snapshot(actions.toString())), "ACTIONS").semantic();

        assertEquals(301, semantic.counts().added());
        assertEquals(300, semantic.items().size());
        assertTrue(semantic.truncated());
    }

    @Test
    void limitsFieldsPerObjectWithoutLosingObjectCount() {
        StringBuilder oldFields = new StringBuilder();
        StringBuilder newFields = new StringBuilder();
        for (int index = 0; index < 31; index++) {
            if (index > 0) {
                oldFields.append(',');
                newFields.append(',');
            }
            oldFields.append("\"field").append(index).append("\":\"before\"");
            newFields.append("\"field").append(index).append("\":\"after\"");
        }
        String before = snapshot("\"actions\":[{\"actionName\":\"QA\",\"configJson\":{"
                + oldFields + "}}]");
        String after = snapshot("\"actions\":[{\"actionName\":\"QA\",\"configJson\":{"
                + newFields + "}}]");
        var semantic = module(compare(before, after), "ACTIONS").semantic();

        assertEquals(1, semantic.counts().modified());
        assertEquals(1, semantic.items().size());
        assertEquals(30, semantic.items().get(0).fields().size());
        assertTrue(semantic.truncated());
    }

    private VersionCompareResponse compare(String before, String after) {
        return engine.compare(version(1, before), version(2, after));
    }

    private VersionCompareResponse.ModuleDiff module(VersionCompareResponse response, String key) {
        return response.modules().stream().filter(module -> module.key().equals(key)).findFirst().orElseThrow();
    }

    private int count(VersionCompareResponse.Counts counts) {
        return counts.added() + counts.modified() + counts.removed();
    }

    private String snapshot(String extra) {
        String base = "{\"snapshotSchemaVersion\":2,\"name\":\"QA\",\"tables\":[],"
                + "\"forms\":[],\"actions\":[],\"decisions\":[],\"emailTemplates\":[],"
                + "\"emailMonitors\":[],\"connections\":[],\"documents\":{}}";
        try {
            ObjectNode snapshot = (ObjectNode) mapper.readTree(base);
            if (!extra.isBlank()) {
                mapper.readTree("{" + extra + "}").properties()
                        .forEach(entry -> snapshot.set(entry.getKey(), entry.getValue()));
            }
            return mapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid test snapshot", e);
        }
    }

    private String subFormSnapshot(int bindingId, boolean readonly) {
        return snapshot("\"forms\":[{\"formName\":\"Case\",\"tableBindings\":[{"
                + "\"bindingId\":" + bindingId + ",\"bindingType\":\"SUB_TABLE\","
                + "\"tableName\":\"detail\",\"sortOrder\":0}],\"configJson\":{"
                + "\"rule\":[],\"subForms\":{\"" + bindingId + "\":{\"rule\":[{"
                + "\"field\":\"note\",\"title\":\"Note\",\"type\":\"input\","
                + "\"props\":{\"readonly\":" + readonly + "}}]}}}}]");
    }

    private String quote(String value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Version version(long id, String snapshot) {
        return Version.builder().id(id).versionNumber("1.0." + id)
                .snapshotData(snapshot.getBytes(StandardCharsets.UTF_8)).build();
    }
}

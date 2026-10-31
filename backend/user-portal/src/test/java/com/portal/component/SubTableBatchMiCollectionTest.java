package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchMiCollectionTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcTemplate jdbc = spy(new JdbcTemplate(database));
    private final MiCollectionVariableBuilder builder = new MiCollectionVariableBuilder(engine, jdbc);
    SubTableBatchMiCollectionTest() {
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(100) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        for (String id : List.of("user-one", "user-two", "u1", "u2")) jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", id, "ACTIVE", false);
        jdbc.execute("CREATE TABLE sys_business_units(id VARCHAR(100), code VARCHAR(100), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_roles(id VARCHAR(100), code VARCHAR(100), type VARCHAR(30), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_business_unit_roles(business_unit_id VARCHAR(100), role_id VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles(user_id VARCHAR(100), business_unit_id VARCHAR(100), role_id VARCHAR(100))");
        jdbc.update("INSERT INTO sys_business_units VALUES('bu-id', 'bu-a', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_roles VALUES('role-id', 'role-a', 'BU_BOUNDED', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES('bu-id', 'role-id')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES('u1', 'bu-id', 'role-id')");
        clearInvocations(jdbc);
    }
    @AfterEach void close() { database.shutdown(); }
    private static final String XML = """
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="urn:synthetic">
          <bpmn:process id="process"><bpmn:userTask id="host"><bpmn:extensionElements><custom:properties>
          <custom:property name="formId" value="form"/></custom:properties></bpmn:extensionElements></bpmn:userTask>
          <bpmn:sequenceFlow id="next" sourceRef="host" targetRef="mi"/>
          <bpmn:subProcess id="mi"><bpmn:multiInstanceLoopCharacteristics flowable:collection="rowsToCreate" flowable:elementVariable="item"/>
          <bpmn:userTask id="child"><bpmn:extensionElements><custom:properties><custom:property name="formId" value="form"/>
          <custom:property name="subTableName" value="synthetic_lines"/><custom:property name="subTableId" value="987"/>
          <custom:property name="assigneeMode" value="both"/><custom:property name="assigneeField" value="handler_code"/>
          <custom:property name="roleField" value="pool_code"/><custom:property name="buField" value="unit_code"/>
          </custom:properties></bpmn:extensionElements></bpmn:userTask></bpmn:subProcess></bpmn:process>
        </bpmn:definitions>
        """;

    @Test
    void frozenStoreAndKeysWinWithoutLiveDwOrCandidateColumnGuessing() {
        var variables = variables(List.of(row("one", "user-one"), row("two", "user-two")));
        inject(variables, controls());
        assertThat((List<?>) variables.get("rowsToCreate")).hasSize(2);
        var item = (Map<?, ?>) ((List<?>) variables.get("rowsToCreate")).get(0);
        assertThat(item.get("rowKey")).isEqualTo(Map.of("line_key", "one"));
        assertThat(item.get("handler_code")).isEqualTo("user-one");
        assertNoLiveDwReads();
        verify(engine, never()).getBpmnXml(anyString());
    }

    @Test
    void lookupPersonIdentityIsUsedForTaskCreationWithoutChangingTheSourceRow() {
        var row = new LinkedHashMap<>(row("one", "user-one"));
        var person = Map.of("id", "user-one", "email", "test@example.invalid");
        row.put("handler_code", person);
        var variables = variables(List.of(row));
        inject(variables, controls());
        var item = (Map<?, ?>) ((List<?>) variables.get("rowsToCreate")).get(0);
        assertThat(item.get("handler_code")).isEqualTo("user-one");
        assertThat(row.get("handler_code")).isEqualTo(person);
        row.put("handler_code", Map.of("email", "test@example.invalid"));
        assertThatThrownBy(() -> inject(variables(List.of(row)), controls())).hasMessageContaining("source cannot be verified");
    }

    @Test
    void missingFrozenTargetCannotUseAnAssigneeAliasAndNeverPartiallyPublishesCollection() {
        var invalid = new LinkedHashMap<>(row("two", "user-two"));
        invalid.remove("handler_code"); invalid.put("assignee", "wrong-alias-person");
        var variables = variables(List.of(row("one", "user-one"), invalid));
        assertThatThrownBy(() -> inject(variables, controls())).hasMessageContaining("source cannot be verified");
        assertThat(variables).doesNotContainKey("rowsToCreate");
        verifyNoInteractions(jdbc);
    }

    @Test
    void duplicateOrMissingFrozenIdentityRejectsWholeCollection() {
        for (var rows : List.of(List.of(row("one", "u1"), row("one", "u2")), List.of(Map.of("handler_code", "u1")))) {
            var variables = variables(rows);
            assertThatThrownBy(() -> inject(variables, controls())).hasMessageContaining("source cannot be verified");
            assertThat(variables).doesNotContainKey("rowsToCreate");
        }
    }

    @Test
    void conflictingModesAndMappingDriftAreExplicitErrors() {
        var both = new LinkedHashMap<>(row("one", "u1"));
        both.put("pool_code", "role-a"); both.put("unit_code", "bu-a");
        assertThatThrownBy(() -> inject(variables(List.of(both)), controls())).hasMessageContaining("source cannot be verified");
        var config = new LinkedHashMap<>(config()); config.put("assigneeField", "other_handler");
        var control = new LinkedHashMap<>(control()); control.put("assignmentConfig", config);
        assertThatThrownBy(() -> inject(variables(List.of(row("one", "u1"))), List.of(control)))
                .hasMessageContaining("source cannot be verified");
    }

    @Test
    void rolePoolRemainsRoleAndNoPersonalTargetIsInvented() {
        var variables = variables(List.of(Map.of("line_key", "role-row", "pool_code", "role-a", "unit_code", "bu-a", "case_ref", "case-one")));
        inject(variables, controls());
        var item = (Map<?, ?>) ((List<?>) variables.get("rowsToCreate")).get(0);
        assertThat(item.get("pool_code")).isEqualTo("role-a");
        assertThat(item.get("unit_code")).isEqualTo("bu-a");
        assertThat(item.containsKey("handler_code")).isFalse();
        assertNoLiveDwReads();
    }

    @Test
    void alreadyCreatedChildCanCompleteWithoutRegeneratingFutureCollection() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        var variables = variables(List.of(row("one", "u1")));
        builder.injectMiCollectionFromBpmn("process", "child", "instance", variables, snapshot(controls()));
        assertThat(variables).doesNotContainKey("rowsToCreate");
        verifyNoInteractions(jdbc);
    }

    @Test
    void twoFormsMayReuseAControlIdWithoutMixingTheirContracts() {
        var other = new LinkedHashMap<>(control());
        other.put("bindingId", "other-binding");
        other.put("assignmentConfig", Map.of("taskDefinitionKey", "foreign-task"));
        var baseline = Map.<String, Object>of("case_key", "case-one", SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", controls()), "foreign-form", Map.of("controls", List.of(other)))));
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        var variables = variables(List.of(row("one", "u1")));
        builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, baseline);
        assertThat((List<?>) variables.get("rowsToCreate")).hasSize(1);
        assertNoLiveDwReads();
    }

    @Test
    void simultaneousTableAndTargetDriftCannotFallBackToTheLegacyBuilder() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(
                XML.replace("value=\"synthetic_lines\"", "value=\"other_table\"").replace("id=\"child\"", "id=\"other_child\"")));
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, snapshot(controls())))
                .hasRootCauseMessage("Frozen assignment does not match deployed BPMN");
        assertThat(variables).doesNotContainKey("rowsToCreate");
        verifyNoInteractions(jdbc);
    }

    @Test
    void roleWithoutABuFieldPreservesTheExistingOptionalBuContract() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML.replace(
                "<custom:property name=\"buField\" value=\"unit_code\"/>", "")));
        var config = new LinkedHashMap<>(config()); config.remove("buField");
        var control = new LinkedHashMap<>(control()); control.put("assignmentConfig", config);
        var variables = variables(List.of(Map.of("line_key", "one", "pool_code", "role-a", "case_ref", "case-one")));
        builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, snapshot(List.of(control)));
        var item = (Map<?, ?>) ((List<?>) variables.get("rowsToCreate")).get(0);
        assertThat(item.get("pool_code")).isEqualTo("role-a");
        assertThat(item.containsKey("unit_code")).isFalse();
        assertNoLiveDwReads();
    }

    @Test
    void missingDeployedFormSourceAndInvalidSnapshotRejectInsteadOfUsingLiveDesign() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML.replace("name=\"formId\"", "name=\"unrelated\"")));
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "host", "instance",
                variables(List.of(row("one", "u1"))), snapshot(controls())))
                .hasRootCauseMessage("Instance batch capability snapshot is invalid");
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingCanonicalStoreCannotUseASimilarUnrelatedSlice() {
        assertThatThrownBy(() -> inject(new LinkedHashMap<>(Map.of("__subTables__", Map.of(
                "dw:unrelated", List.of(row("one", "u1"))))), controls()))
                .hasRootCauseMessage("Frozen assignment canonical store is missing");
        verifyNoInteractions(jdbc);
    }

    @Test
    void nonAssignmentControlsDoNotValidateUnrelatedAssignmentConfiguration() throws Exception {
        var control = new LinkedHashMap<>(control());
        control.put("capabilities", Map.of("allowBatchEdit", true));
        var document = BpmnMiXmlSupport.parseBpmnSecurely(XML.replace("value=\"both\"", "value=\"unsupported-mode\""));
        assertThatCode(() -> SubTableBatchMiCollection.validateTargets(document, Map.of("control", control))).doesNotThrowAnyException();
        var subProcess = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "subProcess", "mi");
        assertThat(SubTableBatchMiCollection.inject(subProcess, new HashMap<>(), Map.of("control", control), Map.of(), (people, roles) -> { })).isFalse();
    }

    @Test
    void onlyThePersistedMainParentScopeCanGenerateParticipants() {
        var foreign = new LinkedHashMap<>(row("foreign", "foreign-user")); foreign.put("case_ref", "case-two");
        var variables = variables(List.of(row("one", "u1"), foreign));
        inject(variables, controls());
        var items = (List<?>) variables.get("rowsToCreate");
        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.get(0)).get("rowKey")).isEqualTo(Map.of("line_key", "one"));
        assertNoLiveDwReads();
    }

    @Test
    void missingParentAuthorityCannotAuthorizeEveryRow() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        var baseline = new LinkedHashMap<>(snapshot(controls())); baseline.remove("case_key");
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, baseline))
                .hasRootCauseMessage("Frozen assignment parent scope is missing or ambiguous");
        assertThat(variables).doesNotContainKey("rowsToCreate");
    }

    @Test
    void nestedTargetCannotWriteInnerRowsIntoTheOuterCollection() {
        String nested = XML.replace("targetRef=\"mi\"", "targetRef=\"outer\"")
                .replace("<bpmn:subProcess id=\"mi\">", "<bpmn:subProcess id=\"outer\"><bpmn:multiInstanceLoopCharacteristics flowable:collection=\"outerRows\"/><bpmn:subProcess id=\"mi\">")
                .replace("</bpmn:subProcess>", "</bpmn:subProcess></bpmn:subProcess>");
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(nested));
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, snapshot(controls())))
                .hasRootCauseMessage("Nested assignment target requires a separate collection scope");
        assertThat(variables).doesNotContainKeys("rowsToCreate", "outerRows");
        verifyNoInteractions(jdbc);
    }

    @Test
    void savedPersonBecomingInactiveBlocksCompleteWithoutPublishingAnyCollection() {
        jdbc.update("UPDATE sys_users SET status=? WHERE id=?", "INACTIVE", "u1");
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> inject(variables, controls()))
                .hasRootCauseMessage("Initial assignment person target is not currently active");
        assertThat(variables).doesNotContainKey("rowsToCreate");
    }

    @Test
    void savedRoleLosingAdmissionOrAllActiveMembersBlocksCompleteBeforePublishing() {
        jdbc.update("DELETE FROM sys_business_unit_roles");
        var variables = variables(List.of(Map.of("line_key", "role-row", "pool_code", "role-a", "unit_code", "bu-a", "case_ref", "case-one")));
        assertThatThrownBy(() -> inject(variables, controls())).hasRootCauseMessage("Initial assignment role target is not currently eligible");
        assertThat(variables).doesNotContainKey("rowsToCreate");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES('bu-id', 'role-id')");
        jdbc.update("UPDATE sys_users SET status='INACTIVE' WHERE id='u1'");
        assertThatThrownBy(() -> inject(variables, controls())).hasRootCauseMessage("Initial assignment role target is not currently eligible");
        assertThat(variables).doesNotContainKey("rowsToCreate");
    }

    @Test
    void firstTaskRebuildsFrozenTargetsInsteadOfTrustingAnAlreadyPreparedCollection() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        var variables = variables(List.of(row("one", "u1")));
        variables.put("rowsToCreate", List.of(Map.of("handler_code", "untrusted-target")));
        var expected = builder.injectMiCollectionForFirstTask("process", "host", "instance", variables, snapshot(controls()));
        assertThat(expected).containsExactly(new SubTableBatchInitialMiOutcome.Expected(
                "child", true, "synthetic_lines", List.of(Map.of("line_key", "one")),
                List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("line_key", "one"), "u1")), List.of()));
        var item = (Map<?, ?>) ((List<?>) variables.get("rowsToCreate")).get(0);
        assertThat(item.get("handler_code")).isEqualTo("u1");
        assertThat(item.get("rowKey")).isEqualTo(Map.of("line_key", "one"));
    }

    @Test
    void firstTaskRetainsPreparedCollectionsForTargetsThatDoNotEnableBatchAssign() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        var disabled = new HashMap<>(control()); disabled.put("capabilities", Map.of("allowBatchAssign", false));
        var prepared = List.of(Map.of("legacy", "prepared-business-collection"));
        var variables = variables(List.of()); variables.put("rowsToCreate", prepared);
        assertThat(builder.injectMiCollectionForFirstTask("process", "host", "instance", variables, snapshot(List.of(disabled)))).isEmpty();
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared);
        verifyNoInteractions(jdbc);
    }

    @Test
    void frozenFirstTaskStopsAtTheNextHumanHostRatherThanDemandingItsFutureAssignment() {
        String xml = XML.replace("targetRef=\"mi\"", "targetRef=\"future-host\"").replace("<bpmn:subProcess id=\"mi\">",
                "<bpmn:userTask id=\"future-host\"/><bpmn:sequenceFlow id=\"later\" sourceRef=\"future-host\" targetRef=\"mi\"/><bpmn:subProcess id=\"mi\">");
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(xml));
        var variables = variables(List.of(Map.of("line_key", "one", "case_ref", "case-one")));
        assertThat(builder.injectMiCollectionForFirstTask("process", "host", "instance", variables, snapshot(controls()))).isEmpty();
        assertThat(variables).doesNotContainKey("rowsToCreate");
        verifyNoInteractions(jdbc);
    }

    @Test
    void callActivityLookAheadStillValidatesTheCurrentProcessesReachableAssignmentCollection() {
        String xml = XML.replace("targetRef=\"mi\"", "targetRef=\"called\"").replace("<bpmn:subProcess id=\"mi\">",
                "<bpmn:callActivity id=\"called\"/><bpmn:sequenceFlow id=\"later\" sourceRef=\"called\" targetRef=\"mi\"/><bpmn:subProcess id=\"mi\">");
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(xml));
        var variables = variables(List.of(Map.of("line_key", "one", "case_ref", "case-one")));
        assertThatThrownBy(() -> builder.injectMiCollectionForFirstTask("process", "host", "instance", variables, snapshot(controls())))
                .hasMessageContaining("source cannot be verified");
        assertThat(variables).doesNotContainKey("rowsToCreate");
    }

    private List<SubTableBatchInitialMiOutcome.Expected> inject(Map<String, Object> variables, List<?> controls) {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        return builder.injectMiCollectionFromBpmn("process", "host", "instance", variables, snapshot(controls));
    }

    @Test void completionReturnsOnlyItsStrictPreparedAssignmentNodePlan() {
        assertThat(inject(variables(List.of(row("one", "u1"))), controls()))
                .extracting(SubTableBatchInitialMiOutcome.Expected::node, SubTableBatchInitialMiOutcome.Expected::personalOnly)
                .containsExactly(tuple("child", true));
        var mixed = Map.of("line_key", "two", "pool_code", "role-a", "unit_code", "bu-a", "case_ref", "case-one");
        assertThat(inject(variables(List.of(row("one", "u1"), mixed)), controls()))
                .extracting(SubTableBatchInitialMiOutcome.Expected::node, SubTableBatchInitialMiOutcome.Expected::personalOnly)
                .containsExactly(tuple("child", false));
        assertThat(inject(variables(List.of()), controls())).isEmpty();
    }

    private static String initialXml() {
        return XML.replace("<bpmn:userTask id=\"host\">", "<bpmn:startEvent id=\"host\">")
                .replaceFirst("</bpmn:userTask>", "</bpmn:startEvent>");
    }
    @Test void initialMiReplacesClientCollectionUsingFrozenCanonicalRowsBeforeStart() {
        var variables = variables(List.of(row("one", "u1"), row("two", "u2")));
        variables.put("rowsToCreate", List.of(Map.of("handler_code", "untrusted")));
        var plan = SubTableBatchInitialMiPreflight.prepare(initialXml(), "process", variables, snapshot(controls()), jdbc);
        assertThat(plan).extracting(SubTableBatchInitialMiOutcome.Expected::node, SubTableBatchInitialMiOutcome.Expected::personalOnly)
                .containsExactly(tuple("child", true));
        assertThat((List<?>) variables.get("rowsToCreate")).hasSize(2);
        assertThat(variables).doesNotContainKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        verifyNoInteractions(engine);
    }
    @Test void initialMiRejectsInvalidTargetsAndDoesNotPublishAnyPreparedCollection() {
        var variables = variables(List.of(row("one", "u1"), row("two", "inactive-synthetic-user")));
        var spoofed = List.of(Map.of("handler_code", "untrusted")); variables.put("rowsToCreate", spoofed);
        assertThatThrownBy(() -> SubTableBatchInitialMiPreflight.prepare(initialXml(), "process", variables, snapshot(controls()), jdbc))
                .hasMessageContaining("active");
        assertThat(variables.get("rowsToCreate")).isSameAs(spoofed);
        verifyNoInteractions(engine);
    }
    @Test void initialPreflightDoesNotDemandFutureHumanHostsAssignmentsOrAffectLegacyStarts() {
        var variables = variables(List.of(Map.of("line_key", "one", "case_ref", "case-one")));
        String ordinary = XML.replace("<bpmn:process id=\"process\">", "<bpmn:process id=\"process\"><bpmn:startEvent id=\"start\"/>"
                + "<bpmn:sequenceFlow id=\"initial-flow\" sourceRef=\"start\" targetRef=\"host\"/>");
        SubTableBatchInitialMiPreflight.prepare(ordinary, "process", variables, snapshot(controls()), jdbc);
        SubTableBatchInitialMiPreflight.prepare("not xml", "process", variables, Map.of(), jdbc);
        assertThat(variables).doesNotContainKey("rowsToCreate");
        verifyNoInteractions(engine, jdbc);
    }
    @Test void initialPreflightRetainsPreparedCollectionsForNonAssignmentControls() {
        var disabled = new HashMap<>(control()); disabled.put("capabilities", Map.of("allowBatchAssign", false));
        var prepared = List.of(Map.of("legacy", "prepared"));
        var variables = variables(List.of()); variables.put("rowsToCreate", prepared);
        assertThat(SubTableBatchInitialMiPreflight.prepare(initialXml(), "process", variables, snapshot(List.of(disabled)), jdbc)).isEmpty();
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared);
        verifyNoInteractions(engine, jdbc);
    }
    @Test void laterInitialMiFailureCannotPublishAnEarlierCollectionsPreparedRows() {
        String xml = twoInitialTargets(false);
        var other = new HashMap<>(control());
        var otherConfig = new HashMap<>(config()); otherConfig.put("taskDefinitionKey", "other-child"); other.put("assignmentConfig", otherConfig);
        var design = new HashMap<>((Map<String, Object>) other.get("rowDesign"));
        var binding = new HashMap<>((Map<String, Object>) design.get("binding")); binding.put("tableName", "other_lines");
        design.put("binding", binding); other.put("rowDesign", design); other.put("batchControlId", "other-control");
        var variables = variables(List.of(row("one", "u1")));
        var stores = new HashMap<>((Map<String, Object>) variables.get("__subTables__"));
        stores.put("dw:other_lines", List.of(row("two", "inactive-synthetic-user"))); variables.put("__subTables__", stores);
        var prepared = List.of(Map.of("legacy", "untrusted")); variables.put("rowsToCreate", prepared);
        String source = xml;
        assertThatThrownBy(() -> SubTableBatchInitialMiPreflight.prepare(source, "process", variables, snapshot(List.of(control(), other)), jdbc))
                .hasMessageContaining("active");
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared);
        assertThat(variables).doesNotContainKey("otherRows");
    }
    private static String twoInitialTargets(boolean sameCollection) {
        String xml = initialXml();
        String second = xml.substring(xml.indexOf("<bpmn:subProcess"), xml.indexOf("</bpmn:subProcess>") + "</bpmn:subProcess>".length())
                .replace("id=\"mi\"", "id=\"other-mi\"").replace("id=\"child\"", "id=\"other-child\"").replace("synthetic_lines", "other_lines");
        if (!sameCollection) second = second.replace("rowsToCreate", "otherRows");
        return xml.replace("</bpmn:process>", "<bpmn:sequenceFlow id=\"second\" sourceRef=\"host\" targetRef=\"other-mi\"/>" + second + "</bpmn:process>");
    }
    @Test void directMiWithoutAnyAssignRetainsLegacyCollectionEvenWithoutAStartForm() {
        var disabled = new HashMap<>(control()); disabled.put("capabilities", Map.of("allowBatchEdit", true));
        var variables = variables(List.of()); var prepared = List.of(Map.of("legacy", "prepared")); variables.put("rowsToCreate", prepared);
        String xml = initialXml().replace("<custom:property name=\"formId\" value=\"form\"/>", "");
        SubTableBatchInitialMiPreflight.prepare(xml, "process", variables, snapshot(List.of(disabled)), jdbc);
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared); verifyNoInteractions(engine, jdbc);
    }
    @Test void assignmentAndLegacyTargetsCannotShareAnInitialCollectionName() {
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> SubTableBatchInitialMiPreflight.prepare(twoInitialTargets(true), "process", variables, snapshot(controls()), jdbc))
                .hasMessageContaining("overlap");
        assertThat(variables).doesNotContainKey("rowsToCreate");
    }
    @Test void differentLegacyCollectionRemainsUntouchedBesideAnInitialAssignmentTarget() {
        var variables = variables(List.of(row("one", "u1"))); var prepared = List.of(Map.of("legacy", "prepared"));
        variables.put("otherRows", prepared);
        SubTableBatchInitialMiPreflight.prepare(twoInitialTargets(false), "process", variables, snapshot(controls()), jdbc);
        assertThat(variables.get("otherRows")).isSameAs(prepared);
        assertThat((List<?>) variables.get("rowsToCreate")).hasSize(1);
    }
    @Test void multipleInitialEventsAreRejectedRatherThanGuessingWhichOneTheKeyStartUses() {
        String xml = initialXml().replace("</bpmn:process>", "<bpmn:startEvent id=\"another-start\"/></bpmn:process>");
        var variables = variables(List.of(row("one", "u1")));
        assertThatThrownBy(() -> SubTableBatchInitialMiPreflight.prepare(xml, "process", variables, snapshot(controls()), jdbc))
                .hasMessageContaining("ambiguous");
        assertThat(variables).doesNotContainKey("rowsToCreate"); verifyNoInteractions(engine, jdbc);
    }
    @Test void aDifferentFormsAssignCapabilityNeitherAuthorizesNorBlocksTheLegacyStartCollection() {
        var disabled = new HashMap<>(control()); disabled.put("capabilities", Map.of("allowBatchEdit", true));
        var baseline = new HashMap<>(snapshot(controls()));
        baseline.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1, "forms",
                Map.of("form", Map.of("controls", List.of(disabled)), "other-form", Map.of("controls", controls()))));
        var variables = variables(List.of(row("one", "u1")));
        var prepared = List.of(Map.of("legacy", "prepared")); variables.put("rowsToCreate", prepared);
        SubTableBatchInitialMiPreflight.prepare(initialXml(), "process", variables, baseline, jdbc);
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared); verifyNoInteractions(engine, jdbc);
    }
    @Test void anotherTablesSameNamedTaskDoesNotProtectTheCurrentLegacyInitialCollection() {
        var foreign = new HashMap<>(control());
        var design = new HashMap<>((Map<String, Object>) foreign.get("rowDesign"));
        var binding = new HashMap<>((Map<String, Object>) design.get("binding")); binding.put("tableName", "another_process_table");
        design.put("binding", binding); foreign.put("rowDesign", design);
        var baseline = new HashMap<>(snapshot(controls()));
        baseline.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("foreign-form", Map.of("controls", List.of(foreign)))));
        var variables = variables(List.of()); var prepared = List.of(Map.of("legacy", "prepared")); variables.put("rowsToCreate", prepared);
        String xml = initialXml().replace("<custom:property name=\"formId\" value=\"form\"/>", "");
        SubTableBatchInitialMiPreflight.prepare(xml, "process", variables, baseline, jdbc);
        assertThat(variables.get("rowsToCreate")).isSameAs(prepared); verifyNoInteractions(engine, jdbc);
    }
    private static Map<String, Object> variables(List<?> rows) {
        return new LinkedHashMap<>(Map.of("__subTables__", Map.of("dw:synthetic_lines", rows,
                "dw:unrelated", List.of(row("wrong", "foreign-person")))));
    }
    private static Map<String, Object> row(String key, String target) { return Map.of("line_key", key, "handler_code", target, "case_ref", "case-one"); }
    private static List<?> controls() { return List.of(control()); }
    private static Map<String, Object> control() {
        return Map.of("batchControlId", "control", "bindingId", "17", "capabilities", Map.of("allowBatchAssign", true),
                "assignmentConfig", config(), "rowDesign", Map.of("binding", Map.of("tableName", "synthetic_lines", "primaryKeyFields", List.of("line_key"),
                        "filterFkFieldName", "case_ref", "fieldDefinitions", List.of(Map.of("fieldName", "case_ref", "isForeignKey", true,
                                "refTableId", "parent", "refPrimaryKeyFields", List.of("case_key")))),
                        "parentTablesById", Map.of("parent", Map.of("tableName", "synthetic_header", "tableType", "MAIN"))));
    }
    private static Map<String, Object> config() {
        return Map.of("taskDefinitionKey", "child", "allowUser", true, "allowRole", true,
                "assigneeField", "handler_code", "roleField", "pool_code", "buField", "unit_code");
    }
    private static Map<String, Object> snapshot(List<?> controls) {
        return Map.of("case_key", "case-one", "activeBusinessUnitId", "bu-id", SubTableWriteIsolation.BATCH_RUNTIME_FIELD,
                Map.of("schemaVersion", 1, "forms", Map.of("form", Map.of("controls", controls))));
    }
    private void assertNoLiveDwReads() {
        verify(jdbc, never()).query(contains("dw_"), any(org.springframework.jdbc.core.RowMapper.class), anyLong());
    }
}

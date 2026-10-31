package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the portal's records follow a request into the Function Units it calls, and back out.
 */
class CalledProcessSyncComponentTest {

    private final Map<String, ProcessInstance> rows = new HashMap<>();
    private ProcessInstanceRepository repository;
    private WorkflowEngineClient engine;
    private ProcessInstanceHydrationComponent hydration;
    private ProcessInstanceSyncComponent sync;
    private CalledProcessSyncComponent component;
    private com.platform.common.functionunit.CatalogSnapshotReader snapshots;
    private static final String PURCHASE_RELEASE = "22222222-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        repository = mock(ProcessInstanceRepository.class);
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<String>getArgument(0))));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> {
            ProcessInstance pi = inv.getArgument(0);
            rows.put(pi.getId(), pi);
            return pi;
        });
        engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstance(anyString())).thenReturn(Optional.of(Map.of("assignmentTracked",false)));
        when(engine.getSubProcesses(anyString())).thenReturn(List.of());
        hydration = mock(ProcessInstanceHydrationComponent.class);
        // Hydration from the engine: a called instance has no catalog pin and no start user.
        when(hydration.requireProcessInstance(anyString())).thenAnswer(inv -> rows.computeIfAbsent(
                inv.getArgument(0),
                id -> ProcessInstance.builder().id(id).processDefinitionKey("fu-vendor")
                        .functionUnitCode("").startUserId("system").status("RUNNING").build()));
        sync = mock(ProcessInstanceSyncComponent.class);
        snapshots = mock(com.platform.common.functionunit.CatalogSnapshotReader.class);
        component = new CalledProcessSyncComponent(repository, engine, hydration, sync, snapshots);

        rows.put("purchase", ProcessInstance.builder().id("purchase").processDefinitionKey("fu-purchase")
                .functionUnitCode("fu-purchase").startUserId("user-dev").startUserName("Developer Tester")
                .status("RUNNING").build());
    }

    private static Map<String, Object> status(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
    @Test void protectedCalledWorkDoesNotUseTheLegacyHydrationAndOwnerWriter() {
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("assignmentTracked",true)));
        when(engine.getSubProcesses("purchase")).thenReturn(List.of(Map.of("subProcessInstanceId","protected-child","callActivityId","call","embedded",false)));
        component.recordCalledWork("purchase",status("nextTaskProcessInstanceId","protected-child","nextTaskName","Review"));
        verify(hydration,never()).requireProcessInstance(anyString());
        verify(sync,never()).updateProcessInstanceAssignee(anyString(),any(),any(),any());
        verify(repository,never()).save(any());
    }
    @Test void protectedCallersAreProjectedOnlyByTheirAcceptedOperation() {
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("assignmentTracked",true)));
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status("completed",false,"nextTaskName","Review")));
        component.refreshCallers(status("completed",true,"processInstanceId","child","superProcessInstanceId","purchase"));
        verify(sync,never()).updateProcessInstanceAssignee(anyString(),any(),any(),any());
        verify(repository,never()).save(any());
    }

    @Test
    void recordsTheCalledInstanceLinkedToItsCaller() {
        when(engine.getSubProcesses("purchase")).thenReturn(List.of(Map.of(
                "subProcessInstanceId", "vendor-check", "callActivityId", "PR_Call_PrimaryVendor", "embedded", false)));

        component.recordCalledWork("purchase", status(
                "nextTaskName", "Review Vendor", "nextAssignee", "user-dev",
                "nextTaskProcessInstanceId", "vendor-check"));

        ProcessInstance child = rows.get("vendor-check");
        assertThat(child.getParentProcessInstanceId()).isEqualTo("purchase");
        assertThat(child.getCallActivityId()).isEqualTo("PR_Call_PrimaryVendor");
        assertThat(child.getFunctionUnitCode()).isEqualTo("fu-vendor");
        assertThat(child.getStartUserId()).isEqualTo("user-dev");
        verify(sync).updateProcessInstanceAssignee("vendor-check", "user-dev", null, "Review Vendor");
    }

    @Test
    void ignoresAStatusWhoseTaskIsTheRequestsOwn() {
        component.recordCalledWork("purchase", status(
                "nextTaskName", "Approve Purchase", "nextTaskProcessInstanceId", "purchase"));

        verify(engine, never()).getSubProcesses(anyString());
        verify(sync, never()).updateProcessInstanceAssignee(any(), any(), any(), any());
    }

    @Test
    void skipsEmbeddedMultiInstanceSubProcesses() {
        when(engine.getSubProcesses("purchase")).thenReturn(List.of(Map.of(
                "subProcessInstanceId", "purchase-mi", "callActivityId", "SP", "embedded", true)));

        component.recordCalledWork("purchase", status(
                "nextTaskName", "Review Vendor", "nextTaskProcessInstanceId", "vendor-check"));

        verify(hydration, never()).requireProcessInstance(anyString());
    }

    /** When the called unit finishes, the caller moves to its own next task. */
    @Test
    void refreshesTheCallerAfterTheCalledUnitMovesOn() {
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status(
                "completed", false, "nextTaskName", "Approve Purchase", "nextAssignee", "boss",
                "nextTaskProcessInstanceId", "purchase")));

        component.refreshCallers(status("completed", true, "superProcessInstanceId", "purchase"));

        verify(sync).updateProcessInstanceAssignee("purchase", "boss", null, "Approve Purchase");
    }

    @Test
    void doesNothingForATopLevelRequest() {
        component.refreshCallers(status("completed", false, "nextTaskName", "Approve Purchase"));

        verify(engine, never()).getProcessInstanceStatus(anyString());
    }

    @Test
    void neverFailsTheActionThatTriggeredIt() {
        when(engine.getSubProcesses("purchase")).thenThrow(new IllegalStateException("engine down"));

        component.recordCalledWork("purchase", status(
                "nextTaskName", "Review Vendor", "nextTaskProcessInstanceId", "vendor-check"));
        // no exception
    }

    /** The engine already holds the result; the portal record must too, or the next form loses it. */
    @Test
    void copiesTheMappedResultBackIntoTheCallersRecordWhenTheCallFinishes() {
        rows.put("vendor-check", ProcessInstance.builder().id("vendor-check").processDefinitionKey("fu-vendor")
                .parentProcessInstanceId("purchase").callActivityId("PR_Call_PrimaryVendor").build());
        rows.get("purchase").setVariables(new HashMap<>(Map.of("title", "Laptops")));
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-purchase">
                    <bpmn:callActivity id="PR_Call_PrimaryVendor" calledElement="fu-vendor">
                      <bpmn:extensionElements>
                        <flowable:out source="check_result" target="vendor_check_result"/>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>"""));
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(
                "vendor_check_result", "QUALIFIED", "title", "stale engine copy"))));
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status("completed", false)));

        component.refreshCallers(status(
                "completed", true, "processInstanceId", "vendor-check", "superProcessInstanceId", "purchase"));

        Map<String, Object> vars = rows.get("purchase").getVariables();
        assertThat(vars).containsEntry("vendor_check_result", "QUALIFIED");
        // Only mapped fields come back; the portal's own values are not overwritten from the engine.
        assertThat(vars).containsEntry("title", "Laptops");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "__subTableBatchRuntime__", "__subTableBatchDeletedRows__" })
    void rejectsEngineOutputMappingsToLocalBatchMetadata(String reserved) {
        rows.put("vendor-check", ProcessInstance.builder().id("vendor-check").processDefinitionKey("fu-vendor")
                .parentProcessInstanceId("purchase").callActivityId("PR_Call_PrimaryVendor").build());
        Map<String, Object> trusted = Map.of("server", "immutable");
        rows.get("purchase").setVariables(new HashMap<>(Map.of(reserved, trusted, "title", "local")));
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-purchase">
                    <bpmn:callActivity id="PR_Call_PrimaryVendor" calledElement="fu-vendor">
                      <bpmn:extensionElements>
                        <flowable:out source="result" target="title"/>
                        <flowable:out source="untrusted" target="%s"/>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>""".formatted(reserved)));
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(
                reserved, Map.of("forged", true), "title", "engine"))));

        component.refreshCallers(status("completed", true, "processInstanceId", "vendor-check",
                "superProcessInstanceId", "purchase"));

        assertThat(rows.get("purchase").getVariables()).containsEntry(reserved, trusted).containsEntry("title", "local");
        verify(repository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "__subTableBatchRuntime__", "__subTableBatchDeletedRows__" })
    void engineCannotCreateBatchMetadataOnLegacyCaller(String reserved) {
        rows.put("vendor-check", ProcessInstance.builder().id("vendor-check").processDefinitionKey("fu-vendor")
                .parentProcessInstanceId("purchase").callActivityId("call").build());
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
                  <process id="fu-purchase"><callActivity id="call"><extensionElements>
                    <f:out source="result" target="%s"/>
                  </extensionElements></callActivity></process>
                </definitions>""".formatted(reserved)));
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(reserved, "forged"))));

        component.refreshCallers(status("completed", true, "processInstanceId", "vendor-check",
                "superProcessInstanceId", "purchase"));

        assertThat(rows.get("purchase").getVariables()).isNull();
        verify(repository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "__subTableBatchRuntime__", "__subTableBatchDeletedRows__" })
    void neverCopiesCalledLocalMetadataIntoCallerBusinessRow(String reserved) {
        Map<String, Object> original = Map.of("platformRowUuid", "A", "caption", "local");
        rows.get("purchase").setVariables(new HashMap<>(Map.of("__subTables__", Map.of("dw:lines", List.of(original)))));
        rows.put("child", ProcessInstance.builder().id("child").callActivityId("call")
                .variables(Map.of(reserved, Map.of("private", "server-owned"))).build());
        when(engine.getProcessInstance("child")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(
                "__callRow", Map.of("platformRowUuid", "A")))));
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://custom.bpmn.io/schema">
                  <process id="fu-purchase"><callActivity id="call"><extensionElements><c:properties>
                    <c:property name="callRowsTable" value="lines"/>
                    <c:property name="callOutputMapping"
                      value="[{&quot;from&quot;:&quot;%s&quot;,&quot;to&quot;:&quot;row.caption&quot;}]"/>
                  </c:properties></extensionElements></callActivity></process>
                </definitions>""".formatted(reserved)));

        component.refreshCallers(status("completed", true, "processInstanceId", "child",
                "superProcessInstanceId", "purchase"));

        assertThat(rows.get("purchase").getVariables()).containsEntry("__subTables__", Map.of("dw:lines", List.of(original)));
        verify(repository, never()).save(any());
    }

    /** A per-row call's result goes into the row it ran for — and only that row. */
    @Test
    @SuppressWarnings("unchecked")
    void copiesAPerRowResultIntoTheRowItRanFor() {
        Map<String, Object> rowA = new HashMap<>(Map.of("platformRowUuid", "A", "vendor_name", "Globex"));
        Map<String, Object> rowB = new HashMap<>(Map.of("platformRowUuid", "B", "vendor_name", "Initech"));
        rows.get("purchase").setVariables(new HashMap<>(Map.of(
                "__subTables__", new HashMap<>(Map.of("dw:extra_vendors", List.of(rowA, rowB))))));
        rows.put("check-b", ProcessInstance.builder().id("check-b").processDefinitionKey("fu-vendor")
                .parentProcessInstanceId("purchase").callActivityId("PR_Call_ExtraVendors")
                .variables(new HashMap<>(Map.of("check_result", "QUALIFIED"))).build());
        when(engine.getProcessInstance("check-b")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(
                "__callRow", Map.of("platformRowUuid", "B", "vendor_name", "Initech")))));
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="fu-purchase">
                    <bpmn:callActivity id="PR_Call_ExtraVendors" calledElement="fu-vendor">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="callRowsTable" value="extra_vendors"/>
                          <custom:property name="callOutputMapping"
                              value="[{&quot;from&quot;:&quot;check_result&quot;,&quot;to&quot;:&quot;row.check_status&quot;}]"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics/>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>"""));
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status("completed", false)));

        component.refreshCallers(status(
                "completed", true, "processInstanceId", "check-b", "superProcessInstanceId", "purchase"));

        List<Map<String, Object>> saved = (List<Map<String, Object>>) ((Map<String, Object>)
                rows.get("purchase").getVariables().get("__subTables__")).get("dw:extra_vendors");
        assertThat(saved.get(1)).containsEntry("check_status", "QUALIFIED");
        assertThat(saved.get(0)).doesNotContainKey("check_status");
    }

    private void perRowCallOnRowsWithoutPlatformIds() {
        rows.get("purchase").setVariables(new HashMap<>(Map.of("__subTables__", new HashMap<>(Map.of(
                "dw:extra_vendors", List.of(
                        new HashMap<>(Map.of("vendor_code", "V1", "vendor_name", "Globex")),
                        new HashMap<>(Map.of("vendor_code", "V2", "vendor_name", "Initech"))))))));
        rows.put("check-b", ProcessInstance.builder().id("check-b").processDefinitionKey("fu-vendor")
                .parentProcessInstanceId("purchase").callActivityId("PR_Call_ExtraVendors")
                .variables(new HashMap<>(Map.of("check_result", "QUALIFIED"))).build());
        when(engine.getProcessInstance("check-b")).thenReturn(Optional.of(Map.of("assignmentTracked",false,"variables", Map.of(
                "__callRow", Map.of("vendor_code", "V2", "vendor_name", "Initech")))));
        when(engine.getBpmnXml("fu-purchase", "purchase")).thenReturn(Optional.of("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="fu-purchase">
                    <bpmn:callActivity id="PR_Call_ExtraVendors" calledElement="fu-vendor">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="callRowsTable" value="extra_vendors"/>
                        <custom:property name="callOutputMapping"
                            value="[{&quot;from&quot;:&quot;check_result&quot;,&quot;to&quot;:&quot;row.check_status&quot;}]"/>
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>"""));
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status("completed", false)));
    }

    /**
     * Rows without the platform's id are recognised by the rows table's key as the caller's release
     * declares it — not as the designer declares it today, which may have changed the key since.
     */
    @Test
    @SuppressWarnings("unchecked")
    void rowsWithoutPlatformIdsAreMatchedByTheKeyOfTheCallersRelease() {
        rows.get("purchase").setFunctionUnitCatalogId(PURCHASE_RELEASE);
        perRowCallOnRowsWithoutPlatformIds();
        when(snapshots.contents(PURCHASE_RELEASE, "DATA_TABLE")).thenReturn(List.of(Map.of(
                "tableName", "extra_vendors", "fields", List.of(
                        Map.of("fieldName", "vendor_name", "isPrimaryKey", false, "sortOrder", 2),
                        Map.of("fieldName", "vendor_code", "isPrimaryKey", true, "sortOrder", 1)))));

        component.refreshCallers(status(
                "completed", true, "processInstanceId", "check-b", "superProcessInstanceId", "purchase"));

        List<Map<String, Object>> saved = (List<Map<String, Object>>) ((Map<String, Object>)
                rows.get("purchase").getVariables().get("__subTables__")).get("dw:extra_vendors");
        assertThat(saved.get(1)).containsEntry("check_status", "QUALIFIED");
        assertThat(saved.get(0)).doesNotContainKey("check_status");
    }

    /** A caller with no recorded release has no key to match rows by: nothing is written into its rows. */
    @Test
    void callerWithoutAPinnedReleaseGetsNoRowResult() {
        perRowCallOnRowsWithoutPlatformIds();

        component.refreshCallers(status(
                "completed", true, "processInstanceId", "check-b", "superProcessInstanceId", "purchase"));

        verify(repository, never()).save(any());
        verify(snapshots, never()).contents(anyString(), anyString());
    }
}

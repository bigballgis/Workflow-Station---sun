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
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
        when(engine.getSubProcesses(anyString())).thenReturn(List.of());
        hydration = mock(ProcessInstanceHydrationComponent.class);
        // Hydration from the engine: a called instance has no catalog pin and no start user.
        when(hydration.requireProcessInstance(anyString())).thenAnswer(inv -> rows.computeIfAbsent(
                inv.getArgument(0),
                id -> ProcessInstance.builder().id(id).processDefinitionKey("fu-vendor")
                        .functionUnitCode("").startUserId("system").status("RUNNING").build()));
        sync = mock(ProcessInstanceSyncComponent.class);
        jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        component = new CalledProcessSyncComponent(repository, engine, hydration, sync, jdbc);

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
        when(engine.getBpmnXml("fu-purchase")).thenReturn(Optional.of("""
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
        when(engine.getProcessInstance("purchase")).thenReturn(Optional.of(Map.of("variables", Map.of(
                "vendor_check_result", "QUALIFIED", "title", "stale engine copy"))));
        when(engine.getProcessInstanceStatus("purchase")).thenReturn(Optional.of(status("completed", false)));

        component.refreshCallers(status(
                "completed", true, "processInstanceId", "vendor-check", "superProcessInstanceId", "purchase"));

        Map<String, Object> vars = rows.get("purchase").getVariables();
        assertThat(vars).containsEntry("vendor_check_result", "QUALIFIED");
        // Only mapped fields come back; the portal's own values are not overwritten from the engine.
        assertThat(vars).containsEntry("title", "Laptops");
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
        when(engine.getProcessInstance("check-b")).thenReturn(Optional.of(Map.of("variables", Map.of(
                "__callRow", Map.of("platformRowUuid", "B", "vendor_name", "Initech")))));
        when(engine.getBpmnXml("fu-purchase")).thenReturn(Optional.of("""
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
}

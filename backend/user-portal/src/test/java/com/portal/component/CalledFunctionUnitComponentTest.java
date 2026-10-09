package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.CalledFunctionUnitInstance;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;

/**
 * Reading the Function Unit sub-processes a request started.
 *
 * <p>The case that matters most here is the negative one: a multi-instance sub-process is not a
 * Function Unit call, and must never be presented as one.
 */
@ExtendWith(MockitoExtension.class)
class CalledFunctionUnitComponentTest {

    private static final String PARENT_ID = "parent-1";

    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private WorkflowEngineClient workflowEngineClient;

    private CalledFunctionUnitComponent component;

    @BeforeEach
    void setUp() {
        component = new CalledFunctionUnitComponent(processInstanceRepository, workflowEngineClient);
    }

    private static ProcessInstance child(String id, String callActivityId, String status) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(id);
        instance.setParentProcessInstanceId(PARENT_ID);
        instance.setCallActivityId(callActivityId);
        instance.setStatus(status);
        instance.setProcessDefinitionKey("fu-vendor-check");
        instance.setProcessDefinitionName("Vendor Check");
        instance.setFunctionUnitCode("fu-vendor-check");
        instance.setStartTime(LocalDateTime.now());
        instance.setVariables(Map.of("result", "passed"));
        return instance;
    }

    @Test
    void returnsNothingForARequestThatCallsNothing() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of());
        when(workflowEngineClient.getSubProcesses(PARENT_ID)).thenReturn(List.of());

        assertThat(component.findCalledInstances(PARENT_ID)).isEmpty();
    }

    @Test
    void toleratesBlankParentId() {
        assertThat(component.findCalledInstances(null)).isEmpty();
        assertThat(component.findCalledInstances("  ")).isEmpty();
        verify(processInstanceRepository, never()).findByParentProcessInstanceId(anyString());
    }

    @Test
    void exposesTheChildInstanceAndItsData() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        when(processInstanceRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        List<CalledFunctionUnitInstance> out = component.findCalledInstances(PARENT_ID);

        assertThat(out).singleElement().satisfies(called -> {
            assertThat(called.getProcessInstanceId()).isEqualTo("child-1");
            assertThat(called.getCallActivityId()).isEqualTo("Call_1");
            assertThat(called.getFunctionUnitCode()).isEqualTo("fu-vendor-check");
            assertThat(called.getStatus()).isEqualTo("RUNNING");
            assertThat(called.getFormData()).containsEntry("result", "passed");
        });
    }

    /**
     * A multi-instance sub-process runs inside the calling instance and is reported by the engine
     * alongside real calls. Linking it would make the MI expansion appear as a called Function
     * Unit on the request page.
     */
    @Test
    void ignoresEmbeddedMultiInstanceSubProcesses() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of());
        when(workflowEngineClient.getSubProcesses(PARENT_ID)).thenReturn(List.of(
                Map.of("subProcessInstanceId", "exec-mi-1",
                        "embedded", Boolean.TRUE)));

        assertThat(component.findCalledInstances(PARENT_ID)).isEmpty();
        verify(processInstanceRepository, never()).save(any());
    }

    /** Same, for the other JSON spelling the engine DTO can produce. */
    @Test
    void ignoresEmbeddedSubProcessesUnderTheIsEmbeddedSpelling() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of());
        when(workflowEngineClient.getSubProcesses(PARENT_ID)).thenReturn(List.of(
                Map.of("subProcessInstanceId", "exec-mi-1",
                        "isEmbedded", Boolean.TRUE)));

        assertThat(component.findCalledInstances(PARENT_ID)).isEmpty();
        verify(processInstanceRepository, never()).save(any());
    }

    @Test
    void backfillsTheParentLinkFromTheEngineWhenItIsMissing() {
        ProcessInstance unlinked = child("child-1", null, "RUNNING");
        unlinked.setParentProcessInstanceId(null);

        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of());
        when(workflowEngineClient.getSubProcesses(PARENT_ID)).thenReturn(List.of(
                Map.of("subProcessInstanceId", "child-1", "callActivityId", "Call_1")));
        when(processInstanceRepository.findById("child-1")).thenReturn(Optional.of(unlinked));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(processInstanceRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        List<CalledFunctionUnitInstance> out = component.findCalledInstances(PARENT_ID);

        assertThat(out).singleElement()
                .satisfies(called -> assertThat(called.getCallActivityId()).isEqualTo("Call_1"));
        assertThat(unlinked.getParentProcessInstanceId()).isEqualTo(PARENT_ID);
        assertThat(unlinked.getCallActivityId()).isEqualTo("Call_1");
    }

    /** A child the portal has never recorded is skipped rather than invented from a partial payload. */
    @Test
    void skipsEngineChildrenWithNoPortalRow() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of());
        when(workflowEngineClient.getSubProcesses(PARENT_ID)).thenReturn(List.of(
                Map.of("subProcessInstanceId", "unknown-child", "callActivityId", "Call_1")));
        when(processInstanceRepository.findById("unknown-child")).thenReturn(Optional.empty());

        assertThat(component.findCalledInstances(PARENT_ID)).isEmpty();
        verify(processInstanceRepository, never()).save(any());
    }

    @Test
    void ordersChildrenOldestFirst() {
        ProcessInstance older = child("child-older", "Call_1", "COMPLETED");
        older.setStartTime(LocalDateTime.now().minusHours(2));
        ProcessInstance newer = child("child-newer", "Call_1", "RUNNING");
        newer.setStartTime(LocalDateTime.now());

        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(newer, older));
        when(processInstanceRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        assertThat(component.findCalledInstances(PARENT_ID))
                .extracting(CalledFunctionUnitInstance::getProcessInstanceId)
                .containsExactly("child-older", "child-newer");
    }

    private void parentCalls(String formProperty) {
        ProcessInstance parent = new ProcessInstance();
        parent.setId(PARENT_ID);
        parent.setProcessDefinitionKey("fu-purchase");
        when(processInstanceRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(workflowEngineClient.getBpmnXml("fu-purchase")).thenReturn(Optional.of("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="fu-purchase">
                    <bpmn:callActivity id="Call_1" name="Check Vendor" calledElement="fu-vendor-check">
                      <bpmn:extensionElements><custom:properties>%s</custom:properties></bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>""".formatted(formProperty)));
    }

    private static Map<String, Object> form(String name) {
        return Map.of("name", name, "data", "{\"rule\":[]}", "tableBindings", List.of(Map.of(
                "bindingType", "PRIMARY",
                "fieldDefinitions", List.of(Map.of("fieldName", "result", "displayName", "Result")))));
    }

    /** The layout is the one designed in the called unit: its configured form travels with the child. */
    @Test
    void deliversTheConfiguredFormOfTheCalledUnit() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING"), child("child-2", "Call_1", "RUNNING")));
        parentCalls("<custom:property name=\"childFormName\" value=\"Vendor Review Form\"/>");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContents("fu-vendor-check", "FORM"))
                .thenReturn(List.of(form("Other Form"), form("Vendor Review Form")));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        List<CalledFunctionUnitInstance> out = component.findCalledInstances(PARENT_ID);

        assertThat(out).allSatisfy(called -> {
            assertThat(called.getChildForm()).containsEntry("name", "Vendor Review Form");
            assertThat(called.getChildFields()).isNull();
        });
        // Loaded once for the page, not once per child.
        verify(processComponent, times(1)).getFunctionUnitContents("fu-vendor-check", "FORM");
    }

    /** No form configured: the called unit's main-table fields, under their designed names. */
    @Test
    void fallsBackToTheCalledUnitsMainTableFields() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContents("fu-vendor-check", "FORM")).thenReturn(List.of(form("Any")));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        CalledFunctionUnitInstance called = component.findCalledInstances(PARENT_ID).get(0);

        assertThat(called.getChildForm()).isNull();
        assertThat(called.getChildFields()).singleElement()
                .satisfies(f -> assertThat(f).containsEntry("fieldName", "result").containsEntry("displayName", "Result"));
    }
}

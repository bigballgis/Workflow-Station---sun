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
    /** The releases of the called unit that its children were started on. */
    private static final String VENDOR_RELEASE_1 = "11111111-0000-0000-0000-000000000001";
    private static final String VENDOR_RELEASE_2 = "11111111-0000-0000-0000-000000000002";

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
        instance.setFunctionUnitCatalogId(VENDOR_RELEASE_1);
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
        when(workflowEngineClient.getBpmnXml("fu-purchase", PARENT_ID)).thenReturn(Optional.of("""
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
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1))
                .thenReturn(Map.of("forms", List.of(form("Other Form"), form("Vendor Review Form"))));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        List<CalledFunctionUnitInstance> out = component.findCalledInstances(PARENT_ID);

        assertThat(out).allSatisfy(called -> {
            assertThat(called.getChildForm()).containsEntry("name", "Vendor Review Form");
            assertThat(called.getChildFields()).isNull();
        });
        // Loaded once for the page, not once per child.
        verify(processComponent, times(1)).getFunctionUnitContent(VENDOR_RELEASE_1);
    }

    /**
     * Each child is shown with the forms of the release it was started on, never the called unit's
     * latest one: a later release may rename or drop the fields an older child holds.
     */
    @Test
    void eachChildUsesTheFormsOfTheReleaseItWasStartedOn() {
        ProcessInstance onRelease1 = child("child-1", "Call_1", "COMPLETED");
        ProcessInstance onRelease2 = child("child-2", "Call_1", "RUNNING");
        onRelease2.setFunctionUnitCatalogId(VENDOR_RELEASE_2);
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(onRelease1, onRelease2));
        parentCalls("<custom:property name=\"childFormName\" value=\"Vendor Review Form\"/>");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1))
                .thenReturn(Map.of("forms", List.of(Map.of("name", "Vendor Review Form", "data", "{\"rule\":\"v1\"}"))));
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_2))
                .thenReturn(Map.of("forms", List.of(Map.of("name", "Vendor Review Form", "data", "{\"rule\":\"v2\"}"))));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        List<CalledFunctionUnitInstance> out = component.findCalledInstances(PARENT_ID);

        assertThat(out).extracting(called -> called.getChildForm().get("data"))
                .containsExactlyInAnyOrder("{\"rule\":\"v1\"}", "{\"rule\":\"v2\"}");
        assertThat(out).filteredOn(called -> "child-2".equals(called.getProcessInstanceId()))
                .singleElement().satisfies(called -> assertThat(called.getChildForm()).containsEntry("data", "{\"rule\":\"v2\"}"));
        verify(processComponent, never()).getFunctionUnitContent("fu-vendor-check");
    }

    /** A child with no recorded release cannot be shown with any unit's forms: that is a version error. */
    @Test
    void childWithoutAPinnedReleaseIsAVersionError() {
        ProcessInstance unpinned = child("child-1", "Call_1", "RUNNING");
        unpinned.setFunctionUnitCatalogId(null);
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID)).thenReturn(List.of(unpinned));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> component.findCalledInstances(PARENT_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        verify(processComponent, never()).getFunctionUnitContent(anyString());
    }

    /** No form configured: the called unit's main-table fields, under their designed names. */
    @Test
    void fallsBackToTheCalledUnitsMainTableFields() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1))
                .thenReturn(Map.of("forms", List.of(form("Any"))));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        CalledFunctionUnitInstance called = component.findCalledInstances(PARENT_ID).get(0);

        assertThat(called.getChildForm()).isNull();
        assertThat(called.getChildFields()).singleElement()
                .satisfies(f -> assertThat(f).containsEntry("fieldName", "result").containsEntry("displayName", "Result"));
    }

    /** A child whose release cannot be read is a version error on the page, not a card without fields. */
    @Test
    void childWhoseReleaseCannotBeReadIsAVersionError() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1)).thenThrow(
                new com.platform.common.functionunit.FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", VENDOR_RELEASE_1));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> component.findCalledInstances(PARENT_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
    }

    /** Admin Center unreachable: the child and its data are still listed, without the designed layout. */
    @Test
    void adminOutageListsTheChildWithoutItsLayout() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("<custom:property name=\"childFormName\" value=\"Vendor Review Form\"/>");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        CalledFunctionUnitInstance called = component.findCalledInstances(PARENT_ID).get(0);

        assertThat(called.getChildForm()).isNull();
        assertThat(called.getFormData()).containsEntry("result", "passed");
    }

    /** The child's release is gone from the catalog: a version error, not a card without fields. */
    @Test
    void releaseMissingFromTheCatalogIsAVersionError() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1)).thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Not Found", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> component.findCalledInstances(PARENT_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
    }

    /** Anything other than an outage (e.g. a broken MI assignment design) is not hidden as one. */
    @Test
    void otherFailuresAreNotHiddenAsAnOutage() {
        when(processInstanceRepository.findByParentProcessInstanceId(PARENT_ID))
                .thenReturn(List.of(child("child-1", "Call_1", "RUNNING")));
        parentCalls("");
        ProcessComponent processComponent = mock(ProcessComponent.class);
        when(processComponent.getFunctionUnitContent(VENDOR_RELEASE_1)).thenThrow(new IllegalStateException("broken design"));
        ReflectionTestUtils.setField(component, "processComponent", processComponent);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> component.findCalledInstances(PARENT_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}

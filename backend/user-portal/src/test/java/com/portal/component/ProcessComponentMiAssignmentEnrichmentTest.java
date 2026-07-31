package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.repository.FavoriteProcessRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessComponentMiAssignmentEnrichmentTest {

    @Mock private FavoriteProcessRepository favoriteProcessRepository;
    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock private WorkflowEngineClient workflowEngineClient;
    @Mock private ProcessDraftComponent processDraftComponent;
    @Mock private RestTemplate restTemplate;
    @Mock private I18nService i18nService;
    @Mock private ProcessStartComponent processStartComponent;
    @Mock private ProcessApplicationQueryComponent processApplicationQueryComponent;
    @Mock private SubTableEnrichmentComponent subTableEnrichmentComponent;

    @InjectMocks
    private ProcessComponent component;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://admin-center");
        when(functionUnitAccessComponent.resolveFunctionUnitId("FU-1")).thenReturn("101");
    }

    @Test
    void permissionCheckedAndInternalPathsReturnSameEnrichedCachedPayload() {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(responseWith(process("task-1", "participants", "both", "owner_id", "role_id")));

        Map<String, Object> checked = component.getFunctionUnitContent("user-1", "FU-1");
        Map<String, Object> internal = component.getFunctionUnitContent("FU-1");

        assertThat(checked).isSameAs(internal);
        assertThat(miAssignments(checked).get("participants"))
                .containsEntry("allowUser", true)
                .containsEntry("allowRole", true)
                .containsEntry("assigneeField", "owner_id")
                .containsEntry("roleField", "role_id")
                .doesNotContainKey("buField");
        verify(restTemplate, times(1)).getForObject(anyString(), org.mockito.ArgumentMatchers.eq(Map.class));
    }

    @Test
    void conflictingPayloadIsRejectedAndNotCached() {
        Map<String, Object> payload = Map.of(
                "name", "FU",
                "processes", List.of(
                        Map.of("data", process("task-1", "participants", "user", "owner_id", null)),
                        Map.of("data", process("task-2", "participants", "role", null, "role_id"))));
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(Map.of("success", true, "data", payload));

        assertThatThrownBy(() -> component.getFunctionUnitContent("user-1", "FU-1"))
                .isInstanceOf(BpmnMiXmlSupport.MiAssignmentConfigurationException.class)
                .hasMessageContaining("CONFLICTING_MI_ASSIGNMENT_CONFIG");
        assertThatThrownBy(() -> component.getFunctionUnitContent("user-1", "FU-1"))
                .isInstanceOf(BpmnMiXmlSupport.MiAssignmentConfigurationException.class);
        verify(restTemplate, times(2)).getForObject(anyString(), org.mockito.ArgumentMatchers.eq(Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> miAssignments(Map<String, Object> payload) {
        return (Map<String, Map<String, Object>>) payload.get("miAssignments");
    }

    private static Map<String, Object> responseWith(String bpmnXml) {
        return Map.of("success", true, "data", Map.of(
                "name", "FU",
                "processes", List.of(Map.of("data", bpmnXml))));
    }

    private static String process(
            String taskId, String subTableName, String mode, String assigneeField, String roleField) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="p"><bpmn:subProcess id="sp-%s">
                    <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"/>
                    <bpmn:userTask id="%s"><bpmn:extensionElements><custom:properties>
                      <custom:property name="subTableName" value="%s"/>
                      <custom:property name="assigneeMode" value="%s"/>
                      %s%s
                    </custom:properties></bpmn:extensionElements></bpmn:userTask>
                  </bpmn:subProcess></bpmn:process>
                </bpmn:definitions>
                """.formatted(
                taskId,
                taskId,
                subTableName,
                mode,
                assigneeField == null ? "" : property("assigneeField", assigneeField),
                roleField == null ? "" : property("roleField", roleField));
    }

    private static String property(String name, String value) {
        return "<custom:property name=\"" + name + "\" value=\"" + value + "\"/>";
    }
}

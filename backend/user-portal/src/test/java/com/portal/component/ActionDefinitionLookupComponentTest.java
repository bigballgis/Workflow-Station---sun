package com.portal.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionDefinitionLookupComponentTest {

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @InjectMocks
    private ActionDefinitionLookupComponent lookupComponent;

    @Test
    @DisplayName("resolveByIdsInOrder falls back to dw_action_definitions")
    void resolveByIdsInOrder_fallsBackToDesignerTable() {
        when(actionDefinitionRepository.findAllById(List.of("50419"))).thenReturn(List.of());
        when(actionDefinitionRepository.findFromDwByIds(List.of("50419"))).thenReturn(List.of(
                ActionDefinition.builder().id("50419").actionName("Submit").actionType("PROCESS_SUBMIT").build()));

        List<ActionDefinition> resolved = lookupComponent.resolveByIdsInOrder(List.of("50419"));

        assertEquals(1, resolved.size());
        assertEquals("50419", resolved.get(0).getId());
    }

    @Test
    @DisplayName("resolveByIdsInOrder deduplicates repeated ids like legacy TaskActionService")
    void resolveByIdsInOrder_deduplicatesRepeatedIds() {
        ActionDefinition action = ActionDefinition.builder().id("19").actionName("A").build();
        when(actionDefinitionRepository.findAllById(List.of("19"))).thenReturn(List.of(action));

        List<ActionDefinition> resolved = lookupComponent.resolveByIdsInOrder(List.of("19", "19"));

        assertEquals(1, resolved.size());
        assertEquals("19", resolved.get(0).getId());
    }

    // Releases deployed before runtime versioning: catalog actions have no source_id; the release's
    // own published BPMN pairs each designer actionId with its actionName.

    @Test
    @DisplayName("resolvePinned maps a legacy release's actions through its published BPMN, without modifying them")
    void resolvePinned_legacyReleaseResolvesThroughPublishedBpmn() {
        ActionDefinition approve = legacy("uuid-approve", "Approve Purchase");
        ActionDefinition reject = legacy("uuid-reject", "Reject Purchase");
        when(actionDefinitionRepository.findPinnedActions("v1", List.of("50401", "50400"))).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId("v1")).thenReturn(List.of(approve, reject));
        when(actionDefinitionRepository.findPublishedProcessXml("v1")).thenReturn(List.of(bpmn(
                task("Task_Submit", "[50393]", "[&#34;Submit Request&#34;]"),
                task("Task_Approve", "[50400,50401]", "[&#34;Approve Purchase&#34;,&#34;Reject Purchase&#34;]"))));

        List<ActionDefinition> resolved = lookupComponent.resolvePinned("v1", List.of("50401", "50400"));

        assertEquals(List.of("uuid-reject", "uuid-approve"), resolved.stream().map(ActionDefinition::getId).toList());
        assertEquals(List.of("50401", "50400"), resolved.stream().map(ActionDefinition::getSourceId).toList());
        assertNull(approve.getSourceId());
        assertNull(reject.getSourceId());
    }

    @Test
    @DisplayName("resolvePinned keeps refusing when the published ids and names do not pair up")
    void resolvePinned_legacyReleaseWithUnpairedNamesIsRefused() {
        when(actionDefinitionRepository.findPinnedActions("v1", List.of("50400"))).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId("v1")).thenReturn(List.of(
                legacy("uuid-approve", "Approve Purchase"), legacy("uuid-reject", "Reject Purchase")));
        when(actionDefinitionRepository.findPublishedProcessXml("v1")).thenReturn(List.of(bpmn(
                task("Task_Approve", "[50400,50401]", "[&#34;Approve Purchase&#34;]"))));

        assertThrows(FunctionUnitVersionException.class,
                () -> lookupComponent.resolvePinned("v1", List.of("50400")));
    }

    @Test
    @DisplayName("resolvePinned keeps refusing when two tasks name the same id differently")
    void resolvePinned_legacyReleaseWithConflictingNamesIsRefused() {
        when(actionDefinitionRepository.findPinnedActions("v1", List.of("50400"))).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId("v1")).thenReturn(List.of(
                legacy("uuid-approve", "Approve Purchase"), legacy("uuid-reject", "Reject Purchase")));
        when(actionDefinitionRepository.findPublishedProcessXml("v1")).thenReturn(List.of(bpmn(
                task("Task_A", "[50400]", "[&quot;Approve Purchase&quot;]"),
                task("Task_B", "[50400]", "[&quot;Reject Purchase&quot;]"))));

        assertThrows(FunctionUnitVersionException.class,
                () -> lookupComponent.resolvePinned("v1", List.of("50400")));
    }

    @Test
    @DisplayName("resolvePinned does not reinterpret a release whose actions all carry source ids")
    void resolvePinned_versionedReleaseStillRefusesUnknownIds() {
        when(actionDefinitionRepository.findPinnedActions("v1", List.of("50400"))).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId("v1")).thenReturn(List.of(
                ActionDefinition.builder().id("uuid-approve").sourceId("50399").actionName("Approve Purchase").build()));

        assertThrows(FunctionUnitVersionException.class,
                () -> lookupComponent.resolvePinned("v1", List.of("50400")));
        verify(actionDefinitionRepository, never()).findPublishedProcessXml("v1");
    }

    @Test
    @DisplayName("findPinned resolves a legacy release's action the same way, and reports an unknown id as absent")
    void findPinned_isTheNonThrowingSingleIdLookup() {
        ActionDefinition approve = legacy("uuid-approve", "Approve Purchase");
        when(actionDefinitionRepository.findPinnedActions(org.mockito.ArgumentMatchers.eq("v1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId("v1")).thenReturn(List.of(approve));
        when(actionDefinitionRepository.findPublishedProcessXml("v1")).thenReturn(List.of(bpmn(
                task("Task_Approve", "[50400]", "[&#34;Approve Purchase&#34;]"))));

        assertEquals("uuid-approve", lookupComponent.findPinned("v1", "50400").orElseThrow().getId());
        assertTrue(lookupComponent.findPinned("v1", "99").isEmpty());
    }

    private static ActionDefinition legacy(String id, String name) {
        return ActionDefinition.builder().id(id).functionUnitId("v1").actionName(name).actionType("APPROVE").build();
    }

    private static String bpmn(String... tasks) {
        return "<bpmn:definitions><bpmn:process id=\"p\">" + String.join("", tasks) + "</bpmn:process></bpmn:definitions>";
    }

    private static String task(String id, String actionIds, String actionNames) {
        return "<bpmn:userTask id=\"" + id + "\"><bpmn:extensionElements><custom_1:properties>"
                + "<custom_1:values name=\"actionIds\" value=\"" + actionIds + "\" />"
                + "<custom_1:values name=\"actionNames\" value=\"" + actionNames + "\" />"
                + "</custom_1:properties></bpmn:extensionElements></bpmn:userTask>";
    }
}

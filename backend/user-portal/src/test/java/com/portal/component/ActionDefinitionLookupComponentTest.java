package com.portal.component;

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
}

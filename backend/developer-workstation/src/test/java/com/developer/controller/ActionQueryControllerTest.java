package com.developer.controller;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code GET /actions/batch} names no unit: every unit the returned actions belong to must be visible (§6.1). */
@ExtendWith(MockitoExtension.class)
class ActionQueryControllerTest {

    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private FunctionUnitWorkspaceAccessService workspaceAccessService;

    @InjectMocks private ActionQueryController controller;

    @Test
    void batchGet_withAnActionOfAUnitTheCallerCannotSee_isDenied() {
        when(actionDefinitionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(action(1L, 10L), action(2L, 20L)));
        // lenient: unit 10 (visible) reaches the same method first
        lenient().doThrow(new FunctionUnitWorkspaceAccessDeniedException("denied"))
                .when(workspaceAccessService).assertCanAccess(20L, WorkspaceAccessAction.VIEW);

        assertThatThrownBy(() -> controller.batchGet("1,2"))
                .isInstanceOf(FunctionUnitWorkspaceAccessDeniedException.class);
    }

    @Test
    void batchGet_checksEachUnitOnce() {
        when(actionDefinitionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(action(1L, 10L), action(2L, 10L)));

        assertThat(controller.batchGet("1, 2").getBody().getData()).hasSize(2);
        verify(workspaceAccessService, times(1)).assertCanAccess(10L, WorkspaceAccessAction.VIEW);
    }

    private static ActionDefinition action(Long id, Long unitId) {
        return ActionDefinition.builder().id(id).functionUnit(FunctionUnit.builder().id(unitId).build()).build();
    }
}

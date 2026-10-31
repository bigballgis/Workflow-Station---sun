package com.workflow.assignment;

import com.platform.common.assignment.AssignmentStartContext;
import com.workflow.component.ProcessStartDefinitionResolver;
import com.workflow.controller.AssignmentStartContextController;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentStartContextControllerTest {
    private final ProcessStartDefinitionResolver definitions=mock(ProcessStartDefinitionResolver.class);
    private final AssignmentDefinitionPolicy policies=mock(AssignmentDefinitionPolicy.class);
    private final AssignmentStartContextController controller=new AssignmentStartContextController(definitions,policies);

    @Test void unauthenticatedRequestCannotResolveDefinitionsOrReadPolicies() {
        ReflectionTestUtils.setField(controller,"token","synthetic-start-token");
        assertThrows(RuntimeException.class,()->controller.read("wrong",new AssignmentStartContext.Request("flow",null)));
        verifyNoInteractions(definitions,policies);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void contextUsesTheResolvedDefinitionAndEchoesTheRequestedPin(boolean tracked) {
        ReflectionTestUtils.setField(controller,"token","synthetic-start-token");
        var definition=mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("resolved-id"); when(definition.getKey()).thenReturn("flow");
        when(definitions.resolve("flow","requested-id")).thenReturn(definition);
        when(policies.tracked("resolved-id")).thenReturn(tracked);
        var result=controller.read("synthetic-start-token",new AssignmentStartContext.Request("flow","requested-id")).getData();
        assertEquals(new AssignmentStartContext(1,"resolved-id","flow",tracked,"requested-id"),result);
    }

    @Test void failedDefinitionResolutionDoesNotGuessLegacy() {
        ReflectionTestUtils.setField(controller,"token","synthetic-start-token");
        when(definitions.resolve("flow",null)).thenThrow(new IllegalStateException("synthetic unavailable definition"));
        assertThrows(IllegalStateException.class,()->controller.read("synthetic-start-token",new AssignmentStartContext.Request("flow",null)));
        verifyNoInteractions(policies);
    }
}

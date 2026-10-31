package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.workflow.component.ProcessDeploymentManager;
import com.workflow.component.impl.ProcessStartComponentImpl;
import com.workflow.dto.request.StartProcessRequest;
import org.flowable.engine.*;
import org.flowable.engine.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentStartPinContextTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }
    @Test void aChangedLatestDefinitionIsRejectedBeforeQualificationOrAnyWorkflowStart() {
        var repository=mock(RepositoryService.class); var query=mock(ProcessDefinitionQuery.class,RETURNS_SELF);
        var definition=mock(ProcessDefinition.class); when(definition.getId()).thenReturn("newer-definition");
        when(repository.createProcessDefinitionQuery()).thenReturn(query); when(query.singleResult()).thenReturn(definition);
        var runtime=mock(RuntimeService.class); var starts=mock(AssignmentRootStarter.class);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var component=new ProcessStartComponentImpl(repository,runtime,mock(ProcessDeploymentManager.class),starts,manager);
        var request=new MockHttpServletRequest(); request.addHeader(AssignmentStartContext.HEADER,"previous-definition");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var input=new StartProcessRequest(); input.setProcessDefinitionKey("process");
        var failure=assertThrows(AssignmentFailure.class,()->component.startProcess(input));
        assertEquals("ASSIGNMENT_STATE_CHANGED",failure.getDetails().get("reason"));
        verifyNoInteractions(runtime,starts,manager);
    }
    @Test void anExactOrAbsentPreflightIsOnlyAnAssertionAndDoesNotGrantAuthority() {
        assertDoesNotThrow(()->AssignmentStartPinContext.requireDefinition("exact"));
        var request=new MockHttpServletRequest(); request.addHeader(AssignmentStartContext.HEADER,"exact");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertDoesNotThrow(()->AssignmentStartPinContext.requireDefinition("exact"));
        assertThrows(AssignmentFailure.class,()->AssignmentStartPinContext.requireDefinition("other"));
    }
}

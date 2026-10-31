package com.portal.client;

import com.platform.common.assignment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentStartCoordinationClientTest {
    final AssignmentEngineTransport engine=mock(AssignmentEngineTransport.class);
    final AssignmentCoordinationVerifier coordination=mock(AssignmentCoordinationVerifier.class);
    final AssignmentStartCoordinationClient client=new AssignmentStartCoordinationClient(engine,coordination);
    @Test void protectedGraphRequiresTheLocalPhysicalProofBeforeItsDefinitionCanBeStarted() {
        var context=new AssignmentStartContext(1,"exact-definition","process",true,"explicit-pin");
        reply(context); assertEquals(context,client.prepare("process","explicit-pin"));
        verify(coordination).requireSamePrimary();
    }
    @Test void positivelyLegacyGraphDoesNotRequireThePhysicalProbe() {
        var context=new AssignmentStartContext(1,"exact-definition","process",false,null);
        reply(context); assertEquals(context,client.prepare("process",null)); verifyNoInteractions(coordination);
    }
    @Test void aFailedProofCannotBeIgnoredAfterAPositiveProtectedClassification() {
        reply(new AssignmentStartContext(1,"exact-definition","process",true,null));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(coordination).requireSamePrimary();
        assertThrows(AssignmentFailure.class,()->client.prepare("process",null));
    }
    @ParameterizedTest @MethodSource("invalid")
    void absentOrMisboundContextNeverMeansLegacy(AssignmentStartContext context) {
        reply(context); assertThrows(AssignmentFailure.class,()->client.prepare("process",null)); verifyNoInteractions(coordination);
    }
    static Stream<AssignmentStartContext> invalid() {
        return Stream.of(null,new AssignmentStartContext(0,"id","process",false,null),new AssignmentStartContext(1,"id","other",false,null),
                new AssignmentStartContext(1,"id","process",null,null),new AssignmentStartContext(1,"","process",false,null),
                new AssignmentStartContext(1,"id","process",false,"someone-elses-pin"));
    }
    void reply(AssignmentStartContext context) {
        when(engine.post(eq(List.of("start-context")),any(AssignmentStartContext.Request.class),any(),eq(false))).thenReturn(context);
    }
}

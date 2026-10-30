package com.portal.client;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

/** Pins the actual immutable graph before deciding whether physical coordination is required. */
@Component
public class AssignmentStartCoordinationClient {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentStartContext>> TYPE=new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    private final AssignmentCoordinationVerifier coordination;
    public AssignmentStartCoordinationClient(AssignmentEngineTransport engine,AssignmentCoordinationVerifier coordination) {
        this.engine=engine; this.coordination=coordination;
    }
    public AssignmentStartContext prepare(String key,String expected) {
        var context=engine.post(List.of("start-context"),new AssignmentStartContext.Request(key,expected),TYPE,false);
        if (context==null || context.contractVersion()!=1 || context.tracked()==null || !Objects.equals(key,context.definitionKey())
                || !Objects.equals(expected,context.requestedDefinitionId()) || context.definitionId()==null || context.definitionId().isBlank()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        if (context.tracked()) coordination.requireSamePrimary();
        return context;
    }
}

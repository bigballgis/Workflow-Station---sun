package com.admin.config;

import com.platform.common.assignment.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class AssignmentCoordinationConfiguration {
    @Bean @Lazy
    public AssignmentCoordinationVerifier assignmentCoordinationVerifier(AssignmentCoordinationProofRepository repository,
            @Value("${workflow-engine.url:http://localhost:8081}") String origin,@Value("${service.internal-token:}") String token) {
        return new AssignmentCoordinationVerifier(repository,AssignmentPublicationCapability.newTransport(),origin,token,
                AssignmentCoordinationProof.Participant.ADMIN_CENTER);
    }
}

package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
import com.portal.repository.AssignmentDelegationSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

/** A short local qualification snapshot. No Engine or Admin HTTP call runs under its read lock. */
@Component
public class AssignmentDelegationSnapshotComponent {
    private final AssignmentDelegationSnapshotRepository repository;
    private final ObjectMapper json;
    private final TransactionTemplate read;
    public AssignmentDelegationSnapshotComponent(AssignmentDelegationSnapshotRepository repository, ObjectMapper json, PlatformTransactionManager transactions) {
        this.repository=repository; this.json=json;
        read=new TransactionTemplate(transactions);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read.setReadOnly(true);
    }
    public AssignmentDelegationSnapshot read(AssignmentDelegationSnapshot.Request request) {
        return read.execute(status -> snapshot(request));
    }
    private AssignmentDelegationSnapshot snapshot(AssignmentDelegationSnapshot.Request request) {
        var epoch=repository.lockSnapshot();
        var mirror=repository.mirror(request);
        Instant deadline=epoch.now().plusSeconds(30);
        for (var rule:repository.active(request,epoch.now())) {
            if (matches(rule.processes(),mirror.process()) && matches(rule.priorities(),mirror.priority())) {
                Instant expiry=rule.end()!=null && rule.end().isBefore(deadline)?rule.end():deadline;
                return new AssignmentDelegationSnapshot(1,request,epoch.revision(),true,rule.id(),expiry);
            }
        }
        return new AssignmentDelegationSnapshot(1,request,epoch.revision(),false,null,deadline);
    }
    private boolean matches(String filter,String value) {
        if (filter==null) return true; // SQL NULL means no configured filter.
        try {
            var values=json.readTree(filter);
            if (!values.isArray()) throw unavailable();
            boolean found=false;
            for (var item:values) {
                if (!item.isTextual() || item.asText().isBlank()) throw unavailable();
                if (item.asText().equals(value)) found=true;
            }
            return values.isEmpty() || found;
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}

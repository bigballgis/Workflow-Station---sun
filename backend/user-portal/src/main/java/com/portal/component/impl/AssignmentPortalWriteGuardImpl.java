package com.portal.component.impl;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.portal.client.AssignmentEngineTransport;
import com.portal.component.AssignmentPortalWriteGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

@Component
public class AssignmentPortalWriteGuardImpl implements AssignmentPortalWriteGuard {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentWriteDecision>> DECISION = new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    private final JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entities;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private AssignmentCoordinationVerifier coordination;
    public AssignmentPortalWriteGuardImpl(AssignmentEngineTransport engine, JdbcTemplate jdbc) { this.engine = engine; this.jdbc = jdbc; }

    @Override public AssignmentWriteDecision prepare(Request request) {
        var decision = engine.post(List.of("write-decisions"), new AssignmentWriteDecision.Request(request.taskId(), request.actor(),
                request.representedUser(), request.operation(), request.digest(), request.instanceId()), DECISION, false);
        verify(decision, request);
        if (decision.tracked()) com.portal.security.AssignmentPortalActor.require(request.actor());
        if (decision.tracked() && TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        if (decision.tracked()) coordination.requireSamePrimary();
        return decision;
    }

    @Override public void enter(AssignmentWriteDecision decision, Request request) {
        verify(decision, request);
        if (!decision.tracked()) return;
        com.portal.security.AssignmentPortalActor.require(request.actor());
        coordination.requireVerified();
        var state = new AssignmentCaseFence(jdbc).locked(decision.caseId());
        if (state.synchronizationPending()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_PENDING);
        var authority = new AssignmentAuthorityFence(jdbc);
        if (state.revision() != decision.caseRevision() || state.portalRevision() != decision.portalRevision()
                || authority.revision() != decision.authorityRevision() || !authority.currentTime().isBefore(decision.validUntil())) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        // OSIV may retain entities read before preparation. Business writes must reload them after the final fence.
        entities.clear();
    }

    @Override public void written(AssignmentWriteDecision decision, boolean changesCandidates) {
        if (decision.tracked()) new AssignmentCaseFence(jdbc).portalWritten(decision.caseId(), decision.portalRevision(), changesCandidates);
    }

    private void verify(AssignmentWriteDecision decision, Request request) {
        if (decision == null || decision.contractVersion() != 1 || !request.taskId().equals(decision.taskId())
                || request.instanceId()==null || !request.instanceId().equals(decision.instanceId())
                || !request.actor().equals(decision.actor()) || !request.operation().equals(decision.operation())
                || !Objects.equals(request.representedUser(), decision.representedUser()) || !request.digest().equals(decision.requestDigest())
                || decision.tracked() && (decision.caseId() == null || decision.validUntil() == null || decision.caseRevision() < 0
                        || decision.portalRevision() < 0 || decision.authorityRevision() < 0)) throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}

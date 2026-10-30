package com.portal.component.impl;

import com.platform.common.assignment.*;
import com.portal.component.AssignmentReopenMirror;
import com.portal.component.ProcessTerminalStatusResolver;
import com.portal.component.ProcessVersionContext;
import com.portal.entity.ProcessInstance;
import com.portal.repository.AssignmentReopenMirrorRepository;
import com.portal.repository.ProcessInstanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** The local mirror and its retry ledger commit together; an existing round is never replaced by an old initial snapshot. */
@Component
public class AssignmentReopenMirrorImpl implements AssignmentReopenMirror {
    private final ProcessInstanceRepository instances;
    private final AssignmentReopenMirrorRepository ledger;
    private final JdbcTemplate jdbc;
    private final ProcessTerminalStatusResolver terminal;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private AssignmentCoordinationVerifier coordination;
    public AssignmentReopenMirrorImpl(ProcessInstanceRepository instances, AssignmentReopenMirrorRepository ledger,
            JdbcTemplate jdbc, ProcessTerminalStatusResolver terminal) {
        this.instances = instances; this.ledger = ledger; this.jdbc = jdbc; this.terminal = terminal;
    }

    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void apply(AssignmentReopenReceipt receipt) {
        coordination.requireVerified();
        new AssignmentCaseFence(jdbc).locked(receipt.caseId());
        var applied = ledger.find(receipt.operationId());
        if (applied.isPresent()) {
            if (!applied.get().caseId().equals(receipt.caseId()) || !applied.get().source().equals(receipt.sourceInstanceId())
                    || !applied.get().instance().equals(receipt.processInstanceId())) throw conflict();
            if (!instances.existsById(receipt.processInstanceId())) throw conflict();
            return;
        }
        var source = instances.findById(receipt.sourceInstanceId()).orElseThrow(this::conflict);
        if (source.getParentProcessInstanceId() != null || !receipt.processDefinitionId().equals(source.getProcessDefinitionId())
                || !source.getStartUserId().equals(receipt.variables().get("initiator"))
                || instances.existsById(receipt.processInstanceId())) throw conflict();
        var mirror = ProcessInstance.builder().id(receipt.processInstanceId()).processInstanceId(receipt.processInstanceId())
                .processDefinitionId(receipt.processDefinitionId()).processDefinitionKey(source.getProcessDefinitionKey())
                .processDefinitionName(source.getProcessDefinitionName()).businessKey(source.getBusinessKey()).title(source.getTitle())
                .startUserId(source.getStartUserId()).startUserName(source.getStartUserName()).initiatorId(source.getInitiatorId())
                .priority(source.getPriority()).functionUnitVersionId(source.getFunctionUnitVersionId())
                .functionUnitCatalogId(source.getFunctionUnitCatalogId()).functionUnitCode(source.getFunctionUnitCode())
                .functionUnitVersionLabel(source.getFunctionUnitVersionLabel()).variables(new java.util.LinkedHashMap<>(receipt.variables())).build();
        ProcessVersionContext.apply(mirror, mirror.getVariables());
        mirror.setStatus(switch (receipt.processState()) {
            case "ACTIVE" -> ProcessTerminalStatusResolver.STATUS_RUNNING;
            case "COMPLETED" -> terminal.resolveFinishedStatus(mirror);
            case "CANCELLED" -> ProcessTerminalStatusResolver.STATUS_WITHDRAWN;
            default -> throw conflict();
        });
        if (!ProcessTerminalStatusResolver.STATUS_RUNNING.equals(mirror.getStatus())) {
            mirror.setEndTime(java.time.LocalDateTime.now()); mirror.setCompletedAt(mirror.getEndTime());
        }
        instances.saveAndFlush(mirror);
        ledger.record(receipt);
        new AssignmentCaseFence(jdbc).portalReopenApplied(receipt.caseId(), receipt.operationId());
    }
    private AssignmentFailure conflict() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
}

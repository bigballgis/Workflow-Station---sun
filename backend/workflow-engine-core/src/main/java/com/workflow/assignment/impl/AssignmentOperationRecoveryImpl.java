package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentOperationReceipt;
import com.workflow.assignment.AssignmentOperationRecovery;
import com.workflow.assignment.AssignmentOperationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AssignmentOperationRecoveryImpl implements AssignmentOperationRecovery {
    private final AssignmentOperationRepository operations;
    public AssignmentOperationRecoveryImpl(AssignmentOperationRepository operations) { this.operations = operations; }
    @Override public Optional<AssignmentOperationReceipt> find(UUID id) { return operations.find(id); }
    @Override public Optional<AssignmentOperationReceipt> byTask(String task) { return operations.byTask(task); }
    @Override public List<AssignmentOperationReceipt> pending(int limit) { return operations.pending(limit); }
    @Override @Transactional public void confirm(UUID id, String digest) { operations.confirm(id, digest); }
    @Override @Transactional public void retry(UUID id, boolean conflict) { operations.retry(id, conflict); }
}

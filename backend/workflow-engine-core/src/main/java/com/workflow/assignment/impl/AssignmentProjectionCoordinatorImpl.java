package com.workflow.assignment.impl;

import com.workflow.assignment.*;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Refreshes a projection in its own successful transaction, independent of any denied user request. */
@Component
public class AssignmentProjectionCoordinatorImpl implements AssignmentProjectionCoordinator {
    private final TaskService tasks;
    private final AssignmentCaseEvaluator evaluator;
    private final AssignmentTaskStateRepository states;
    private final ExtendedTaskInfoRepository extended;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AssignmentProjectionCoordinatorImpl(TaskService tasks, AssignmentCaseEvaluator evaluator,
            AssignmentTaskStateRepository states, ExtendedTaskInfoRepository extended, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.tasks = tasks; this.evaluator = evaluator; this.states = states; this.extended = extended; this.jdbc = jdbc;
    }

    @Override @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void apply(AssignmentCaseEvaluator.Decision decision) {
        evaluator.checkCurrent(decision);
        var task = tasks.createTaskQuery().taskId(decision.taskId()).singleResult();
        if (task == null) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        var state = states.find(task.getId()).orElseThrow();
        String holder = task.getAssignee();
        boolean backupHolder = holder != null && "BACKUP".equals(state.holderSource()) && decision.qualification().backup().contains(holder);
        boolean qualified = holder != null && (decision.qualification().primary().contains(holder) || backupHolder);
        boolean release = decision.policy().enabled() && holder != null && (decision.excluded().contains(holder) || !qualified);
        long revision = decision.caseRevision();
        if (release) {
            AssignmentProjectionScope.releasing(task.getId());
            tasks.unclaim(task.getId());
            states.changed(decision.caseId());
            revision++;
            states.released(task.getId(), holder, revision);
            holder = null;
        }
        if (decision.policy().enabled()) {
            candidates(task.getId(), decision.pool().users());
            var info = extended.findByTaskIdAndIsDeletedFalse(task.getId()).orElseThrow();
            info.setAssignmentType(AssignmentType.CANDIDATE_USERS);
            info.setAssignmentTarget(String.join(",", decision.pool().users()));
            info.setClaimedBy(holder);
            if (holder == null) info.setClaimedTime(null);
            if (release) info.clearDelegation();
            extended.save(info);
        }
        states.projection(new AssignmentTaskStateRepository.Projection(task.getId(), decision.qualification().primary(),
                decision.pool().users(), decision.pool().source(), decision.policy().enabled() && holder == null && decision.pool().users().isEmpty() ? "BLOCKED_NO_ELIGIBLE_USER" : "ACTIVE",
                revision, decision.qualification().authorityRevision(), decision.qualification().nextEvaluationAt()));
        states.reconciled(decision.caseId(), revision, decision.qualification().authorityRevision());
        states.attempted(decision.caseId());
    }

    @Override @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void retireMissing(String taskId) {
        var state = states.find(taskId).orElseThrow();
        new com.platform.common.assignment.AssignmentCaseFence(jdbc).locked(state.caseId());
        if (tasks.createTaskQuery().taskId(taskId).singleResult() != null) return;
        if (states.find(taskId).orElseThrow().active()) {
            states.cancelledTask(taskId);
            states.changed(state.caseId());
        }
    }

    private void candidates(String taskId, List<String> users) {
        Set<String> current = new HashSet<>();
        for (var link : List.copyOf(tasks.getIdentityLinksForTask(taskId))) {
            if (!"candidate".equals(link.getType())) continue;
            if (link.getGroupId() != null) tasks.deleteCandidateGroup(taskId, link.getGroupId());
            if (link.getUserId() != null) current.add(link.getUserId());
        }
        for (String user : current) if (!users.contains(user)) tasks.deleteCandidateUser(taskId, user);
        for (String user : users) if (!current.contains(user)) tasks.addCandidateUser(taskId, user);
    }
}

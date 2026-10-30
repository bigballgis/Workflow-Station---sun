package com.workflow.assignment.impl;

import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.*;
import com.workflow.enums.AssignmentType;
import com.workflow.listener.TaskAssignmentNotifications;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Final assignment replaces all implicit group links before any creation notification can escape. */
@Component
public class AssignmentCreationCoordinatorImpl implements AssignmentCreationCoordinator {
    private final TaskService tasks;
    private final AssignmentCaseEvaluator evaluator;
    private final AssignmentTaskStateRepository states;
    private final ExtendedTaskInfoRepository extended;
    private final NotificationDispatchHelper notifications;
    @org.springframework.beans.factory.annotation.Autowired
    private AssignmentDefinitionPolicy definitions;

    public AssignmentCreationCoordinatorImpl(@Lazy TaskService tasks, AssignmentCaseEvaluator evaluator,
            AssignmentTaskStateRepository states, ExtendedTaskInfoRepository extended, NotificationDispatchHelper notifications) {
        this.tasks = tasks; this.evaluator = evaluator; this.states = states;
        this.extended = extended; this.notifications = notifications;
    }

    @Override
    public void created(Task task) {
        var links = List.copyOf(tasks.getIdentityLinksForTask(task.getId()));
        var original = new LinkedHashSet<String>();
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) original.add(task.getAssignee());
        links.stream().filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
                .forEach(link -> original.add(link.getUserId()));
        var source = AssignmentCreationBuffer.source(task.getId(), List.copyOf(original), task.getAssignee() == null);
        var node=(org.flowable.bpmn.model.UserTask) definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        var nativeSource=AssignmentNativeSource.dynamic(node);
        if (nativeSource!=null) source=nativeSource;
        if (source.kind()==AssignmentSourceSpec.Kind.FIXED && com.workflow.util.BpmnExtensionUtils.getExtensionProperty(node,"assigneeType")!=null) {
            throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        var decision = evaluator.prepare(task, source, List.of());
        evaluator.checkCurrent(decision);
        if (source.kind() == AssignmentSourceSpec.Kind.FIXED) {
            source = AssignmentSourceSpec.fixedCanonical(decision.qualification().primaryIdentities(), source.claimPool());
        }
        states.source(task.getId(), source);
        if (!decision.policy().enabled() && links.stream().anyMatch(link -> "candidate".equals(link.getType()) && link.getGroupId()!=null)) {
            // Responsibility tracking does not change an unconfigured node's original group authorization.
            states.projection(new AssignmentTaskStateRepository.Projection(task.getId(),decision.qualification().primary(),
                    decision.pool().users(),decision.pool().source(),"ACTIVE",decision.caseRevision(),
                    decision.qualification().authorityRevision(),decision.qualification().validUntil()));
            AssignmentCreationBuffer.finish(task.getId(),Set.copyOf(original));
            return;
        }
        boolean claim = decision.qualification().requiresClaim() || decision.pool().source() == AssignmentExclusionEvaluator.Source.BACKUP;
        String holder = !claim && decision.pool().users().size() == 1 ? decision.pool().users().get(0) : null;
        for (var link : links) {
            if (!"candidate".equals(link.getType())) continue;
            if (link.getUserId() != null) tasks.deleteCandidateUser(task.getId(), link.getUserId());
            if (link.getGroupId() != null) tasks.deleteCandidateGroup(task.getId(), link.getGroupId());
        }
        if (!Objects.equals(task.getAssignee(), holder)) tasks.setAssignee(task.getId(), holder);
        if (holder == null) decision.pool().users().forEach(user -> tasks.addCandidateUser(task.getId(), user));
        if (holder != null) states.held(task.getId(), decision.pool().source());
        states.projection(new AssignmentTaskStateRepository.Projection(task.getId(), decision.qualification().primary(),
                decision.pool().users(), decision.pool().source(), decision.pool().users().isEmpty() ? "BLOCKED_NO_ELIGIBLE_USER" : "ACTIVE",
                decision.caseRevision(), decision.qualification().authorityRevision(), decision.qualification().validUntil()));
        synchronizeExtended(task, holder, decision.pool().users());
        // Discard source-stage notices, including excluded recipients; publish only the final canonical assignment.
        AssignmentCreationBuffer.finish(task.getId(), Set.of());
        for (String user : decision.pool().users()) TaskAssignmentNotifications.send(notifications,
                new TaskAssignmentNotifications.Notice(user, task.getId(), task.getName(), task.getProcessInstanceId(), holder == null));
    }

    private void synchronizeExtended(Task task, String holder, List<String> candidates) {
        // Native/preassigned user tasks have no platform assignment row yet; initialize that row explicitly.
        var info = extended.findByTaskIdAndIsDeletedFalse(task.getId()).orElseGet(() ->
                com.workflow.entity.ExtendedTaskInfo.builder().taskId(task.getId())
                        .processInstanceId(task.getProcessInstanceId()).processDefinitionId(task.getProcessDefinitionId())
                        .taskDefinitionKey(task.getTaskDefinitionKey()).taskName(task.getName()).formKey(task.getFormKey())
                        .status("CREATED").createdTime(java.time.LocalDateTime.ofInstant(task.getCreateTime().toInstant(),
                                java.time.ZoneId.systemDefault())).build());
        info.setAssignmentType(holder == null ? AssignmentType.CANDIDATE_USERS : AssignmentType.USER);
        info.setAssignmentTarget(holder == null ? String.join(",", candidates) : holder);
        info.setClaimedBy(null);
        info.setClaimedTime(null);
        extended.save(info);
    }
}

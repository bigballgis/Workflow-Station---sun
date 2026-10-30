package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.workflow.assignment.*;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Additional responsibility veto; existing workspace and operation permissions remain mandatory. */
@Component
public class AssignmentWriteGuardImpl implements AssignmentWriteGuard {
    private final TaskService tasks;
    private final AssignmentCaseEvaluator evaluator;
    private final AssignmentTaskStateRepository states;
    private final ExtendedTaskInfoRepository extended;
    private final AssignmentDelegationResolver delegation;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private AssignmentLegacyTaskCheck legacyCheck;

    public AssignmentWriteGuardImpl(TaskService tasks, AssignmentCaseEvaluator evaluator, AssignmentTaskStateRepository states,
            ExtendedTaskInfoRepository extended, AssignmentDelegationResolver delegation) {
        this.tasks = tasks; this.evaluator = evaluator; this.states = states; this.extended = extended; this.delegation = delegation;
    }

    @Override public Optional<Prepared> prepare(Command command) {
        return prepare(command,false);
    }
    @Override public Optional<Prepared> prepareHandoff(String task,String actor) {
        var state=states.find(task);
        if (state.isEmpty() || !state.get().active() || state.get().handoffConsumed() || !actor.equals(state.get().releasedHolder())) return Optional.empty();
        return prepare(new Command(task,AssignmentMutationScope.Operation.HANDOFF,actor,null,null),true);
    }
    private Optional<Prepared> prepare(Command command,boolean handoffRead) {
        var state = states.find(command.taskId());
        if (state.isEmpty()) { legacyCheck.requireLegacy(command.taskId()); return Optional.empty(); }
        if (!state.get().active() || state.get().sourceSpec() == null) throw changed();
        Task task = requireTask(command.taskId());
        var info = extended.findByTaskIdAndIsDeletedFalse(command.taskId());
        Holding holding = holding(task, info);
        var refs = new ArrayList<AssignmentIdentitySnapshot.Reference>();
        refs.add(reference(command.actor()));
        if (holding.holder() != null) refs.add(reference(holding.holder()));
        if (command.target() != null) refs.add(reference(command.target()));
        var decision = evaluator.prepare(task, state.get().sourceSpec(), List.copyOf(refs));
        // Resolve delegation after the prepared epoch: final validation then detects every intervening revocation.
        var grant = delegation.resolve(task, command, holding, decision);
        decision = limitValidity(decision, grant.validUntil());
        if (handoffRead) {
            if (decision.pool().users().isEmpty()) return Optional.empty();
            command=new Command(command.taskId(),command.operation(),command.actor(),null,decision.pool().users().get(0));
        }
        validateUsers(command, holding, decision, grant.delegated());
        return Optional.of(new Prepared(command, decision, holding, grant.delegated()));
    }

    @Override public AssignmentMutationScope.Authorized validate(Prepared prepared) {
        evaluator.checkCurrent(prepared.decision());
        var task = requireTask(prepared.command().taskId());
        var current = holding(task, extended.findByTaskIdAndIsDeletedFalse(task.getId()));
        if (!current.equals(prepared.holding())) throw changed();
        validateUsers(prepared.command(), current, prepared.decision(), prepared.verifiedDelegate());
        var responsible = new LinkedHashSet<String>();
        responsible.add(prepared.command().actor());
        if (prepared.verifiedDelegate()) responsible.add(current.holder());
        var holders = new LinkedHashSet<>(prepared.decision().pool().users());
        if (current.holder() != null) holders.add(current.holder());
        return new AssignmentMutationScope.Authorized(task.getId(), prepared.command().operation(), prepared.command().actor(), responsible, holders);
    }

    private void validateUsers(Command command, Holding holding, AssignmentCaseEvaluator.Decision decision, boolean delegate) {
        if ((command.operation() == AssignmentMutationScope.Operation.ASSIGN || command.operation()==AssignmentMutationScope.Operation.REASSIGN)
                && command.target() == null) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        }
        active(command.actor(), decision);
        if (command.operation()==AssignmentMutationScope.Operation.HANDOFF) {
            handoff(command,holding,decision);
            return;
        }
        veto(command.actor(), decision);
        if (command.target() != null) {
            active(command.target(), decision);
            veto(command.target(), decision);
            if (decision.policy().enabled() && command.operation() != AssignmentMutationScope.Operation.DELEGATE
                    && !decision.pool().users().contains(command.target())) throw denied();
        }
        if (command.operation() == AssignmentMutationScope.Operation.UNCLAIM
                || command.operation()==AssignmentMutationScope.Operation.REASSIGN) return; // Existing management checks govern release and reassignment.
        if (holding.holder() != null) {
            active(holding.holder(), decision);
            veto(holding.holder(), decision);
            var state = states.find(command.taskId()).orElseThrow(this::changed);
            boolean backup = "BACKUP".equals(state.holderSource()) && decision.qualification().backup().contains(holding.holder());
            if (decision.policy().enabled() && !decision.qualification().primary().contains(holding.holder()) && !backup) throw denied();
            if (!command.actor().equals(holding.holder()) && !delegate) throw denied();
            if (command.operation() == AssignmentMutationScope.Operation.CLAIM) throw changed();
        } else if (decision.policy().enabled() && !decision.pool().users().contains(command.actor())) {
            throw denied();
        } else if (decision.policy().enabled() && decision.pool().source()==AssignmentExclusionEvaluator.Source.BACKUP
                && command.operation()!=AssignmentMutationScope.Operation.CLAIM
                && command.operation()!=AssignmentMutationScope.Operation.ASSIGN) {
            throw denied(); // Backup participation always begins with an explicit claim or authorized assignment.
        }
    }

    private void handoff(Command command,Holding holding,AssignmentCaseEvaluator.Decision decision) {
        var state=states.find(command.taskId()).orElseThrow(this::changed);
        if (holding.holder()!=null || state.handoffConsumed() || !command.actor().equals(state.releasedHolder())
                || command.target()==null || !decision.pool().users().contains(command.target())) throw denied();
        // Pool IDs are canonical even when the authority snapshot was requested using usernames.
        if (decision.qualification().identities().values().stream().noneMatch(user -> command.target().equals(user.id()) && user.active())) throw denied();
        veto(command.target(),decision);
    }

    private AssignmentCaseEvaluator.Decision limitValidity(AssignmentCaseEvaluator.Decision decision, java.time.Instant until) {
        var old = decision.qualification();
        var limited = new AssignmentQualificationComponent.Prepared(old.authorityRevision(), until, old.primary(), old.backup(),
                old.requiresClaim(), old.identities(), old.primaryIdentities(),old.nextEvaluationAt());
        return new AssignmentCaseEvaluator.Decision(decision.taskId(), decision.caseId(), decision.caseRevision(), decision.policy(),
                decision.excluded(), limited, decision.pool(), decision.portalRevision());
    }

    private Holding holding(Task task, Optional<ExtendedTaskInfo> info) {
        if (info.isEmpty()) return new Holding(task.getAssignee(), null, null, null, null);
        var value = info.get();
        return new Holding(task.getAssignee(), value.getDelegatedTo(), value.getDelegatedTargetType() == null ? null
                : value.getDelegatedTargetType().name(), value.getDelegatedBuCode(), value.getDelegatedRoleCode());
    }

    private void active(String id, AssignmentCaseEvaluator.Decision decision) {
        var user = decision.qualification().identities().get(id);
        if (user == null || !user.active() || !id.equals(user.id())) throw denied();
    }
    private void veto(String id, AssignmentCaseEvaluator.Decision decision) { if (decision.excluded().contains(id)) throw denied(); }
    private AssignmentIdentitySnapshot.Reference reference(String id) { return new AssignmentIdentitySnapshot.Reference(id, AssignmentIdentitySnapshot.Kind.ID); }
    private Task requireTask(String id) { var task = tasks.createTaskQuery().taskId(id).singleResult(); if (task == null) throw changed(); return task; }
    private AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
    private AssignmentFailure denied() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED); }
}

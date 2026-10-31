package com.workflow.assignment.impl;

import com.workflow.assignment.*;
import com.workflow.listener.TaskAssignmentListener;
import com.workflow.service.TaskAssigneeResolver;
import com.workflow.util.AssigneeRoleIdsSupport;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Source-specific transport decoding delegates all existing assignment semantics to their current owners. */
@Component
public class AssignmentSourceResolverImpl implements AssignmentSourceResolver {
    private final TaskAssigneeResolver resolver;
    private final TaskAssignmentListener listener;
    private final AssignmentSourceValues values;
    private final AssignmentDefinitionPolicy definitions;

    public AssignmentSourceResolverImpl(TaskAssigneeResolver resolver, @Lazy TaskAssignmentListener listener,
            AssignmentSourceValues values, AssignmentDefinitionPolicy definitions) {
        this.resolver = resolver; this.listener = listener; this.values = values; this.definitions = definitions;
    }

    @Override
    public Resolution resolve(Task task, AssignmentSourceSpec source,AssignmentTaskStateRepository.CaseContext context) {
        if (context==null || context.initiator()==null || context.initiator().isBlank()) {
            throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        return AssignmentIdentityReadScope.fresh(() -> switch (source.kind()) {
            case RESOLVER -> resolvedSource(task,source,context);
            case VARIABLE -> variable(task,source);
            case MANUAL -> manual(task, source);
            case ROW -> row(task, source,context);
            case FIXED -> boundFixed(task, source);
        });
    }

    private Resolution variable(Task task,AssignmentSourceSpec source) {
        if (source.variable()==null || source.variable().isBlank()) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        return Resolution.canonical(listener.resolveAssigneeFromVariable(source.variable(),values.variables(task,List.of(source.variable()))),null);
    }

    private Resolution row(Task task, AssignmentSourceSpec source,AssignmentTaskStateRepository.CaseContext context) {
        Map<String,Object> typed = values.row(task,java.util.stream.Stream.of(source.assigneeField(),source.roleField(),source.buField())
                .filter(field -> field!=null && !field.isBlank()).distinct().toList());
        Map<String,Object> row = typed;
        List<String> roles = source.roleField() == null ? List.of()
                : AssigneeRoleIdsSupport.resolveRoleCodesFromItem(typed, source.roleField());
        if (!roles.isEmpty()) {
            Object bu = source.buField() == null ? null : row.get(source.buField());
            String code = bu == null ? null : bu.toString().strip();
            if (code == null || code.isBlank()) code = context.startBu();
            if (code==null || code.isBlank()) throw new IllegalStateException("Assignment source BU is unavailable");
            return Resolution.canonical(resolver.resolveWithRoleIds("BU_ROLE", roles, code, null, null, code), code);
        }
        if (source.assigneeField() == null || source.assigneeField().isBlank()) {
            throw new IllegalStateException("Assignment row has no configured user field");
        }
        Object user = row.get(source.assigneeField());
        String id = com.workflow.listener.AssigneeUserIdNormalizer.normalizeFlowableUserIdValue(user);
        if (id == null) throw new IllegalStateException("Assignment row has no user reference");
        Object bu = source.buField() == null ? null : row.get(source.buField());
        if (bu != null && !(bu instanceof String)) throw new IllegalStateException("Assignment row BU must be a configured business-unit code");
        return Resolution.canonical(fixed(List.of(id), false), bu instanceof String code && !code.isBlank() ? code.strip() : null);
    }

    private Resolution manual(Task task, AssignmentSourceSpec source) {
        var variables = values.variables(task,List.of(
                defaultField(source.manualUserVariable(),"manualAssignee_",task),
                defaultField(source.manualBuVariable(),"manualAssignBu_",task),
                defaultField(source.manualRoleVariable(),"manualAssignRole_",task)).stream().distinct().toList());
        var result = listener.resolveManualAssign(task.getTaskDefinitionKey(), source.manualUserVariable(), source.manualBuVariable(),
                source.manualRoleVariable(), variables, null);
        String bu = null;
        if (result.getAssigneeType() == com.workflow.enums.AssigneeType.BU_ROLE) {
            String field = source.manualBuVariable() == null || source.manualBuVariable().isBlank()
                    ? "manualAssignBu_" + task.getTaskDefinitionKey() : source.manualBuVariable();
            Object value = variables.get(field);
            if (value instanceof String code && !code.isBlank()) bu = code;
        }
        return Resolution.canonical(result, bu);
    }

    private TaskAssigneeResolver.ResolveResult fixed(List<String> users, boolean claimPool) {
        if (users.size() == 1 && !claimPool) return TaskAssigneeResolver.ResolveResult.builder().assignee(users.get(0)).requiresClaim(false).build();
        return TaskAssigneeResolver.ResolveResult.builder().candidateUsers(users).requiresClaim(true).build();
    }

    private String defaultField(String field,String prefix,Task task) {
        return field==null || field.isBlank() ? prefix+task.getTaskDefinitionKey() : field;
    }

    private Resolution boundFixed(Task task, AssignmentSourceSpec source) {
        var bound = definitions.boundUsers(task.getProcessDefinitionId());
        var kinds = new java.util.LinkedHashMap<String,com.platform.common.assignment.AssignmentIdentitySnapshot.Kind>();
        var node = (org.flowable.bpmn.model.UserTask) definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        var users = source.fixedUsers().stream().map(reference -> {
            String id = bound.get(reference);
            boolean literal = reference.equals(node.getAssignee()) || node.getCandidateUsers().contains(reference);
            if (id == null && !source.canonicalFixedUsers() && literal
                    && definitions.node(task.getProcessDefinitionId(), task.getTaskDefinitionKey()).enabled()) {
                throw new IllegalStateException("Static assignment user has no deployment binding");
            }
            boolean canonical = id != null || source.canonicalFixedUsers() || literal;
            String resolved = id == null ? reference : id;
            var kind = canonical ? com.platform.common.assignment.AssignmentIdentitySnapshot.Kind.ID
                    : com.platform.common.assignment.AssignmentIdentitySnapshot.Kind.ID_OR_USERNAME;
            var previous = kinds.putIfAbsent(resolved, kind);
            if (previous != null && previous != kind) throw new IllegalStateException("Ambiguous fixed assignment source");
            return resolved;
        }).toList();
        return new Resolution(fixed(users, source.claimPool()), null, Map.copyOf(kinds));
    }

    private Resolution resolvedSource(Task task,AssignmentSourceSpec source,AssignmentTaskStateRepository.CaseContext context) {
        var type = com.workflow.enums.AssigneeType.fromCode(source.type());
        String anchor=source.anchor(), activeBu=source.activeBuCode();
        if (type!=null && type.requiresAnchorUserId()) {
            var node=definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
            if (!(node instanceof org.flowable.bpmn.model.UserTask)) throw new IllegalStateException("Frozen assignment node is unavailable");
            var configured=com.workflow.util.BpmnExtensionUtils.getExtensionProperty(node,"assigneeAnchor");
            if (com.workflow.util.AssigneeAnchorPolicy.resolve(source.type(),type,configured)==com.workflow.enums.AssigneeAnchor.INITIATOR) {
                anchor=context.initiator(); activeBu=context.startBu();
            }
        }
        var result = resolver.resolveWithRoleIds(source.type(), source.roles(), source.buCode(),
                context.initiator(), anchor, activeBu);
        String bu = type == com.workflow.enums.AssigneeType.BU_ROLE ? source.buCode() : null;
        return Resolution.canonical(result, bu);
    }
}

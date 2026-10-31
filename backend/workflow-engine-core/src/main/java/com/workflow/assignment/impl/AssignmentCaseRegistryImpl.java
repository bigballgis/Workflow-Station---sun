package com.workflow.assignment.impl;

import com.workflow.assignment.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentCaseFence;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Trusted case/round/position registry, available before the first Portal process mirror exists. */
@Component
public class AssignmentCaseRegistryImpl implements AssignmentCaseRegistry {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = com.platform.common.util.JsonUtils.getObjectMapper();
    private final AssignmentCaseRepository cases;
    private final RuntimeService runtime;
    private final AssignmentDefinitionPolicy definitions;
    private final CatalogSnapshotReader catalogs;

    public AssignmentCaseRegistryImpl(JdbcTemplate jdbc, AssignmentCaseRepository cases, @Lazy RuntimeService runtime,
            AssignmentDefinitionPolicy definitions, CatalogSnapshotReader catalogs) {
        this.jdbc = jdbc; this.cases = cases; this.runtime = runtime; this.definitions = definitions; this.catalogs = catalogs;
    }

    /** Caller prepared identity and BU; it must validate that snapshot after registration and before starting Flowable. */
    public Registration newRoot(String definition, String actor, String startBu) {
        return newRoot(definition, actor, startBu, null);
    }

    public Registration newRoot(String definition, String actor, String startBu, String startBuId) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Missing root initiator identity");
        new AssignmentDefinitionRetention(jdbc).start(definition);
        UUID caseId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        String instance = UUID.randomUUID().toString();
        cases.createCase(caseId, instance, actor, startBu, startBuId);
        new AssignmentCaseFence(jdbc).locked(caseId);
        Registration registered = new Registration(instance, caseId, roundId, definition, List.of());
        insert(registered, null, null);
        return registered;
    }

    /** Minimal reopen: exactly one active top-level round and the source's original immutable definition. */
    public Registration reopen(String sourceInstance) {
        Registration source = find(sourceInstance).orElseThrow(() -> new IllegalStateException("No trusted reopen source"));
        var fence = new AssignmentCaseFence(jdbc).locked(source.caseId());
        if (fence.synchronizationPending()) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_SYNC_PENDING);
        var state = cases.sourceState(sourceInstance);
        if (!state.root() || "ACTIVE".equals(state.state())) {
            throw new IllegalStateException("Only ended top-level requests can be reopened");
        }
        if (cases.hasActiveRoot(source.caseId())) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        Registration reopened = new Registration(UUID.randomUUID().toString(), source.caseId(), UUID.randomUUID(), source.definition(), List.of());
        insert(reopened, null, sourceInstance);
        return reopened;
    }

    public Optional<Registration> instance(String processInstance) {
        Optional<Registration> known = find(processInstance);
        if (known.isPresent()) return known;
        var execution = runtime.createExecutionQuery().executionId(processInstance).singleResult();
        if (execution == null) throw new IllegalStateException("Assignment execution is unavailable");
        var process = runtime.createProcessInstanceQuery().processInstanceId(processInstance).singleResult();
        if (process == null) throw new IllegalStateException("Assignment process is unavailable");
        if (execution.getSuperExecutionId() == null) {
            if (definitions.tracked(process.getProcessDefinitionId())) throw new com.platform.common.assignment.AssignmentFailure(
                    com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            return Optional.empty();
        }
        var callExecution = runtime.createExecutionQuery().executionId(execution.getSuperExecutionId()).singleResult();
        if (callExecution == null) throw new IllegalStateException("Assignment caller is unavailable");
        var parent = instance(callExecution.getProcessInstanceId());
        if (parent.isEmpty()) {
            if (definitions.tracked(process.getProcessDefinitionId())) throw new com.platform.common.assignment.AssignmentFailure(
                    com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            return Optional.empty();
        }
        Registration ancestor = parent.get();
        new AssignmentCaseFence(jdbc).locked(ancestor.caseId());
        var concurrent = find(processInstance);
        if (concurrent.isPresent()) return concurrent;
        var call = definitions.model(ancestor.definition()).getFlowElement(callExecution.getActivityId());
        if (!(call instanceof org.flowable.bpmn.model.CallActivity activity)) throw new IllegalStateException("Invalid assignment call position");
        Map<String,Object> position = new LinkedHashMap<>();
        position.put("definition", ancestor.definition()); position.put("node", call.getId());
        if (activity.getLoopCharacteristics() != null) {
            Object row = runtime.getVariable(processInstance, "__callRow");
            if (!(row instanceof Map<?,?> map)) throw new IllegalStateException("MI call has no stable row identity");
            position.put("row", rowKey(ancestor.definition(), call, map));
        }
        var path = new ArrayList<>(ancestor.calls()); path.add(Map.copyOf(position));
        var child = new Registration(processInstance, ancestor.caseId(), ancestor.roundId(), process.getProcessDefinitionId(), List.copyOf(path));
        insert(child, ancestor.instance(), null);
        return Optional.of(child);
    }

    public Optional<Registration> registerTask(Task task) {
        var registration = instance(task.getProcessInstanceId());
        if (registration.isEmpty()) return registration;
        var registered = registration.get();
        new AssignmentCaseFence(jdbc).locked(registered.caseId());
        var position = AssignmentSlotIdentity.position(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), registered.calls(), miPath(task));
        cases.registerTask(new AssignmentCaseRepository.TaskSnapshot(task.getId(), task.getTaskDefinitionKey(), registered,
                position, policyHash(task)));
        return registration;
    }

    public void ended(String process, boolean cancelled) {
        var registered = find(process);
        if (registered.isEmpty()) return;
        var fence=new AssignmentCaseFence(jdbc).locked(registered.get().caseId());
        if (cancelled && fence.synchronizationPending()) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_SYNC_PENDING);
        cases.ended(registered.get(), cancelled);
        AssignmentCompletionChanges.changed(process);
    }

    private List<Map<String,Object>> miPath(Task task) {
        var path = new ArrayList<Map<String,Object>>();
        String id = task.getExecutionId();
        while (id != null) {
            var execution = runtime.createExecutionQuery().executionId(id).singleResult();
            if (execution == null) throw new IllegalStateException("Incomplete MI execution ancestry");
            FlowElement node = execution.getActivityId() == null ? null
                    : definitions.model(task.getProcessDefinitionId()).getFlowElement(execution.getActivityId());
            if (node instanceof Activity activity && activity.getLoopCharacteristics() != null) {
                String variable = activity.getLoopCharacteristics().getElementVariable();
                if (variable == null || variable.isBlank()) throw new IllegalStateException("MI position has no declared item variable");
                if (runtime.hasVariableLocal(id, variable)) {
                    Object row = runtime.getVariableLocal(id, variable);
                    if (!(row instanceof Map<?,?> map)) throw new IllegalStateException("MI participant requires a stable configured item identity");
                    path.add(0, Map.of("scope", node.getId(), "row", rowKey(task.getProcessDefinitionId(), node, map)));
                }
            }
            id = execution.getParentId();
        }
        FlowElement scope = definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        while (scope != null) {
            if (scope instanceof Activity activity && activity.getLoopCharacteristics() != null) {
                String scopeId = scope.getId();
                if (path.stream().filter(item -> scopeId.equals(item.get("scope"))).count() != 1) {
                    throw new IllegalStateException("MI position does not have a complete stable ancestry");
                }
            }
            scope = scope.getSubProcess();
        }
        return List.copyOf(path);
    }

    private Map<String,Object> rowKey(String definition, FlowElement node, Map<?,?> row) {
        if (row.containsKey(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD)) {
            return AssignmentSlotIdentity.rowKey(row, List.of());
        }
        String table = node instanceof Activity activity?AssignmentMiRowSource.table(activity):null;
        if (table == null) return AssignmentSlotIdentity.rowKey(row, List.of());
        var pins = cases.catalogsFor(definition);
        if (pins.size() != 1) throw new IllegalStateException("Frozen assignment catalog is ambiguous or unavailable");
        return AssignmentSlotIdentity.rowKey(row, catalogs.primaryKeyFields(pins.get(0), table));
    }

    private Optional<Registration> find(String process) {
        return cases.find(process);
    }

    private void insert(Registration registered, String parent, String source) {
        cases.insert(registered, parent, source, definitions.model(registered.definition()).getMainProcess().getId());
        AssignmentCompletionChanges.created(registered.instance());
    }

    private String policyHash(Task task) {
        return AssignmentSlotIdentity.position(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), List.of(),
                List.of(parseMap(encode(definitions.node(task.getProcessDefinitionId(), task.getTaskDefinitionKey()))))).key();
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid assignment metadata", e); }
    }
    private Map<String,Object> parseMap(String value) {
        try { return json.readValue(value, new TypeReference<Map<String,Object>>() { }); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment identity", e); }
    }
}

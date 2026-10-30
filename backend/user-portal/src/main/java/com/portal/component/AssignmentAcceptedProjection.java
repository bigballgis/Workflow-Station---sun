package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.functionunit.RuntimeVersionContext;
import com.platform.common.util.JsonUtils;
import com.portal.entity.ProcessInstance;
import com.portal.repository.AssignmentAcceptedProjectionRepository;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Applies frozen status/Owner/call-mirror effects in the same transaction as the stage snapshot and receipt ledger. */
@Component
public class AssignmentAcceptedProjection {
    private final AssignmentAcceptedProjectionRepository repository;
    private final OwnerFieldComponent owners;
    private final ProcessTerminalStatusResolver terminal;
    public AssignmentAcceptedProjection(AssignmentAcceptedProjectionRepository repository,OwnerFieldComponent owners,ProcessTerminalStatusResolver terminal) {
        this.repository=repository; this.owners=owners; this.terminal=terminal;
    }
    public void apply(AssignmentOperationReceipt receipt,AssignmentCompletionRequest.Effect effect) {
        var states=decode(receipt,effect);
        var existing=new LinkedHashMap<>(repository.load(states.keySet()));
        if (!existing.containsKey(effect.instanceId())) throw conflict();
        relatedWrites(receipt,states,existing);
        materialize(states,existing);
        var rejected=rejected(states);
        for (var state:states.values()) project(state,existing.get(state.instanceId()).mirror(),rejected.contains(state.instanceId()));
        repository.save(List.copyOf(existing.values()));
    }
    private void relatedWrites(AssignmentOperationReceipt receipt,Map<String,AssignmentAcceptedProcessState> states,
            Map<String,AssignmentAcceptedProjectionRepository.Stored> existing) {
        try {
            Object raw=receipt.acceptedEffect().get(AssignmentRelatedVariablePatch.EFFECT_KEY);
            if (raw==null) throw conflict();
            List<AssignmentRelatedVariablePatch> patches=JsonUtils.getObjectMapper().convertValue(raw,new TypeReference<>() { });
            if (patches==null) throw conflict();
            var seen=new HashSet<String>();
            for (var patch:patches) {
                if (patch==null || !seen.add(patch.instanceId())) throw conflict();
                var state=states.get(patch.instanceId()); var row=existing.get(patch.instanceId());
                if (state==null || row==null || !patch.definitionId().equals(state.definitionId())
                        || !patch.definitionId().equals(row.mirror().getProcessDefinitionId())) throw conflict();
                ObjectNode before=JsonUtils.getObjectMapper().valueToTree(row.mirror().getVariables());
                row.mirror().setVariables(JsonUtils.getObjectMapper().convertValue(AssignmentVariablePatch.apply(before,patch.variablePatch()),new TypeReference<>() { }));
            }
        } catch (IllegalArgumentException malformed) { throw conflict(); }
    }
    private Map<String,AssignmentAcceptedProcessState> decode(AssignmentOperationReceipt receipt,AssignmentCompletionRequest.Effect effect) {
        try {
            Object raw=receipt.acceptedEffect().get(AssignmentAcceptedProcessState.EFFECT_KEY);
            if (raw==null) throw conflict();
            List<AssignmentAcceptedProcessState> values=JsonUtils.getObjectMapper().convertValue(raw,new TypeReference<>() { });
            if (values==null || values.isEmpty() || values.contains(null)) throw conflict();
            var states=new LinkedHashMap<String,AssignmentAcceptedProcessState>();
            for (var value:values) if (states.putIfAbsent(value.instanceId(),value)!=null) throw conflict();
            if (!states.containsKey(effect.instanceId()) || states.values().stream().filter(value->value.parentInstanceId()==null).count()!=1) throw conflict();
            for (var state:states.values()) {
                var visited=new HashSet<String>(); var cursor=state;
                while (cursor!=null) {
                    if (!visited.add(cursor.instanceId())) throw conflict();
                    if (cursor.parentInstanceId()==null) break;
                    cursor=states.get(cursor.parentInstanceId());
                    if (cursor==null) throw conflict();
                }
            }
            return states;
        } catch (IllegalArgumentException malformed) { throw conflict(); }
    }
    private void materialize(Map<String,AssignmentAcceptedProcessState> states,Map<String,AssignmentAcceptedProjectionRepository.Stored> existing) {
        var pending=states.values().stream().filter(value->!existing.containsKey(value.instanceId())).collect(Collectors.toCollection(ArrayList::new));
        while (!pending.isEmpty()) {
            var ready=pending.stream().filter(value->value.parentInstanceId()!=null && existing.containsKey(value.parentInstanceId())).toList();
            if (ready.isEmpty()) throw conflict();
            var created=repository.create(ready.stream().map(value->seed(value,existing.get(value.parentInstanceId()).mirror())).toList());
            for (var value:created) existing.put(value.mirror().getId(),value);
            pending.removeAll(ready);
        }
    }
    private ProcessInstance seed(AssignmentAcceptedProcessState state,ProcessInstance parent) {
        var vars=new LinkedHashMap<>(state.variables());
        var pin=new RuntimeVersionContext(text(vars,"functionUnitId"),text(vars,"functionUnitCode"),text(vars,"functionUnitVersionLabel"),state.definitionId());
        if (state.startTime()==null || parent.getStartUserId()==null) throw conflict();
        // Same initial mirror policy as engine hydration: local batch-edit transport metadata is never imported from Engine variables.
        vars.remove(SubTableWriteIsolation.BATCH_RUNTIME_FIELD); vars.remove(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD);
        var mirror=ProcessInstance.builder().id(state.instanceId()).processInstanceId(state.instanceId()).processDefinitionId(state.definitionId())
                .processDefinitionKey(state.processKey()).processDefinitionName(state.processName()).startUserId(parent.getStartUserId())
                .initiatorId(parent.getStartUserId()).parentProcessInstanceId(state.parentInstanceId()).callActivityId(state.callActivityId())
                .functionUnitCatalogId(pin.catalogId()).functionUnitCode(pin.code()).functionUnitVersionLabel(pin.versionLabel()).variables(vars)
                .businessKey(state.businessKey()).title(state.name()).startTime(local(state.startTime())).build();
        pin.applyTo(vars);
        return mirror;
    }
    private Set<String> rejected(Map<String,AssignmentAcceptedProcessState> states) {
        var rejected=new HashSet<String>();
        for (var value:states.values()) {
            Object outcome=value.variables().get("approvalStatus");
            if (outcome instanceof String text && "REJECTED".equalsIgnoreCase(text.trim()) && !"ACTIVE".equals(value.state())) {
                var current=value;
                while (current!=null) { rejected.add(current.instanceId()); current=current.parentInstanceId()==null?null:states.get(current.parentInstanceId()); }
            }
        }
        return rejected;
    }
    private void project(AssignmentAcceptedProcessState state,ProcessInstance mirror,boolean rejected) {
        if (!state.definitionId().equals(mirror.getProcessDefinitionId())
                || !Objects.equals(state.parentInstanceId(),mirror.getParentProcessInstanceId()) && mirror.getParentProcessInstanceId()!=null) throw conflict();
        try { ProcessVersionContext.apply(mirror,mirror.getVariables()); }
        catch (com.platform.common.functionunit.FunctionUnitVersionException malformed) { throw conflict(); }
        mirror.setParentProcessInstanceId(state.parentInstanceId()); mirror.setCallActivityId(state.callActivityId());
        String status=switch (state.state()) {
            case "ACTIVE" -> ProcessTerminalStatusResolver.STATUS_RUNNING;
            case "COMPLETED" -> terminal.resolveFinishedStatus(mirror);
            case "CANCELLED" -> ProcessTerminalStatusResolver.STATUS_WITHDRAWN;
            default -> throw conflict();
        };
        if (rejected && !"ACTIVE".equals(state.state())) { status=ProcessTerminalStatusResolver.STATUS_REJECTED; mirror.getVariables().put("approvalStatus","REJECTED"); }
        mirror.setStatus(status);
        var work=state.tasks().isEmpty()?null:state.tasks().get(0);
        boolean active="ACTIVE".equals(state.state());
        mirror.setCurrentNode(active && work!=null?work.name():null);
        mirror.setCurrentAssignee(active && work!=null?work.assignee():null);
        mirror.setCandidateUsers(active && work!=null && !work.candidateUsers().isEmpty()?String.join(",",work.candidateUsers()):null);
        if (!active) {
            if (state.endTime()==null) throw conflict();
            mirror.setEndTime(local(state.endTime())); mirror.setCompletedAt(mirror.getEndTime());
        }
        OwnerFieldComponent.stripProcessWideCurrentItem(mirror.getVariables());
        owners.applyAssigneeSnapshot(mirror.getFunctionUnitCode(),mirror.getVariables(),mirror.getCurrentAssignee(),mirror.getCandidateUsers(),status,
                MiOuterStepResolver.OuterLookup.known(work==null?null:work.ownerOuterStep()));
    }
    private String text(Map<String,Object> values,String key) { if (values.get(key) instanceof String value && !value.isBlank()) return value; throw conflict(); }
    private LocalDateTime local(java.time.Instant value) { return LocalDateTime.ofInstant(value,ZoneId.systemDefault()); }
    private AssignmentFailure conflict() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
}

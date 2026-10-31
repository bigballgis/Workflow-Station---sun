package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAcceptedProcessState;
import com.platform.common.assignment.AssignmentFailure;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Captures this round only. Recovery never asks a later live process to reconstruct an older completion. */
@Component
public class AssignmentCompletionOutcomeSnapshot {
    private final AssignmentTaskStateRepository instances;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final TaskService tasks;
    private final RepositoryService definitions;
    public AssignmentCompletionOutcomeSnapshot(AssignmentTaskStateRepository instances,RuntimeService runtime,
            HistoryService history,TaskService tasks,RepositoryService definitions) {
        this.instances=instances; this.runtime=runtime; this.history=history; this.tasks=tasks; this.definitions=definitions;
    }
    public Map<String,com.fasterxml.jackson.databind.node.ObjectNode> variablesBefore(String instance) {
        var chain=instances.affectedInstances(instance,java.util.Set.of(instance));
        var ids=chain.stream().map(AssignmentTaskStateRepository.RoundInstance::instance).collect(Collectors.toSet());
        if (ids.isEmpty()) throw unavailable();
        var active=runtime.createProcessInstanceQuery().processInstanceIds(ids).list();
        var variables=AssignmentWorkflowValues.runtime(runtime,ids);
        var result=new LinkedHashMap<String,com.fasterxml.jackson.databind.node.ObjectNode>();
        for (var process:active) {
            if (!ids.contains(process.getId())) throw unavailable();
            result.put(process.getId(),com.platform.common.util.JsonUtils.getObjectMapper().valueToTree(variables.get(process.getId())));
        }
        if (!result.keySet().equals(ids)) throw unavailable();
        return Map.copyOf(result);
    }
    public List<AssignmentAcceptedProcessState> capture(String instance) {
        var round=instances.affectedInstances(instance,AssignmentCompletionChanges.instances(instance));
        if (round.isEmpty()) throw unavailable();
        var ids=round.stream().map(AssignmentTaskStateRepository.RoundInstance::instance).collect(Collectors.toSet());
        var definitionIds=round.stream().map(AssignmentTaskStateRepository.RoundInstance::definition).collect(Collectors.toSet());
        var past=history.createHistoricProcessInstanceQuery().processInstanceIds(ids).list().stream()
                .collect(Collectors.toMap(org.flowable.engine.history.HistoricProcessInstance::getId,value->value));
        var active=runtime.createProcessInstanceQuery().processInstanceIds(ids).list().stream()
                .collect(Collectors.toMap(org.flowable.engine.runtime.ProcessInstance::getId,value->value));
        var versions=definitions.createProcessDefinitionQuery().processDefinitionIds(definitionIds).list().stream()
                .collect(Collectors.toMap(org.flowable.engine.repository.ProcessDefinition::getId,value->value));
        var positions=instances.roundPositions(instance).stream().collect(Collectors.toMap(AssignmentTaskStateRepository.RoundInstance::instance,value->value));
        var roots=positions.values().stream().filter(value->value.parent()==null).toList();
        if (roots.size()!=1) throw unavailable();
        var activeIds=positions.values().stream().filter(value->"ACTIVE".equals(value.state()))
                .map(AssignmentTaskStateRepository.RoundInstance::instance).toList();
        var current=currentTasks(activeIds);
        var work=new AssignmentOutcomeWork(positions,current,definitions);
        var values=AssignmentWorkflowValues.runtime(runtime,active.keySet());
        var ended=new java.util.HashSet<>(ids); ended.removeAll(active.keySet());
        values.putAll(AssignmentWorkflowValues.historic(history,ended));
        var data=new Snapshot(past,versions,work,values);
        return round.stream().map(registered->process(registered,data)).toList();
    }
    private List<org.flowable.task.api.Task> currentTasks(List<String> instances) {
        var result=new java.util.ArrayList<org.flowable.task.api.Task>();
        for (int offset=0;offset<instances.size();offset+=1000) {
            result.addAll(tasks.createTaskQuery().processInstanceIdIn(instances.subList(offset,Math.min(offset+1000,instances.size())))
                    .includeIdentityLinks().list());
        }
        result.sort(java.util.Comparator.comparing(org.flowable.task.api.Task::getId));
        return List.copyOf(result);
    }
    private AssignmentAcceptedProcessState process(AssignmentTaskStateRepository.RoundInstance registered,Snapshot data) {
        var historic=data.past().get(registered.instance());
        var definition=data.definitions().get(registered.definition());
        if (historic==null || definition==null || !registered.definition().equals(historic.getProcessDefinitionId())) throw unavailable();
        var values=data.values().get(registered.instance());
        if (values==null) throw unavailable();
        var variables=new LinkedHashMap<>(values);
        var projected=data.work().project(registered);
        return new AssignmentAcceptedProcessState(registered.instance(),registered.definition(),definition.getKey(),definition.getName(),
                registered.parent(),registered.callActivity(),registered.state(),historic.getBusinessKey(),historic.getName(),
                historic.getStartTime()==null?null:historic.getStartTime().toInstant(),historic.getEndTime()==null?null:historic.getEndTime().toInstant(),variables,projected);
    }
    private record Snapshot(Map<String,org.flowable.engine.history.HistoricProcessInstance> past,
            Map<String,org.flowable.engine.repository.ProcessDefinition> definitions,
            AssignmentOutcomeWork work,Map<String,Map<String,Object>> values) { }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}

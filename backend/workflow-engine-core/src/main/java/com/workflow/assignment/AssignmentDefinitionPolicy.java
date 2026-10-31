package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicy;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** All runtime policies come from the task's immutable deployed definition. */
@Component
public class AssignmentDefinitionPolicy {
    private final RepositoryService definitions;
    private final Map<String,AssignmentCompiledMetadata> compiled = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(32,0.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,AssignmentCompiledMetadata> entry) { return size()>128; }
            });
    private static final AssignmentExclusionPolicy DISABLED = new AssignmentExclusionPolicy(1, false, false, false, List.of(),
            new AssignmentExclusionPolicy.Backup(false, null, null, null, List.of(), List.of()));

    public AssignmentDefinitionPolicy(RepositoryService definitions) {
        this.definitions = definitions;
    }

    public boolean tracked(String definition) {
        return metadata(definition).tracked();
    }

    public AssignmentExclusionPolicy node(String definition, String key) {
        if (!(model(definition).getFlowElement(key) instanceof UserTask)) throw new IllegalStateException("Assignment task definition is unavailable");
        return metadata(definition).policies().getOrDefault(key, DISABLED);
    }

    public Map<String,String> boundUsers(String definition) {
        return metadata(definition).users();
    }

    private AssignmentCompiledMetadata metadata(String definition) {
        return compiled.computeIfAbsent(definition, id -> AssignmentCompiledMetadata.read(model(id)));
    }

    public BpmnModel model(String definition) {
        var model = definitions.getBpmnModel(definition);
        if (model == null || model.getMainProcess() == null) throw new IllegalStateException("Frozen assignment definition is unavailable");
        return model;
    }
}

package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads the immutable deployed BPMN used by an active instance, not the latest definition sharing its key. */
@Component
@RequiredArgsConstructor
public class InstanceBpmnReader {
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    public record Source(String processInstanceId, String processDefinitionId, String bpmnXml) { }

    public Source read(String processDefinitionKey, String processInstanceId) {
        if (blank(processDefinitionKey) || blank(processInstanceId)) throw unavailable();
        var instance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (instance == null || !processInstanceId.equals(instance.getId()) || blank(instance.getProcessDefinitionId())) throw unavailable();
        var definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(instance.getProcessDefinitionId()).singleResult();
        if (definition == null || !processDefinitionKey.equals(definition.getKey())
                || !instance.getProcessDefinitionId().equals(definition.getId())
                || blank(definition.getDeploymentId()) || blank(definition.getResourceName())) throw unavailable();
        try (var stream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            if (stream == null) throw unavailable();
            return new Source(instance.getId(), definition.getId(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new WorkflowBusinessException("INSTANCE_BPMN_UNAVAILABLE", "Instance deployed BPMN cannot be read", error);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static WorkflowBusinessException unavailable() {
        return new WorkflowBusinessException("INSTANCE_BPMN_UNAVAILABLE", "Instance deployed BPMN cannot be verified");
    }
}

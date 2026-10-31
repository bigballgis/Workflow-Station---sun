package com.workflow.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.workflow.exception.WorkflowValidationException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;

/** Shared by the actual start and its read-only preflight; never silently changes an explicit release pin. */
@Component
public class ProcessStartDefinitionResolver {
    private final RepositoryService repository;
    private final ProcessDeploymentManager deployment;
    public ProcessStartDefinitionResolver(RepositoryService repository,ProcessDeploymentManager deployment) {
        this.repository=repository; this.deployment=deployment;
    }
    public ProcessDefinition resolve(String key,String expected) {
        if (!StringUtils.hasText(key)) throw validation("Process definition key must not be empty",key);
        if (expected==null) {
            var definition=repository.createProcessDefinitionQuery().processDefinitionKey(key).latestVersion().singleResult();
            if (definition==null) throw validation("Process definition does not exist",key);
            if (definition.isSuspended()) throw validation("Process definition is suspended, cannot start new instance",key);
            return definition;
        }
        var definition=repository.createProcessDefinitionQuery().processDefinitionId(expected).singleResult();
        if (definition==null) {
            // FALLBACK(migration): resolve only the exact pre-isolation display ID, as the existing start path does.
            String exact=deployment.flowableDefinitionId(expected);
            if (exact!=null) definition=repository.createProcessDefinitionQuery().processDefinitionId(exact).singleResult();
        }
        if (definition==null || definition.isSuspended() || !key.equals(definition.getKey())) {
            throw new FunctionUnitVersionException("VERSION_DEFINITION_MISMATCH",expected);
        }
        return definition;
    }
    private WorkflowValidationException validation(String message,String key) {
        return new WorkflowValidationException(List.of(new WorkflowValidationException.ValidationError("processDefinitionKey",message,key)));
    }
}

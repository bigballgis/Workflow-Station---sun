package com.workflow.component;

import com.workflow.dto.response.DeploymentResult;
import org.flowable.engine.repository.ProcessDefinition;

/** Preserves the deployment API's legacy display ID alongside Flowable's actual immutable definition ID. */
final class ProcessDeploymentResultFactory {
    private ProcessDeploymentResultFactory() { }
    static DeploymentResult success(String deployment,ProcessDefinition definition) {
        String actual=definition.getId();
        String composite=actual.contains(":")?actual:String.format("%s:%d:%s",definition.getKey(),definition.getVersion(),actual);
        var result=DeploymentResult.success(deployment,composite,definition.getKey(),definition.getName(),definition.getVersion());
        result.setEngineProcessDefinitionId(actual);
        return result;
    }
}

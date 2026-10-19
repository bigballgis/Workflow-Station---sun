package com.workflow.component;

import com.workflow.dto.request.ProcessDefinitionRequest;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentEngineIdentityTest {
    @Test void rawEngineIdentityIsReturnedWithoutChangingTheLegacyDisplayId() {
        var repository = mock(RepositoryService.class);
        var builder = mock(DeploymentBuilder.class, RETURNS_SELF);
        var deployment = mock(Deployment.class);
        var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        var definition = mock(ProcessDefinition.class);
        when(repository.createDeployment()).thenReturn(builder); when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("synthetic-deployment");
        when(repository.createProcessDefinitionQuery()).thenReturn(query); when(query.list()).thenReturn(List.of(definition));
        when(definition.getId()).thenReturn("synthetic-raw-engine-id"); when(definition.getKey()).thenReturn("synthetic");
        when(definition.getVersion()).thenReturn(3);
        var component = new ProcessDeploymentManager();
        ReflectionTestUtils.setField(component, "repositoryService", repository);
        var request = new ProcessDefinitionRequest(); request.setKey("synthetic"); request.setName("Synthetic deployment identity");
        request.setBpmnXml("""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:synthetic">
                <process id="synthetic" isExecutable="true"><startEvent id="start"/><endEvent id="end"/>
                <sequenceFlow id="flow" sourceRef="start" targetRef="end"/></process></definitions>
                """);
        var result = component.deployProcess(request);
        assertThat(result.isSuccess()).as("synthetic deployment validation").isTrue();
        assertThat(result.getProcessDefinitionId()).isEqualTo("synthetic:3:synthetic-raw-engine-id");
        assertThat(result.getEngineProcessDefinitionId()).isEqualTo("synthetic-raw-engine-id");
    }
}

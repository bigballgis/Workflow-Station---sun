package com.workflow.component;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstanceBpmnReaderTest {
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final RepositoryService definitions = mock(RepositoryService.class);
    private final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class, RETURNS_SELF);
    private final ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
    private final ProcessInstance instance = mock(ProcessInstance.class);
    private final ProcessDefinition old = mock(ProcessDefinition.class);
    private final InstanceBpmnReader reader = new InstanceBpmnReader(runtime, definitions);

    @BeforeEach
    void setUp() {
        when(runtime.createProcessInstanceQuery()).thenReturn(instances);
        when(instances.singleResult()).thenReturn(instance);
        when(instance.getId()).thenReturn("old-instance");
        when(instance.getProcessDefinitionId()).thenReturn("process:1:old");
        when(definitions.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(old);
        when(old.getId()).thenReturn("process:1:old");
        when(old.getKey()).thenReturn("process");
        when(old.getDeploymentId()).thenReturn("old-deployment");
        when(old.getResourceName()).thenReturn("old.bpmn");
    }

    @Test
    void readsTheExactInstanceDeploymentAndClosesItsResource() throws Exception {
        var stream = spy(new ByteArrayInputStream("<old/>".getBytes(StandardCharsets.UTF_8)));
        when(definitions.getResourceAsStream("old-deployment", "old.bpmn")).thenReturn(stream);
        var source = reader.read("process", "old-instance");
        assertThat(source).isEqualTo(new InstanceBpmnReader.Source("old-instance", "process:1:old", "<old/>"));
        verify(query).processDefinitionId("process:1:old");
        verify(query, never()).latestVersion();
        verify(query, never()).processDefinitionKey(anyString());
        verify(stream).close();
    }

    @Test
    void missingInstanceOrDefinitionNeverFallsBackToLatest() {
        when(instances.singleResult()).thenReturn(null);
        assertThatThrownBy(() -> reader.read("process", "old-instance")).hasMessageContaining("cannot be verified");
        when(instances.singleResult()).thenReturn(instance);
        when(query.singleResult()).thenReturn(null);
        assertThatThrownBy(() -> reader.read("process", "old-instance")).hasMessageContaining("cannot be verified");
        verify(query, never()).latestVersion();
    }

    @Test
    void foreignKeyOrInstanceCannotImpersonateTheRequestedSource() {
        assertThatThrownBy(() -> reader.read("other-process", "old-instance")).hasMessageContaining("cannot be verified");
        assertThatThrownBy(() -> reader.read("process", "other-instance")).hasMessageContaining("cannot be verified");
        verify(definitions, never()).getResourceAsStream(anyString(), anyString());
    }

    @Test
    void unavailableResourceOrReadFailureDoesNotBecomeEmptyBpmn() throws Exception {
        assertThatThrownBy(() -> reader.read("process", "old-instance")).hasMessageContaining("cannot be verified");
        InputStream broken = mock(InputStream.class);
        when(broken.readAllBytes()).thenThrow(new IOException("synthetic resource failure"));
        when(definitions.getResourceAsStream("old-deployment", "old.bpmn")).thenReturn(broken);
        assertThatThrownBy(() -> reader.read("process", "old-instance")).hasMessageContaining("cannot be read");
        verify(broken).close();
    }
}

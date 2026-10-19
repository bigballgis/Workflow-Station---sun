package com.workflow.controller;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.component.InstanceBpmnReader;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InstanceBpmnReadTest {
    @Test
    void requestingAnExistingInstanceMustNotReadTheLatestDefinitionByKey() throws Exception {
        var processes = mock(ProcessEngineComponent.class);
        var runtime = mock(RuntimeService.class);
        var reader = mock(InstanceBpmnReader.class);
        when(reader.read("synthetic-process", "old-synthetic-instance"))
                .thenReturn(new InstanceBpmnReader.Source("old-synthetic-instance", "synthetic-process:1:old", "<old-version/>"));
        when(processes.getBpmnXml("synthetic-process")).thenReturn("<new-version/>");
        var mvc = MockMvcBuilders.standaloneSetup(new ProcessController(processes, runtime, reader)).build();

        mvc.perform(get("/api/v1/processes/definitions/synthetic-process/bpmn")
                        .param("processInstanceId", "old-synthetic-instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processInstanceId").value("old-synthetic-instance"))
                .andExpect(jsonPath("$.data.processDefinitionId").value("synthetic-process:1:old"))
                .andExpect(jsonPath("$.data.bpmnXml").value("<old-version/>"));
        verify(processes, never()).getBpmnXml(anyString());
        mvc.perform(get("/api/v1/processes/definitions/synthetic-process/bpmn"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bpmnXml").value("<new-version/>"));
    }
}

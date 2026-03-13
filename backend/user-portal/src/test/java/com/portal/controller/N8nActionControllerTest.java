package com.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * N8nActionController 单元测试
 *
 * 测试 Action 执行请求转发、配置加载、错误处理
 *
 * Validates: Requirements 10.18, 10.19
 */
@ExtendWith(MockitoExtension.class)
class N8nActionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private WorkflowEngineClient workflowEngineClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private N8nActionController n8nActionController;

    private static final String EXECUTE_URL = "/api/portal/n8n/action/execute";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(n8nActionController).build();
    }

    // ==================== 正常执行场景 ====================

    @Test
    @DisplayName("POST /execute - 正常执行 N8N Action 并返回结果")
    void executeAction_success() throws Exception {
        // Given
        String actionId = "action-001";
        String configJson = """
            {
                "n8nConfigId": "config-001",
                "n8nWorkflowId": "wf-001",
                "webhookUrl": "https://n8n.example.com/webhook/test",
                "timeoutSeconds": 120,
                "inputMapping": [{"source": "var1", "target": "param1"}],
                "outputMapping": [{"source": "out1", "target": "result1"}]
            }
            """;

        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("N8N_ACTION")
                .configJson(configJson)
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));

        Map<String, Object> engineResponse = new HashMap<>();
        engineResponse.put("success", true);
        engineResponse.put("data", Map.of("status", "SUCCESS"));
        when(workflowEngineClient.executeN8nAction(any())).thenReturn(Optional.of(engineResponse));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);
        requestBody.put("taskId", "task-001");
        requestBody.put("processInstanceId", "proc-001");
        requestBody.put("inputData", Map.of("var1", "value1"));

        // When & Then
        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(workflowEngineClient).executeN8nAction(any());
    }

    // ==================== 配置加载场景 ====================

    @Test
    @DisplayName("POST /execute - actionDefinitionId 为空返回错误")
    void executeAction_missingActionDefinitionId() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("taskId", "task-001");

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400"));

        verify(workflowEngineClient, never()).executeN8nAction(any());
    }

    @Test
    @DisplayName("POST /execute - Action 定义不存在返回 404")
    void executeAction_actionNotFound() throws Exception {
        String actionId = "non-existent";
        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.empty());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("404"));

        verify(workflowEngineClient, never()).executeN8nAction(any());
    }

    @Test
    @DisplayName("POST /execute - Action 类型不是 N8N_ACTION 返回错误")
    void executeAction_wrongActionType() throws Exception {
        String actionId = "action-002";
        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("API_CALL")
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("Action definition is not N8N_ACTION type"));

        verify(workflowEngineClient, never()).executeN8nAction(any());
    }

    @Test
    @DisplayName("POST /execute - configJson 为空返回错误")
    void executeAction_emptyConfigJson() throws Exception {
        String actionId = "action-003";
        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("N8N_ACTION")
                .configJson(null)
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400"));

        verify(workflowEngineClient, never()).executeN8nAction(any());
    }

    @Test
    @DisplayName("POST /execute - configJson 格式无效返回错误")
    void executeAction_invalidConfigJson() throws Exception {
        String actionId = "action-004";
        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("N8N_ACTION")
                .configJson("invalid-json{{{")
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("500"));

        verify(workflowEngineClient, never()).executeN8nAction(any());
    }

    // ==================== 错误处理场景 ====================

    @Test
    @DisplayName("POST /execute - workflow engine 不可用返回 503")
    void executeAction_workflowEngineUnavailable() throws Exception {
        String actionId = "action-005";
        String configJson = """
            {
                "n8nConfigId": "config-001",
                "n8nWorkflowId": "wf-001",
                "webhookUrl": "https://n8n.example.com/webhook/test",
                "timeoutSeconds": 120,
                "inputMapping": [],
                "outputMapping": []
            }
            """;

        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("N8N_ACTION")
                .configJson(configJson)
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(workflowEngineClient.executeN8nAction(any())).thenReturn(Optional.empty());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);
        requestBody.put("taskId", "task-001");
        requestBody.put("processInstanceId", "proc-001");

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("503"));

        verify(workflowEngineClient).executeN8nAction(any());
    }

    @Test
    @DisplayName("POST /execute - 正确提取 configJson 中的参数并转发")
    void executeAction_correctlyExtractsConfigParams() throws Exception {
        String actionId = "action-006";
        String configJson = """
            {
                "n8nConfigId": "cfg-uuid-123",
                "n8nWorkflowId": "workflow-456",
                "webhookUrl": "https://n8n.example.com/webhook/abc",
                "timeoutSeconds": 60,
                "inputMapping": [{"source": "processVar1", "target": "n8nParam1"}],
                "outputMapping": [{"source": "n8nOutput1", "target": "processVar3"}]
            }
            """;

        ActionDefinition action = ActionDefinition.builder()
                .id(actionId)
                .actionType("N8N_ACTION")
                .configJson(configJson)
                .build();

        when(actionDefinitionRepository.findById(actionId)).thenReturn(Optional.of(action));

        Map<String, Object> engineResponse = Map.of("success", true, "status", "SUCCESS");
        when(workflowEngineClient.executeN8nAction(any())).thenReturn(Optional.of(engineResponse));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("actionDefinitionId", actionId);
        requestBody.put("taskId", "task-789");
        requestBody.put("processInstanceId", "proc-012");
        requestBody.put("inputData", Map.of("processVar1", "hello"));

        mockMvc.perform(post(EXECUTE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify the request forwarded to workflow engine contains correct params
        verify(workflowEngineClient).executeN8nAction(argThat(req -> {
            return "cfg-uuid-123".equals(req.get("n8nConfigId"))
                    && "workflow-456".equals(req.get("n8nWorkflowId"))
                    && "https://n8n.example.com/webhook/abc".equals(req.get("webhookUrl"))
                    && Integer.valueOf(60).equals(req.get("timeoutSeconds"))
                    && "task-789".equals(req.get("taskId"))
                    && "proc-012".equals(req.get("processInstanceId"));
        }));
    }
}

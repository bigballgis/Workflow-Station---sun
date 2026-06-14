package com.portal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.platform.common.dto.ApiResponse;
import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.security.CurrentUserId;
import com.platform.common.security.SsrfProtection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * N8N Action execution controller.
 * 
 * Receives N8N Action execution requests from the frontend, loads configuration
 * from sys_action_definitions, and forwards to workflow-engine-core via WorkflowEngineClient.
 *
 * Validates: Requirements 10.18, 10.19
 */
@Slf4j
@RestController
@RequestMapping("/n8n/action")
@RequiredArgsConstructor
@Tag(name = "N8N Action", description = "N8N Action execution API")
public class N8nActionController {

    private final ActionDefinitionRepository actionDefinitionRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final ObjectMapper objectMapper;

    /**
     * Execute N8N Action.
     *
     * Receives actionDefinitionId, taskId, processInstanceId, and inputData,
     * loads configJson from sys_action_definitions, extracts N8N config parameters,
     * and forwards to workflow-engine-core via POST /api/v1/n8n/execute.
     *
     * Validates: Requirements 10.18, 10.19
     */
    @Operation(summary = "Execute N8N Action", description = "Trigger N8N workflow execution and synchronously wait for result")
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> executeAction(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> requestBody) {
        log.info("N8N action executed by user: {}", userId);
        String actionDefinitionId = String.valueOf(requestBody.get("actionDefinitionId"));
        String taskId = String.valueOf(requestBody.getOrDefault("taskId", ""));
        String processInstanceId = String.valueOf(requestBody.getOrDefault("processInstanceId", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputData = (Map<String, Object>) requestBody.get("inputData");

        log.info("N8N Action execute request: actionDefinitionId={}, taskId={}, processInstanceId={}",
                actionDefinitionId, taskId, processInstanceId);

        // 1. Validate actionDefinitionId
        if (actionDefinitionId == null || actionDefinitionId.isBlank()) {
            return ApiResponse.error("400", "actionDefinitionId is required");
        }

        // 2. Load ActionDefinition from sys_action_definitions
        Optional<ActionDefinition> actionOpt = actionDefinitionRepository.findById(actionDefinitionId);
        if (actionOpt.isEmpty()) {
            log.warn("Action definition not found: {}", actionDefinitionId);
            return ApiResponse.error("404", "Action definition not found: " + actionDefinitionId);
        }

        ActionDefinition action = actionOpt.get();

        // 3. Verify it's an N8N_ACTION type
        if (!"N8N_ACTION".equals(action.getActionType())) {
            log.warn("Action definition {} is not N8N_ACTION type, actual: {}", actionDefinitionId, action.getActionType());
            return ApiResponse.error("400", "Action definition is not N8N_ACTION type");
        }

        // 4. Parse configJson
        String configJson = action.getConfigJson();
        if (configJson == null || configJson.isBlank()) {
            log.warn("Action definition {} has empty configJson", actionDefinitionId);
            return ApiResponse.error("400", "Action definition has empty configJson");
        }

        Map<String, Object> config;
        try {
            config = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse configJson for action {}: {}", actionDefinitionId, e.getMessage());
            return ApiResponse.error("500", "Failed to parse action configJson");
        }

        // 5. Extract N8N config parameters
        String n8nConfigId = (String) config.get("n8nConfigId");
        String n8nWorkflowId = (String) config.get("n8nWorkflowId");
        String webhookUrl = (String) config.get("webhookUrl");
        SsrfProtection.validate(webhookUrl);
        Integer timeoutSeconds = config.get("timeoutSeconds") != null
                ? ((Number) config.get("timeoutSeconds")).intValue() : 120;
        Object outputMapping = config.get("outputMapping");

        // 6. Build request for workflow-engine-core
        // Note: inputMapping in configJson is for frontend parameter definition (paramName, paramType, etc.)
        // and NOT the workflow-engine's source→target variable mapping format.
        // The frontend already places resolved data directly into inputData, so we do NOT forward
        // the frontend-format inputMapping to workflow-engine (it would fail deserialization).
        Map<String, Object> executeRequest = new HashMap<>();
        executeRequest.put("n8nConfigId", n8nConfigId);
        executeRequest.put("n8nWorkflowId", n8nWorkflowId);
        executeRequest.put("webhookUrl", webhookUrl);
        executeRequest.put("timeoutSeconds", timeoutSeconds);
        executeRequest.put("taskId", taskId);
        executeRequest.put("processInstanceId", processInstanceId);
        executeRequest.put("inputData", inputData);

        if (outputMapping != null) {
            try {
                executeRequest.put("outputMapping", objectMapper.writeValueAsString(outputMapping));
            } catch (Exception e) {
                log.warn("Failed to serialize outputMapping: {}", e.getMessage());
            }
        }

        // 7. Forward to workflow-engine-core via WorkflowEngineClient
        Optional<Map<String, Object>> result = workflowEngineClient.executeN8nAction(executeRequest);

        if (result.isPresent()) {
            return ApiResponse.success(result.get());
        } else {
            log.error("Failed to execute N8N action: workflow engine unavailable or returned error");
            return ApiResponse.error("503", "Workflow engine unavailable or execution failed");
        }
    }
}

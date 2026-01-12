package com.admin.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow Engine Core 客户端
 * 用于 admin-center 调用 workflow-engine-core 模块的 API
 * 主要用于将流程定义部署到 Flowable 引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineClient {

    private final RestTemplate restTemplate;

    @Value("${workflow-engine.url:http://localhost:8091}")
    private String workflowEngineUrl;

    @Value("${workflow-engine.enabled:true}")
    private boolean workflowEngineEnabled;

    /**
     * 检查 workflow-engine-core 是否可用
     */
    public boolean isAvailable() {
        if (!workflowEngineEnabled) {
            log.debug("Workflow engine is disabled by configuration");
            return false;
        }
        try {
            String healthUrl = workflowEngineUrl + "/actuator/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Workflow engine not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 部署流程定义到 Flowable 引擎
     * 
     * @param processKey 流程定义键
     * @param bpmnXml BPMN XML 内容
     * @param name 流程名称
     * @return 部署结果，包含 deploymentId 和 processDefinitionId
     */
    public Optional<ProcessDeploymentResult> deployProcess(String processKey, String bpmnXml, String name) {
        if (!isAvailable()) {
            log.warn("Workflow engine is not available, cannot deploy process: {}", processKey);
            return Optional.empty();
        }
        
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/deploy";
            
            Map<String, Object> request = new HashMap<>();
            request.put("key", processKey);
            request.put("name", name);
            request.put("bpmnXml", bpmnXml);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("Deploying process to Flowable: key={}, name={}", processKey, name);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, 
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                
                if (data != null) {
                    ProcessDeploymentResult result = new ProcessDeploymentResult();
                    result.setDeploymentId((String) data.get("deploymentId"));
                    result.setProcessDefinitionId((String) data.get("processDefinitionId"));
                    result.setProcessDefinitionKey((String) data.get("processDefinitionKey"));
                    result.setVersion(data.get("version") != null ? ((Number) data.get("version")).intValue() : 1);
                    result.setSuccess(Boolean.TRUE.equals(data.get("success")));
                    result.setMessage((String) data.get("message"));
                    
                    log.info("Process deployed successfully: deploymentId={}, processDefinitionId={}", 
                            result.getDeploymentId(), result.getProcessDefinitionId());
                    
                    return Optional.of(result);
                }
            }
            
            log.warn("Failed to deploy process: unexpected response");
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to deploy process to workflow engine: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 删除流程定义
     * 
     * @param deploymentId 部署ID
     * @param cascade 是否级联删除（包括运行中的流程实例）
     * @return 是否删除成功
     */
    public boolean deleteProcessDefinition(String deploymentId, boolean cascade) {
        if (!isAvailable()) {
            log.warn("Workflow engine is not available, cannot delete deployment: {}", deploymentId);
            return false;
        }
        
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/deployments/" 
                    + deploymentId + "?cascade=" + cascade;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.DELETE, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Process definition deleted: deploymentId={}", deploymentId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to delete process definition: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 暂停流程定义
     */
    public boolean suspendProcessDefinition(String processDefinitionId) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/" 
                    + processDefinitionId + "/suspend";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to suspend process definition: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 激活流程定义
     */
    public boolean activateProcessDefinition(String processDefinitionId) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/" 
                    + processDefinitionId + "/activate";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to activate process definition: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 流程部署结果
     */
    @lombok.Data
    public static class ProcessDeploymentResult {
        private String deploymentId;
        private String processDefinitionId;
        private String processDefinitionKey;
        private int version;
        private boolean success;
        private String message;
    }
}

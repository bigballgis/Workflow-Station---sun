package com.developer.client;

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
 * Workflow Engine Core client for developer workstation.
 * Used to deploy BPMN process definitions to Flowable engine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineClient {

    private final RestTemplate restTemplate;

    @Value("${workflow-engine.url:http://localhost:8081}")
    private String workflowEngineUrl;

    @Value("${workflow-engine.enabled:true}")
    private boolean workflowEngineEnabled;

    /**
     * Check if workflow-engine-core is available
     */
    public boolean isAvailable() {
        if (!workflowEngineEnabled) {
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
     * Deploy a process definition to Flowable.
     * 
     * @param processKey the process definition key (versioned format: {functionUnitName}_v{version})
     * @param bpmnXml the BPMN XML content
     * @param name the display name for the process
     * @return deployment result containing process definition ID and key
     */
    public Optional<Map<String, Object>> deployProcess(String processKey, String bpmnXml, String name) {
        if (!isAvailable()) {
            log.warn("Workflow engine is not available for deployment");
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
                log.info("Successfully deployed process to Flowable: key={}", processKey);
                return Optional.of(response.getBody());
            } else {
                log.warn("Failed to deploy process to Flowable: status={}", response.getStatusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Failed to deploy process to workflow engine: key={}", processKey, e);
            throw new RuntimeException("Failed to deploy process to Flowable: " + e.getMessage(), e);
        }
    }
}

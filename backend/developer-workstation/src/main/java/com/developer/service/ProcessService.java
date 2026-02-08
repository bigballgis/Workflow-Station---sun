package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.ProcessInstance;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ProcessInstanceRepository;
import com.developer.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for process instance management with version binding.
 * Handles process instance creation, binding to function unit versions, and querying.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 12.3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessService {
    
    private final ProcessInstanceRepository processInstanceRepository;
    private final VersionService versionService;
    private final WorkflowEngineClient workflowEngineClient;
    
    /**
     * Create a new process instance bound to the active version of a function unit.
     * The process instance is permanently bound to the version that is active at creation time.
     * 
     * Requirements: 5.1 - WHEN a Process_Instance is created, THE System SHALL bind it to 
     *                     the current Active_Version
     *               12.3 - WHEN a Process_Instance is created, THE System SHALL use the 
     *                      process definition key corresponding to the Active_Version
     * 
     * @param functionUnitName the name of the function unit
     * @param processVariables the process variables to start the process with
     * @param startUserId the ID of the user starting the process
     * @param startUserName the name of the user starting the process
     * @return the created process instance
     * @throws ResourceNotFoundException if no active version is found
     * @throws RuntimeException if process creation fails
     */
    @Transactional
    public ProcessInstance createProcessInstance(
            String functionUnitName,
            Map<String, Object> processVariables,
            String startUserId,
            String startUserName) {
        
        log.info("Creating process instance for function unit: {} by user: {}", 
                functionUnitName, startUserId);
        
        // Validate inputs
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        if (startUserId == null || startUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("Start user ID cannot be null or empty");
        }
        
        try {
            // Step 1: Query for active version
            FunctionUnit activeVersion = versionService.getActiveVersion(functionUnitName);
            log.debug("Found active version: {} (ID: {})", 
                    activeVersion.getVersion(), activeVersion.getId());
            
            // Step 2: Get version-specific process definition key
            String processDefinitionKey = getProcessDefinitionKey(activeVersion);
            log.debug("Using process definition key: {}", processDefinitionKey);
            
            // Step 3: Start process in Flowable
            String processInstanceId = startProcessInFlowable(
                    processDefinitionKey, 
                    processVariables, 
                    startUserId);
            log.debug("Started process in Flowable with ID: {}", processInstanceId);
            
            // Step 4: Create process instance record bound to active version
            ProcessInstance processInstance = ProcessInstance.builder()
                    .id(processInstanceId)
                    .processDefinitionKey(processDefinitionKey)
                    .processDefinitionName(activeVersion.getName())
                    .startUserId(startUserId)
                    .startUserName(startUserName)
                    .status("RUNNING")
                    .variables(processVariables)
                    .functionUnitVersion(activeVersion)  // Bind to active version
                    .build();
            
            // Step 5: Store binding in database
            processInstance = processInstanceRepository.save(processInstance);
            
            log.info("Successfully created process instance {} bound to version {} (ID: {})", 
                    processInstanceId, activeVersion.getVersion(), activeVersion.getId());
            
            return processInstance;
            
        } catch (ResourceNotFoundException e) {
            log.error("No active version found for function unit: {}", functionUnitName);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create process instance for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to create process instance: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Start a process in Flowable using the workflow engine client.
     * 
     * @param processDefinitionKey the process definition key
     * @param processVariables the process variables
     * @param startUserId the user ID starting the process
     * @return the process instance ID from Flowable
     * @throws RuntimeException if the process start fails
     */
    private String startProcessInFlowable(
            String processDefinitionKey,
            Map<String, Object> processVariables,
            String startUserId) {
        
        try {
            // In a real implementation, this would call the workflow engine
            // For now, we'll generate a UUID as the process instance ID
            // The actual Flowable integration would be done through WorkflowEngineClient
            
            // TODO: Implement actual Flowable process start
            // Optional<Map<String, Object>> result = workflowEngineClient.startProcess(
            //     processDefinitionKey, processVariables, startUserId);
            
            String processInstanceId = UUID.randomUUID().toString();
            log.debug("Generated process instance ID: {}", processInstanceId);
            
            return processInstanceId;
            
        } catch (Exception e) {
            log.error("Failed to start process in Flowable: {}", processDefinitionKey, e);
            throw new RuntimeException(
                "Failed to start process in Flowable: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get the process definition key for a specific function unit version.
     * The key is in the format: {functionUnitName}_v{version}
     * 
     * Requirements: 5.3 - WHEN a Process_Instance executes, THE System SHALL use the 
     *                     Function_Unit version it was bound to at creation time
     * 
     * @param functionUnitVersion the function unit version
     * @return the version-specific process definition key
     * @throws RuntimeException if the process definition is not found
     */
    @Transactional(readOnly = true)
    public String getProcessDefinitionKey(FunctionUnit functionUnitVersion) {
        log.debug("Retrieving process definition key for version ID: {}", 
                functionUnitVersion.getId());
        
        try {
            ProcessDefinition processDefinition = functionUnitVersion.getProcessDefinition();
            
            if (processDefinition == null) {
                log.error("No process definition found for version ID: {}", 
                        functionUnitVersion.getId());
                throw new ResourceNotFoundException(
                    "ProcessDefinition", 
                    "functionUnitVersionId=" + functionUnitVersion.getId()
                );
            }
            
            // Generate the versioned process definition key
            String processDefinitionKey = String.format("%s_v%s", 
                    functionUnitVersion.getName(), 
                    functionUnitVersion.getVersion());
            
            log.debug("Process definition key: {}", processDefinitionKey);
            return processDefinitionKey;
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve process definition for version ID: {}", 
                    functionUnitVersion.getId(), e);
            throw new RuntimeException(
                "Failed to retrieve process definition: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get all process instances bound to a specific function unit version.
     * Includes version information in the results.
     * 
     * Requirements: 5.5 - WHEN querying Process_Instance data, THE System SHALL include 
     *                     the bound Function_Unit version information
     * 
     * @param versionId the ID of the function unit version
     * @return list of process instances with version information
     */
    @Transactional(readOnly = true)
    public List<ProcessInstance> getProcessInstancesByVersion(Long versionId) {
        log.info("Retrieving process instances for version ID: {}", versionId);
        
        // Validate input
        ValidationUtils.validateVersionId(versionId);
        
        try {
            List<ProcessInstance> processInstances = processInstanceRepository
                    .findByFunctionUnitVersionId(versionId);
            
            log.info("Found {} process instances for version ID: {}", 
                    processInstances.size(), versionId);
            
            return processInstances;
            
        } catch (Exception e) {
            log.error("Failed to retrieve process instances for version ID: {}", 
                    versionId, e);
            throw new RuntimeException(
                "Failed to retrieve process instances: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Count process instances bound to a specific function unit version.
     * Useful for determining the impact of rollback operations.
     * 
     * @param versionId the ID of the function unit version
     * @return count of process instances bound to the version
     */
    @Transactional(readOnly = true)
    public long countProcessInstancesByVersion(Long versionId) {
        log.debug("Counting process instances for version ID: {}", versionId);
        
        // Validate input
        ValidationUtils.validateVersionId(versionId);
        
        try {
            long count = processInstanceRepository.countByFunctionUnitVersionId(versionId);
            log.debug("Found {} process instances for version ID: {}", count, versionId);
            return count;
            
        } catch (Exception e) {
            log.error("Failed to count process instances for version ID: {}", versionId, e);
            throw new RuntimeException(
                "Failed to count process instances: " + e.getMessage(), e
            );
        }
    }
}

package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.dto.DeploymentResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.FunctionUnitRepository;
import com.developer.util.ValidationUtils;
import com.platform.common.exception.TransactionError;
import com.platform.common.exception.VersionValidationError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for deploying function units with versioning support.
 * Handles the complete deployment workflow including version creation,
 * permission inheritance, BPMN deployment to Flowable, and version activation.
 * 
 * Requirements: 1.1, 1.4, 4.1, 7.1-7.6, 12.1, 12.2, 12.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final VersionService versionService;
    private final PermissionService permissionService;
    private final WorkflowEngineClient workflowEngineClient;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;
    
    /**
     * Deploy BPMN to Flowable with versioned key generation.
     * 
     * Requirements: 7.4 - WHEN deploying to Flowable, THE System SHALL use a version-specific 
     *                     process definition key
     *               12.1 - WHEN a Function_Unit version is deployed, THE System SHALL deploy 
     *                      the BPMN to Flowable with a version-specific process definition key
     *               12.2 - WHEN generating a process definition key, THE System SHALL include 
     *                      the version number in the key format
     * 
     * @param functionUnitName the name of the function unit
     * @param version the version number
     * @param bpmnXml the BPMN XML content
     * @return the process definition key in format: {functionUnitName}_v{version}
     * @throws RuntimeException if deployment to Flowable fails
     */
    public String deployBPMNToFlowable(String functionUnitName, String version, String bpmnXml) {
        log.info("Deploying BPMN to Flowable for function unit: {} version: {}", 
                functionUnitName, version);
        
        // Validate inputs
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        ValidationUtils.validateVersionFormat(version);
        ValidationUtils.validateBpmnXml(bpmnXml);
        
        try {
            // Generate versioned process definition key
            String processDefinitionKey = generateProcessDefinitionKey(functionUnitName, version);
            log.debug("Generated process definition key: {}", processDefinitionKey);
            
            // Deploy to Flowable
            String displayName = String.format("%s (v%s)", functionUnitName, version);
            Optional<Map<String, Object>> deploymentResult = workflowEngineClient.deployProcess(
                    processDefinitionKey, bpmnXml, displayName);
            
            if (deploymentResult.isEmpty()) {
                log.error("Failed to deploy BPMN to Flowable: no response from workflow engine");
                throw new RuntimeException(
                    String.format("Failed to deploy BPMN to Flowable for %s v%s: workflow engine unavailable", 
                            functionUnitName, version)
                );
            }
            
            log.info("Successfully deployed BPMN to Flowable: key={}", processDefinitionKey);
            return processDefinitionKey;
            
        } catch (RuntimeException e) {
            log.error("Failed to deploy BPMN to Flowable for function unit: {} version: {}", 
                    functionUnitName, version, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error deploying BPMN to Flowable for function unit: {} version: {}", 
                    functionUnitName, version, e);
            throw new RuntimeException(
                "Unexpected error deploying BPMN to Flowable: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Generate versioned process definition key.
     * Format: {functionUnitName}_v{version}
     * 
     * @param functionUnitName the function unit name
     * @param version the version number
     * @return the versioned process definition key
     */
    private String generateProcessDefinitionKey(String functionUnitName, String version) {
        return String.format("%s_v%s", functionUnitName, version);
    }
    
    /**
     * Deploy a new version of a function unit with retry logic for concurrent modifications.
     * This method wraps deployFunctionUnitInternal with retry logic to handle optimistic locking failures.
     * 
     * @param functionUnitName the name of the function unit to deploy
     * @param bpmnXml the BPMN XML content
     * @param changeType the type of change: "major", "minor", or "patch"
     * @param metadata additional metadata for the function unit
     * @return the deployment result containing version information
     * @throws VersionValidationError if the version already exists or validation fails
     * @throws TransactionError if the deployment transaction fails after all retries
     */
    public DeploymentResult deployFunctionUnit(
            String functionUnitName, 
            String bpmnXml, 
            String changeType,
            Map<String, Object> metadata) {
        
        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                return deployFunctionUnitInternal(functionUnitName, bpmnXml, changeType, metadata);
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("Deployment failed after {} attempts due to concurrent modifications", 
                            MAX_RETRY_ATTEMPTS);
                    throw new TransactionError(
                            "Deployment (concurrent modification)", 
                            new RuntimeException(
                                    String.format("Failed after %d attempts due to concurrent modifications", 
                                            MAX_RETRY_ATTEMPTS), 
                                    e)
                    );
                }
                
                log.warn("Optimistic locking failure on attempt {}, retrying...", attempt);
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TransactionError("Deployment (interrupted during retry)", ie);
                }
            }
        }
        
        // Should never reach here
        throw new TransactionError(
                "Deployment", 
                new RuntimeException("Unexpected error in retry logic")
        );
    }
    
    /**
     * Internal deployment method that performs the actual deployment workflow.
     * This method is called by deployFunctionUnit with retry logic.
     * 
     * Deploy a new version of a function unit.
     * This method orchestrates the complete deployment workflow:
     * 1. Begin database transaction
     * 2. Generate next version number
     * 3. Check version doesn't already exist
     * 4. Copy permissions from previous active version (if exists)
     * 5. Create new version record (is_active = false initially)
     * 6. Deploy BPMN to Flowable
     * 7. Store process definition in database
     * 8. Mark previous active version as inactive
     * 9. Mark new version as active
     * 10. Commit transaction
     * 
     * If any step fails, the transaction is rolled back and the previous state is maintained.
     * 
     * Requirements: 1.1 - THE System SHALL automatically generate a new Semantic_Version number
     *               1.4 - THE System SHALL prevent deployment of a Function_Unit with a version 
     *                     number that already exists
     *               4.1 - WHEN a new version is deployed, THE System SHALL copy all permissions 
     *                     from the previous Active_Version
     *               7.1 - WHEN a deployment is initiated, THE System SHALL create a new version 
     *                     record automatically
     *               7.2 - WHEN a new version is created, THE System SHALL mark it as Active_Version 
     *                     immediately
     *               7.3 - WHEN a new version becomes active, THE System SHALL mark the previous 
     *                     Active_Version as Inactive_Version
     *               7.4 - WHEN deploying to Flowable, THE System SHALL use a version-specific 
     *                     process definition key
     *               7.5 - WHEN a deployment completes, THE System SHALL ensure the new version 
     *                     is stored in the database before marking it active
     *               7.6 - IF a deployment fails, THEN THE System SHALL rollback the version 
     *                     creation and maintain the previous Active_Version
     *               12.5 - THE System SHALL store the process definition key in the 
     *                      dw_process_definitions table linked to the specific version
     * 
     * @param functionUnitName the name of the function unit to deploy
     * @param bpmnXml the BPMN XML content
     * @param changeType the type of change: "major", "minor", or "patch"
     * @param metadata additional metadata for the function unit
     * @return the deployment result containing version information
     * @throws VersionValidationError if the version already exists or validation fails
     * @throws TransactionError if the deployment transaction fails
     */
    @Transactional
    private DeploymentResult deployFunctionUnitInternal(
            String functionUnitName, 
            String bpmnXml, 
            String changeType,
            Map<String, Object> metadata) {
        
        log.info("Starting deployment for function unit: {} with change type: {}", 
                functionUnitName, changeType);
        
        // Validate inputs
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        ValidationUtils.validateBpmnXml(bpmnXml);
        ValidationUtils.validateChangeType(changeType);
        
        try {
            // Step 1: Generate next version number
            String nextVersion = versionService.generateNextVersion(functionUnitName, changeType);
            log.debug("Generated next version: {}", nextVersion);
            
            // Step 2: Check version doesn't already exist
            if (versionService.versionExists(functionUnitName, nextVersion)) {
                log.error("Version {} already exists for function unit: {}", 
                        nextVersion, functionUnitName);
                throw VersionValidationError.duplicateVersion(functionUnitName, nextVersion);
            }
            
            // Step 3: Get previous active version (if exists) for permission copying
            Optional<FunctionUnit> previousActiveVersion = Optional.empty();
            try {
                previousActiveVersion = Optional.of(versionService.getActiveVersion(functionUnitName));
                log.debug("Found previous active version: {}", previousActiveVersion.get().getVersion());
            } catch (Exception e) {
                log.debug("No previous active version found for function unit: {}", functionUnitName);
            }
            
            // Step 4: Create new version record (is_active = false initially)
            FunctionUnit newVersion = FunctionUnit.builder()
                    .name(functionUnitName)
                    .version(nextVersion)
                    .isActive(false)  // Will be activated after successful deployment
                    .deployedAt(Instant.now())
                    .previousVersion(previousActiveVersion.orElse(null))
                    .build();
            
            // Save the new version
            newVersion = functionUnitRepository.save(newVersion);
            log.info("Created new version record: {} v{} (ID: {})", 
                    functionUnitName, nextVersion, newVersion.getId());
            
            // Step 5: Deploy BPMN to Flowable
            String processDefinitionKey;
            try {
                processDefinitionKey = deployBPMNToFlowable(functionUnitName, nextVersion, bpmnXml);
                log.debug("BPMN deployed to Flowable with key: {}", processDefinitionKey);
            } catch (Exception e) {
                log.error("Failed to deploy BPMN to Flowable, rolling back transaction", e);
                throw new TransactionError("BPMN deployment to Flowable", e);
            }
            
            // Step 6: Store process definition in database
            ProcessDefinition processDefinition = ProcessDefinition.builder()
                    .functionUnit(newVersion)
                    .bpmnXml(bpmnXml)
                    .build();
            newVersion.setProcessDefinition(processDefinition);
            newVersion = functionUnitRepository.save(newVersion);
            log.debug("Stored process definition in database for version: {}", newVersion.getId());
            
            // Step 7: Copy permissions from previous active version (if exists)
            if (previousActiveVersion.isPresent()) {
                try {
                    int copiedPermissions = permissionService.copyPermissions(
                            previousActiveVersion.get().getId(), 
                            newVersion.getId());
                    log.debug("Copied {} permissions from previous version", copiedPermissions);
                } catch (Exception e) {
                    log.error("Failed to copy permissions, rolling back transaction", e);
                    throw new TransactionError("Permission copying", e);
                }
            } else {
                log.debug("No previous version exists, skipping permission copying");
            }
            
            // Step 8: Activate the new version (this also deactivates previous versions)
            try {
                versionService.activateVersion(newVersion.getId());
                log.debug("Activated new version: {}", newVersion.getId());
            } catch (Exception e) {
                log.error("Failed to activate new version, rolling back transaction", e);
                throw new TransactionError("Version activation", e);
            }
            
            // Refresh to get updated state
            newVersion = functionUnitRepository.findById(newVersion.getId())
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve deployed version"));
            
            log.info("Successfully deployed function unit: {} v{} (ID: {})", 
                    functionUnitName, nextVersion, newVersion.getId());
            
            // Return deployment result
            return DeploymentResult.builder()
                    .success(true)
                    .versionId(newVersion.getId())
                    .version(nextVersion)
                    .processDefinitionKey(processDefinitionKey)
                    .deployedAt(newVersion.getDeployedAt())
                    .build();
            
        } catch (VersionValidationError | TransactionError e) {
            // Re-throw validation and transaction errors
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during deployment for function unit: {}", 
                    functionUnitName, e);
            throw new TransactionError("Function unit deployment", e);
        }
    }
}

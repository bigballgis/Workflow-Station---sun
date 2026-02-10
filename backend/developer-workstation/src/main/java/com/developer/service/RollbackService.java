package com.developer.service;

import com.developer.dto.RollbackImpact;
import com.developer.dto.RollbackResult;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessInstanceRepository;
import com.developer.util.ValidationUtils;
import com.platform.common.exception.StateError;
import com.platform.common.exception.TransactionError;
import com.platform.common.exception.VersionValidationError;
import com.platform.common.version.SemanticVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for rollback operations.
 * Handles rollback to previous versions, including version deletion and process instance cleanup.
 * 
 * Requirements: 6.2, 6.3, 6.4, 6.5, 6.6, 6.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollbackService {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final VersionService versionService;
    
    /**
     * Calculate the impact of rolling back to a specific version.
     * This method determines which versions and process instances will be deleted.
     * 
     * Requirements: 6.3 - WHEN a rollback is executed, THE System SHALL delete all versions 
     *                     newer than the selected version
     *               6.4 - WHEN a rollback deletes versions, THE System SHALL also delete all 
     *                     Process_Instance records associated with those versions
     *               6.5 - WHEN a rollback is initiated, THE System SHALL display a warning 
     *                     about the destructive nature of the operation
     * 
     * @param versionId the ID of the version to rollback to
     * @return RollbackImpact containing information about what will be deleted
     */
    @Transactional(readOnly = true)
    public RollbackImpact calculateRollbackImpact(Long versionId) {
        log.info("Calculating rollback impact for version ID: {}", versionId);
        
        // Validate input
        ValidationUtils.validateVersionId(versionId);
        
        try {
            // Find the target version
            FunctionUnit targetVersion = functionUnitRepository.findById(versionId)
                    .orElseThrow(() -> {
                        log.error("Version with ID {} not found", versionId);
                        return new ResourceNotFoundException("FunctionUnit", versionId.toString());
                    });
            
            String functionUnitName = targetVersion.getName();
            String targetVersionString = targetVersion.getVersion();
            log.debug("Target version: {} v{}", functionUnitName, targetVersionString);
            
            // Check if target version is already active
            if (targetVersion.getIsActive()) {
                log.warn("Target version {} is already active", targetVersionString);
                throw VersionValidationError.invalidRollback(
                        targetVersionString,
                        String.format("Version %s is already active. No rollback needed.", targetVersionString)
                );
            }
            
            // Get all versions of this function unit
            List<FunctionUnit> allVersions = versionService.getVersionHistory(functionUnitName);
            log.debug("Found {} total versions for function unit: {}", allVersions.size(), functionUnitName);
            
            // Parse target version for comparison
            SemanticVersion targetSemanticVersion = SemanticVersion.parse(targetVersionString);
            
            // Find versions to delete (all versions > target version)
            List<FunctionUnit> versionsToDelete = new ArrayList<>();
            for (FunctionUnit version : allVersions) {
                SemanticVersion currentSemanticVersion = SemanticVersion.parse(version.getVersion());
                if (currentSemanticVersion.greaterThan(targetSemanticVersion)) {
                    versionsToDelete.add(version);
                }
            }
            
            log.debug("Found {} versions to delete", versionsToDelete.size());
            
            // Calculate total process instances to delete
            long totalProcessInstances = 0;
            for (FunctionUnit version : versionsToDelete) {
                long count = processInstanceRepository.countByFunctionUnitVersionId(version.getId());
                totalProcessInstances += count;
                log.debug("Version {} (ID: {}) has {} process instances", 
                        version.getVersion(), version.getId(), count);
            }
            
            // Build warning message
            String warningMessage = String.format(
                    "WARNING: This rollback operation will DELETE %d version(s) and %d process instance(s). " +
                    "This action is IRREVERSIBLE. Please confirm you want to proceed.",
                    versionsToDelete.size(), totalProcessInstances
            );
            
            RollbackImpact impact = RollbackImpact.builder()
                    .targetVersion(targetVersionString)
                    .targetVersionId(versionId)
                    .versionsToDelete(versionsToDelete.stream()
                            .map(FunctionUnit::getVersion)
                            .collect(Collectors.toList()))
                    .versionIdsToDelete(versionsToDelete.stream()
                            .map(FunctionUnit::getId)
                            .collect(Collectors.toList()))
                    .totalProcessInstancesToDelete(totalProcessInstances)
                    .warningMessage(warningMessage)
                    .canProceed(true)
                    .build();
            
            log.info("Rollback impact calculated: {} versions and {} process instances will be deleted",
                    versionsToDelete.size(), totalProcessInstances);
            
            return impact;
            
        } catch (ResourceNotFoundException | VersionValidationError e) {
            // Re-throw these exceptions without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Failed to calculate rollback impact for version ID: {}", versionId, e);
            throw new RuntimeException(
                "Failed to calculate rollback impact: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Rollback to a specific version of a function unit.
     * This operation is destructive and atomic - it deletes all newer versions and their process instances.
     * 
     * Requirements: 6.2 - WHEN a rollback is executed, THE System SHALL mark the selected version 
     *                     as the Active_Version
     *               6.3 - WHEN a rollback is executed, THE System SHALL delete all versions newer 
     *                     than the selected version
     *               6.4 - WHEN a rollback deletes versions, THE System SHALL also delete all 
     *                     Process_Instance records associated with those versions
     *               6.6 - WHEN a rollback is confirmed, THE System SHALL execute all deletions 
     *                     atomically within a transaction
     *               6.7 - IF a rollback transaction fails, THEN THE System SHALL restore the 
     *                     previous state completely
     * 
     * @param versionId the ID of the version to rollback to
     * @return RollbackResult containing information about the rollback operation
     * @throws ResourceNotFoundException if the target version doesn't exist
     * @throws VersionValidationError if the target version is invalid
     * @throws TransactionError if the rollback transaction fails
     */
    @Transactional
    public RollbackResult rollbackToVersion(Long versionId) {
        log.info("Starting rollback to version ID: {}", versionId);
        
        // Validate input
        ValidationUtils.validateVersionId(versionId);
        
        try {
            // Step 1: Calculate rollback impact
            RollbackImpact impact = calculateRollbackImpact(versionId);
            
            // Step 2: Validate rollback can proceed
            if (!impact.isCanProceed()) {
                log.error("Rollback cannot proceed: {}", impact.getErrorMessage());
                throw VersionValidationError.invalidRollback(
                        impact.getTargetVersion(), 
                        impact.getErrorMessage()
                );
            }
            
            log.info("Rollback impact: {} versions and {} process instances will be deleted",
                    impact.getVersionsToDelete().size(), 
                    impact.getTotalProcessInstancesToDelete());
            
            // Step 3: Delete process instances bound to versions being deleted
            long deletedProcessCount = 0;
            for (Long versionIdToDelete : impact.getVersionIdsToDelete()) {
                long count = processInstanceRepository.countByFunctionUnitVersionId(versionIdToDelete);
                processInstanceRepository.deleteByFunctionUnitVersionId(versionIdToDelete);
                deletedProcessCount += count;
                log.debug("Deleted {} process instances for version ID: {}", count, versionIdToDelete);
            }
            
            log.info("Deleted {} process instances", deletedProcessCount);
            
            // Step 4: Delete versions newer than target version
            List<String> deletedVersions = new ArrayList<>(impact.getVersionsToDelete());
            for (Long versionIdToDelete : impact.getVersionIdsToDelete()) {
                functionUnitRepository.deleteById(versionIdToDelete);
                log.debug("Deleted version ID: {}", versionIdToDelete);
            }
            
            log.info("Deleted {} versions", deletedVersions.size());
            
            // Step 5: Mark target version as active (this also deactivates all other versions)
            versionService.activateVersion(versionId);
            log.info("Activated target version ID: {}", versionId);
            
            // Step 6: Build success result
            RollbackResult result = RollbackResult.builder()
                    .success(true)
                    .rolledBackToVersion(impact.getTargetVersion())
                    .rolledBackToVersionId(versionId)
                    .deletedVersions(deletedVersions)
                    .deletedProcessCount(deletedProcessCount)
                    .build();
            
            log.info("Successfully rolled back to version {} (ID: {}). Deleted {} versions and {} process instances",
                    impact.getTargetVersion(), versionId, deletedVersions.size(), deletedProcessCount);
            
            return result;
            
        } catch (ResourceNotFoundException e) {
            // Re-throw ResourceNotFoundException without wrapping
            log.error("Rollback failed: target version not found", e);
            throw e;
        } catch (VersionValidationError e) {
            log.error("Rollback validation failed for version ID: {}", versionId, e);
            throw e;
        } catch (Exception e) {
            log.error("Rollback transaction failed for version ID: {}", versionId, e);
            
            // Build failure result
            RollbackResult result = RollbackResult.builder()
                    .success(false)
                    .rolledBackToVersionId(versionId)
                    .errorMessage("Rollback transaction failed: " + e.getMessage())
                    .errorDetails(e.toString())
                    .build();
            
            // Throw TransactionError to trigger rollback
            throw new TransactionError("Rollback operation", e);
        }
    }
}

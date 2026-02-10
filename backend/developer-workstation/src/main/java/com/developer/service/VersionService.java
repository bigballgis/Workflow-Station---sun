package com.developer.service;

import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.util.ValidationUtils;
import com.platform.common.exception.StateError;
import com.platform.common.version.SemanticVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service class for version management operations.
 * Handles version queries, activation, and history tracking for function units.
 * 
 * Requirements: 2.5, 3.3, 1.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionService {
    
    private final FunctionUnitRepository functionUnitRepository;
    
    /**
     * Validate that exactly one version is active for a function unit.
     * This is a critical system invariant that must be maintained.
     * 
     * Requirements: 2.3 - THE System SHALL ensure that exactly one version of each 
     *                     Function_Unit is active at any time
     *               11.6 - THE System SHALL ensure that the Active_Version flag is never 
     *                      set on multiple versions simultaneously
     * 
     * @param functionUnitName the name of the function unit to validate
     * @throws StateError if the invariant is violated (zero or multiple active versions)
     */
    private void validateSingleActiveVersion(String functionUnitName) {
        log.debug("Validating single active version invariant for function unit: {}", 
                functionUnitName);
        
        try {
            List<FunctionUnit> activeVersions = functionUnitRepository
                    .findByNameAndIsActive(functionUnitName, true)
                    .stream()
                    .toList();
            
            if (activeVersions.isEmpty()) {
                log.error("No active version found for function unit: {}", functionUnitName);
                throw StateError.noActiveVersion(functionUnitName);
            }
            
            if (activeVersions.size() > 1) {
                log.error("Multiple active versions found for function unit: {}. Count: {}", 
                        functionUnitName, activeVersions.size());
                throw StateError.multipleActiveVersions(
                        functionUnitName, 
                        activeVersions.stream()
                                .map(FunctionUnit::getVersion)
                                .collect(Collectors.toList())
                );
            }
            
            log.debug("Single active version invariant validated for function unit: {}", 
                    functionUnitName);
            
        } catch (StateError e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate single active version invariant for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to validate single active version invariant: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get the active version for a function unit.
     * 
     * Requirements: 2.5 - THE System SHALL return only the version marked as Active_Version
     * 
     * @param functionUnitName the name of the function unit
     * @return the active version of the function unit
     * @throws ResourceNotFoundException if no active version is found
     */
    @Transactional(readOnly = true)
    public FunctionUnit getActiveVersion(String functionUnitName) {
        log.info("Retrieving active version for function unit: {}", functionUnitName);
        
        // Validate input
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        
        try {
            Optional<FunctionUnit> activeVersion = functionUnitRepository
                    .findByNameAndIsActive(functionUnitName, true);
            
            if (activeVersion.isEmpty()) {
                log.error("No active version found for function unit: {}", functionUnitName);
                throw new ResourceNotFoundException(
                    "FunctionUnit", functionUnitName
                );
            }
            
            log.info("Found active version {} for function unit: {}", 
                    activeVersion.get().getVersion(), functionUnitName);
            return activeVersion.get();
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve active version for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to retrieve active version: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get version history for a function unit, ordered by version number descending.
     * 
     * Requirements: 3.3 - THE System SHALL display all versions of that Function_Unit 
     *                     ordered by version number
     * 
     * @param functionUnitName the name of the function unit
     * @return list of all versions ordered by semantic version (descending)
     */
    @Transactional(readOnly = true)
    public List<FunctionUnit> getVersionHistory(String functionUnitName) {
        log.info("Retrieving version history for function unit: {}", functionUnitName);
        
        // Validate input
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        
        try {
            List<FunctionUnit> versions = functionUnitRepository
                    .findByNameOrderByVersionDesc(functionUnitName);
            
            log.info("Found {} versions for function unit: {}", 
                    versions.size(), functionUnitName);
            return versions;
            
        } catch (Exception e) {
            log.error("Failed to retrieve version history for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to retrieve version history: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Check if a specific version exists for a function unit.
     * 
     * Requirements: 1.4 - THE System SHALL prevent deployment of a Function_Unit 
     *                     with a version number that already exists
     * 
     * @param functionUnitName the name of the function unit
     * @param version the version number to check
     * @return true if the version exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean versionExists(String functionUnitName, String version) {
        log.debug("Checking if version {} exists for function unit: {}", 
                version, functionUnitName);
        
        // Validate inputs
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        ValidationUtils.validateVersionFormat(version);
        
        try {
            boolean exists = functionUnitRepository
                    .existsByNameAndVersion(functionUnitName, version);
            
            log.debug("Version {} {} for function unit: {}", 
                    version, exists ? "exists" : "does not exist", functionUnitName);
            return exists;
            
        } catch (Exception e) {
            log.error("Failed to check version existence for function unit: {} version: {}", 
                    functionUnitName, version, e);
            throw new RuntimeException(
                "Failed to check version existence: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Activate a specific version of a function unit.
     * Marks the target version as active and deactivates all other versions of the same function unit.
     * This operation is atomic - all changes occur within a single transaction.
     * 
     * Requirements: 2.1 - WHEN a new version is deployed, THE System SHALL mark it as the Active_Version
     *               2.2 - WHEN a new version becomes active, THE System SHALL mark all previous versions 
     *                     of that Function_Unit as Inactive_Version
     * 
     * @param versionId the ID of the version to activate
     * @throws ResourceNotFoundException if the version does not exist
     * @throws RuntimeException if the activation fails
     */
    @Transactional
    public void activateVersion(Long versionId) {
        log.info("Activating version with ID: {}", versionId);
        
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
            log.debug("Target version found: {} version {}", functionUnitName, targetVersion.getVersion());
            
            // Get all versions of this function unit
            List<FunctionUnit> allVersions = functionUnitRepository
                    .findByNameOrderByVersionDesc(functionUnitName);
            
            log.debug("Found {} total versions for function unit: {}", allVersions.size(), functionUnitName);
            
            // Deactivate all versions
            for (FunctionUnit version : allVersions) {
                if (version.getIsActive()) {
                    log.debug("Deactivating version {} (ID: {})", version.getVersion(), version.getId());
                    version.setIsActive(false);
                    functionUnitRepository.save(version);
                }
            }
            
            // Activate the target version
            targetVersion.setIsActive(true);
            functionUnitRepository.save(targetVersion);
            
            log.info("Successfully activated version {} (ID: {}) for function unit: {}", 
                    targetVersion.getVersion(), versionId, functionUnitName);
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to activate version with ID: {}", versionId, e);
            throw new RuntimeException(
                "Failed to activate version: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get a function unit by name without specifying version.
     * Returns the active version transparently for backward compatibility with legacy code.
     * 
     * Requirements: 9.4 - THE System SHALL support queries for Function_Units without specifying version
     *               9.5 - WHEN legacy code queries Function_Units, THE System SHALL return Active_Version data transparently
     * 
     * @param functionUnitName the name of the function unit
     * @return the active version of the function unit
     * @throws ResourceNotFoundException if no active version is found
     */
    @Transactional(readOnly = true)
    public FunctionUnit getFunctionUnit(String functionUnitName) {
        log.info("Retrieving function unit by name (backward compatible query): {}", functionUnitName);
        
        // Delegate to getActiveVersion for backward compatibility
        // This ensures legacy code that doesn't specify version gets the active version
        return getActiveVersion(functionUnitName);
    }
    
    /**
     * Generate the next version number for a function unit based on the change type.
     * 
     * Requirements: 1.1 - THE System SHALL automatically generate a new Semantic_Version number
     *               1.2 - THE System SHALL increment the version based on the previous highest version
     *               1.3 - WHEN a Function_Unit is deployed for the first time, 
     *                     THE System SHALL assign version 1.0.0
     * 
     * @param functionUnitName the name of the function unit
     * @param changeType the type of change: "major", "minor", or "patch"
     * @return the next version number as a string
     * @throws IllegalArgumentException if changeType is invalid
     */
    @Transactional(readOnly = true)
    public String generateNextVersion(String functionUnitName, String changeType) {
        log.info("Generating next version for function unit: {} with change type: {}", 
                functionUnitName, changeType);
        
        // Validate inputs
        ValidationUtils.validateFunctionUnitName(functionUnitName);
        ValidationUtils.validateChangeType(changeType);
        
        try {
            // Get version history to find the highest version
            List<FunctionUnit> versions = functionUnitRepository
                    .findByNameOrderByVersionDesc(functionUnitName);
            
            // Handle first deployment case - return 1.0.0
            if (versions.isEmpty()) {
                log.info("No existing versions found for function unit: {}. Returning 1.0.0", 
                        functionUnitName);
                return "1.0.0";
            }
            
            // Get the highest version (first in the list since it's ordered descending)
            String currentVersionString = versions.get(0).getVersion();
            log.debug("Current highest version for function unit {}: {}", 
                    functionUnitName, currentVersionString);
            
            // Parse the current version
            SemanticVersion currentVersion = SemanticVersion.parse(currentVersionString);
            
            // Increment based on change type
            SemanticVersion nextVersion;
            switch (changeType) {
                case "major":
                    nextVersion = currentVersion.incrementMajor();
                    break;
                case "minor":
                    nextVersion = currentVersion.incrementMinor();
                    break;
                case "patch":
                    nextVersion = currentVersion.incrementPatch();
                    break;
                default:
                    // This should never happen due to validation above, but included for safety
                    throw new IllegalArgumentException("Invalid change type: " + changeType);
            }
            
            String nextVersionString = nextVersion.toString();
            log.info("Generated next version for function unit {}: {} -> {}", 
                    functionUnitName, currentVersionString, nextVersionString);
            
            return nextVersionString;
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation errors
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate next version for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to generate next version: " + e.getMessage(), e
            );
        }
    }
}

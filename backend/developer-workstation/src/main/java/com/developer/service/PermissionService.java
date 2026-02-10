package com.developer.service;

import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitAccess;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitAccessRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for managing permissions across function unit versions.
 * Handles permission inheritance, isolation, and version-specific permission operations.
 * 
 * Requirements: 4.1, 4.3, 4.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {
    
    private final FunctionUnitAccessRepository accessRepository;
    private final FunctionUnitRepository functionUnitRepository;
    
    /**
     * Copy permissions from one version to another.
     * Used during deployment to inherit permissions from the previous active version.
     * 
     * Requirements: 4.1 - WHEN a new version is deployed, THE System SHALL copy all permissions 
     *                     from the previous Active_Version
     * 
     * @param sourceVersionId the ID of the source version to copy permissions from
     * @param targetVersionId the ID of the target version to copy permissions to
     * @return the number of permissions copied
     * @throws ResourceNotFoundException if either version does not exist
     */
    @Transactional
    public int copyPermissions(Long sourceVersionId, Long targetVersionId) {
        log.info("Copying permissions from version {} to version {}", sourceVersionId, targetVersionId);
        
        // Validate inputs
        ValidationUtils.validateVersionId(sourceVersionId);
        ValidationUtils.validateVersionId(targetVersionId);
        
        try {
            // Verify both versions exist
            FunctionUnit sourceVersion = functionUnitRepository.findById(sourceVersionId)
                    .orElseThrow(() -> {
                        log.error("Source version with ID {} not found", sourceVersionId);
                        return new ResourceNotFoundException("FunctionUnit", sourceVersionId.toString());
                    });
            
            FunctionUnit targetVersion = functionUnitRepository.findById(targetVersionId)
                    .orElseThrow(() -> {
                        log.error("Target version with ID {} not found", targetVersionId);
                        return new ResourceNotFoundException("FunctionUnit", targetVersionId.toString());
                    });
            
            log.debug("Source version: {} v{}, Target version: {} v{}", 
                    sourceVersion.getName(), sourceVersion.getVersion(),
                    targetVersion.getName(), targetVersion.getVersion());
            
            // Get all permissions from source version
            List<FunctionUnitAccess> sourcePermissions = accessRepository
                    .findByFunctionUnitId(sourceVersionId);
            
            log.debug("Found {} permissions to copy from source version", sourcePermissions.size());
            
            // Create new permission records for target version
            List<FunctionUnitAccess> targetPermissions = sourcePermissions.stream()
                    .map(sourcePermission -> FunctionUnitAccess.builder()
                            .functionUnit(targetVersion)
                            .accessType(sourcePermission.getAccessType())
                            .targetType(sourcePermission.getTargetType())
                            .targetId(sourcePermission.getTargetId())
                            .build())
                    .collect(Collectors.toList());
            
            // Save all new permissions
            List<FunctionUnitAccess> savedPermissions = accessRepository.saveAll(targetPermissions);
            
            log.info("Successfully copied {} permissions from version {} to version {}", 
                    savedPermissions.size(), sourceVersionId, targetVersionId);
            
            return savedPermissions.size();
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to copy permissions from version {} to version {}", 
                    sourceVersionId, targetVersionId, e);
            throw new RuntimeException(
                "Failed to copy permissions: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get all permissions for a specific version.
     * 
     * Requirements: 4.4 - THE System SHALL maintain separate permission records for each version
     * 
     * @param versionId the ID of the version
     * @return list of permissions for the version
     * @throws ResourceNotFoundException if the version does not exist
     */
    @Transactional(readOnly = true)
    public List<FunctionUnitAccess> getPermissions(Long versionId) {
        log.info("Retrieving permissions for version {}", versionId);
        
        // Validate input
        ValidationUtils.validateVersionId(versionId);
        
        try {
            // Verify version exists
            FunctionUnit version = functionUnitRepository.findById(versionId)
                    .orElseThrow(() -> {
                        log.error("Version with ID {} not found", versionId);
                        return new ResourceNotFoundException("FunctionUnit", versionId.toString());
                    });
            
            log.debug("Retrieving permissions for version: {} v{}", 
                    version.getName(), version.getVersion());
            
            List<FunctionUnitAccess> permissions = accessRepository
                    .findByFunctionUnitId(versionId);
            
            log.info("Found {} permissions for version {}", permissions.size(), versionId);
            
            return permissions;
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve permissions for version {}", versionId, e);
            throw new RuntimeException(
                "Failed to retrieve permissions: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Update permissions for a version.
     * Only allows updates to the active version to ensure permission isolation.
     * 
     * Requirements: 4.3 - WHEN permissions are modified, THE System SHALL apply changes 
     *                     only to the Active_Version
     * 
     * @param versionId the ID of the version to update
     * @param newPermissions the new list of permissions
     * @return the updated list of permissions
     * @throws ResourceNotFoundException if the version does not exist
     * @throws IllegalStateException if the version is not active
     */
    @Transactional
    public List<FunctionUnitAccess> updatePermissions(Long versionId, List<FunctionUnitAccess> newPermissions) {
        log.info("Updating permissions for version {}", versionId);
        
        // Validate inputs
        ValidationUtils.validateVersionId(versionId);
        if (newPermissions == null) {
            throw new IllegalArgumentException("New permissions list cannot be null");
        }
        
        try {
            // Verify version exists and is active
            FunctionUnit version = functionUnitRepository.findById(versionId)
                    .orElseThrow(() -> {
                        log.error("Version with ID {} not found", versionId);
                        return new ResourceNotFoundException("FunctionUnit", versionId.toString());
                    });
            
            if (!version.getIsActive()) {
                log.error("Cannot update permissions for inactive version {} v{}", 
                        version.getName(), version.getVersion());
                throw new IllegalStateException(
                    String.format("Cannot update permissions for inactive version: %s v%s", 
                            version.getName(), version.getVersion())
                );
            }
            
            log.debug("Updating permissions for active version: {} v{}", 
                    version.getName(), version.getVersion());
            
            // Delete existing permissions
            accessRepository.deleteByFunctionUnitId(versionId);
            log.debug("Deleted existing permissions for version {}", versionId);
            
            // Set the function unit reference for all new permissions
            newPermissions.forEach(permission -> permission.setFunctionUnit(version));
            
            // Save new permissions
            List<FunctionUnitAccess> savedPermissions = accessRepository.saveAll(newPermissions);
            
            log.info("Successfully updated permissions for version {}. New count: {}", 
                    versionId, savedPermissions.size());
            
            return savedPermissions;
            
        } catch (ResourceNotFoundException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update permissions for version {}", versionId, e);
            throw new RuntimeException(
                "Failed to update permissions: " + e.getMessage(), e
            );
        }
    }
}

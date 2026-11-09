package com.developer.component;

import com.developer.dto.DevGroupAssignmentRequest;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.MyDevGroupsResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.dto.VersionCompareResponse;
import com.developer.entity.FunctionUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Function unit component interface
 */
public interface FunctionUnitComponent {
    
    /**
     * Create a function unit
     */
    FunctionUnit create(FunctionUnitRequest request);
    
    /**
     * Update a function unit
     */
    FunctionUnit update(Long id, FunctionUnitRequest request);
    
    /**
     * Delete a function unit
     */
    void delete(Long id);

    /**
     * Restore an archived function unit back to DRAFT status.
     */
    FunctionUnit restore(Long id);
    
    /**
     * Get a function unit by ID
     */
    FunctionUnit getById(Long id);
    
    /**
     * Get function unit response DTO by ID
     */
    FunctionUnitResponse getByIdAsResponse(Long id);
    
    /**
     * Paginated query of function units with optional tag filter (AND semantics).
     */
    Page<FunctionUnitResponse> list(String name, String status, List<String> tags, Pageable pageable);

    /**
     * Returns all distinct tags across all enabled function units.
     */
    List<String> getAllTags();
    
    /**
     * Publish a function unit
     */
    FunctionUnit publish(Long id, String changeLog);

    /**
     * Create the version snapshot used by one-click deploy. HTTP deploy already
     * required {@code FUNCTION_UNIT_PUBLISH}; this must not use JWT {@code hasAnyRole}.
     */
    FunctionUnit publishForDeployment(Long id, String changeLog);
    
    /**
     * Clone a function unit into one workspace: the caller can see the source (D-35) and may create in the target.
     */
    FunctionUnit clone(Long id, String newName, String targetGroupId);
    
    /**
     * Validate function unit integrity
     */
    ValidationResult validate(Long id);
    
    /**
     * Get version history
     */
    List<VersionResponse> getVersionHistory(Long functionUnitId);

    /**
     * Rollback to a specific historical version
     */
    FunctionUnit rollback(Long functionUnitId, Long versionId);

    /**
     * Compare snapshot differences between two versions
     */
    Map<String, Object> compareVersions(Long functionUnitId, Long versionId1, Long versionId2);

    /** Typed, module-oriented comparison with the same workspace and ownership checks. */
    VersionCompareResponse compareVersionsV2(Long functionUnitId, Long versionId1, Long versionId2);

    /**
     * Export the snapshot content of a specific historical version
     */
    byte[] exportVersion(Long functionUnitId, Long versionId);
    
    /**
     * Check if a name already exists
     */
    boolean existsByName(String name);
    
    /**
     * Check if a name exists (excluding a specified ID)
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Move the function unit to exactly one other workspace: the caller manages it where it is and may create in the
     * target (platform access model §6.2, §6.3).
     */
    void replaceDevGroupAssignments(Long functionUnitId, DevGroupAssignmentRequest request);

    /**
     * List of sys_virtual_groups.id currently assigned to this function unit
     */
    java.util.List<String> getDevGroupAssignments(Long functionUnitId);

    /**
     * The current user's selectable teams (for the entry dialog / header switcher), plus
     * whether they may view all function units (ADMIN) and the built-in Public group id.
     */
    MyDevGroupsResponse getMyDevGroups();
}

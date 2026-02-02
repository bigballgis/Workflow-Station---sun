package com.admin.helper;

import com.admin.repository.PermissionRepository;
import com.platform.security.entity.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Helper service for permission operations.
 * 
 * <p>This service provides utility methods for working with Permission entities from platform-security,
 * including permission parsing, matching, and wildcard detection. It handles the complexity of
 * extracting resource and action information from Permission entities.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Extract resource and action from Permission entities</li>
 *   <li>Match permissions against resource and action patterns</li>
 *   <li>Detect wildcard permissions</li>
 *   <li>Permission validation and comparison</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Autowired
 * private PermissionHelper permissionHelper;
 * 
 * // Get resource and action
 * String resource = permissionHelper.getResource(permission);
 * String action = permissionHelper.getAction(permission);
 * 
 * // Check if permission matches a resource and action
 * if (permissionHelper.matches(permission, "user", "read")) {
 *     // Permission matches
 * }
 * 
 * // Check if permission is a wildcard
 * if (permissionHelper.isWildcard(permission)) {
 *     // Handle wildcard permission
 * }
 * }</pre>
 * 
 * @author Entity Architecture Alignment
 * @version 1.0
 * @see Permission
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionHelper {
    
    private final PermissionRepository permissionRepository;
    
    /**
     * Gets the resource from a Permission entity.
     * 
     * <p>The Permission entity from platform-security has a resourceType field that represents
     * the resource being protected. This method extracts and returns that field.</p>
     * 
     * <p>If the Permission entity has a code field in a format like "resource:action",
     * this method will return the resourceType field directly. For more complex parsing,
     * consider using the code field.</p>
     * 
     * @param permission the Permission entity
     * @return the resource string, or null if the permission is null or has no resourceType
     */
    public String getResource(Permission permission) {
        if (permission == null) {
            return null;
        }
        
        // Return the resourceType field directly
        String resourceType = permission.getResourceType();
        if (resourceType != null && !resourceType.isEmpty()) {
            return resourceType;
        }
        
        // Fallback: try to parse from code if resourceType is not set
        String code = permission.getCode();
        if (code != null && code.contains(":")) {
            String[] parts = code.split(":", 2);
            return parts[0];
        }
        
        log.debug("Permission {} has no resource information", permission.getId());
        return null;
    }
    
    /**
     * Gets the action from a Permission entity.
     * 
     * <p>The Permission entity from platform-security has an action field that represents
     * the operation being performed on the resource. This method extracts and returns that field.</p>
     * 
     * <p>If the Permission entity has a code field in a format like "resource:action",
     * this method will return the action field directly. For more complex parsing,
     * consider using the code field.</p>
     * 
     * @param permission the Permission entity
     * @return the action string, or null if the permission is null or has no action
     */
    public String getAction(Permission permission) {
        if (permission == null) {
            return null;
        }
        
        // Return the action field directly
        String action = permission.getAction();
        if (action != null && !action.isEmpty()) {
            return action;
        }
        
        // Fallback: try to parse from code if action is not set
        String code = permission.getCode();
        if (code != null && code.contains(":")) {
            String[] parts = code.split(":", 2);
            if (parts.length > 1) {
                return parts[1];
            }
        }
        
        log.debug("Permission {} has no action information", permission.getId());
        return null;
    }
    
    /**
     * Checks if a Permission matches a specific resource and action.
     * 
     * <p>This method compares the permission's resource and action against the provided
     * resource and action strings. It supports exact matching and wildcard matching.</p>
     * 
     * <p><strong>Matching Rules:</strong></p>
     * <ul>
     *   <li>Exact match: permission resource and action exactly match the provided values</li>
     *   <li>Wildcard resource: permission resource is "*" matches any resource</li>
     *   <li>Wildcard action: permission action is "*" matches any action</li>
     *   <li>Resource prefix: permission resource ends with ".*" matches resources with that prefix</li>
     * </ul>
     * 
     * @param permission the Permission entity to check
     * @param resource the resource to match against
     * @param action the action to match against
     * @return true if the permission matches the resource and action, false otherwise
     */
    public boolean matches(Permission permission, String resource, String action) {
        if (permission == null || resource == null || action == null) {
            return false;
        }
        
        String permResource = getResource(permission);
        String permAction = getAction(permission);
        
        if (permResource == null || permAction == null) {
            return false;
        }
        
        // Check for wildcard matches
        boolean resourceMatches = "*".equals(permResource) 
                || permResource.equals(resource)
                || (permResource.endsWith(".*") && resource.startsWith(permResource.substring(0, permResource.length() - 2)));
        
        boolean actionMatches = "*".equals(permAction) || permAction.equals(action);
        
        return resourceMatches && actionMatches;
    }
    
    /**
     * Checks if a Permission is a wildcard permission.
     * 
     * <p>A permission is considered a wildcard if:</p>
     * <ul>
     *   <li>Its resource is "*" (matches all resources)</li>
     *   <li>Its action is "*" (matches all actions)</li>
     *   <li>Its resource ends with ".*" (matches resources with a prefix)</li>
     * </ul>
     * 
     * <p>Wildcard permissions grant broad access and should be used carefully.</p>
     * 
     * @param permission the Permission entity to check
     * @return true if the permission is a wildcard permission, false otherwise
     */
    public boolean isWildcard(Permission permission) {
        if (permission == null) {
            return false;
        }
        
        String resource = getResource(permission);
        String action = getAction(permission);
        
        if (resource == null && action == null) {
            return false;
        }
        
        // Check if resource or action contains wildcards
        boolean hasWildcardResource = resource != null && ("*".equals(resource) || resource.endsWith(".*"));
        boolean hasWildcardAction = action != null && "*".equals(action);
        
        return hasWildcardResource || hasWildcardAction;
    }
}

package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * Exception for permission/authorization failures.
 */
@Getter
public class PermissionDeniedException extends PlatformException {
    
    private final String requiredPermission;
    private final String resourceType;
    private final String resourceId;
    
    public PermissionDeniedException() {
        super(ErrorCode.PERMISSION_DENIED);
        this.requiredPermission = null;
        this.resourceType = null;
        this.resourceId = null;
    }
    
    public PermissionDeniedException(String message) {
        super(ErrorCode.PERMISSION_DENIED, message);
        this.requiredPermission = null;
        this.resourceType = null;
        this.resourceId = null;
    }
    
    public PermissionDeniedException(String requiredPermission, String resourceType, String resourceId) {
        super(ErrorCode.PERMISSION_DENIED, 
              String.format("Permission '%s' required for %s:%s", requiredPermission, resourceType, resourceId),
              Map.of("requiredPermission", requiredPermission, 
                     "resourceType", resourceType, 
                     "resourceId", resourceId));
        this.requiredPermission = requiredPermission;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public PermissionDeniedException(String requiredPermission, String message) {
        super(ErrorCode.PERMISSION_DENIED, message, Map.of("requiredPermission", requiredPermission));
        this.requiredPermission = requiredPermission;
        this.resourceType = null;
        this.resourceId = null;
    }
}

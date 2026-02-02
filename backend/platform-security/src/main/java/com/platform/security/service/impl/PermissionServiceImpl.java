package com.platform.security.service.impl;

import com.platform.cache.service.CacheService;
import com.platform.common.dto.DataFilter;
import com.platform.security.model.ApiPermission;
import com.platform.security.model.DataPermission;
import com.platform.security.entity.Permission;
import com.platform.security.entity.Role;
import com.platform.security.repository.PermissionRepository;
import com.platform.security.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PermissionService for unified permission control.
 * Validates: Requirements 4.1, 4.2, 4.4, 4.5, 4.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(PermissionRepository.class)
public class PermissionServiceImpl implements PermissionService {
    
    private static final String PERMISSION_CACHE_PREFIX = "permission:user:";
    private static final String ROLE_CACHE_PREFIX = "role:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    
    private final PermissionRepository permissionRepository;
    private final CacheService cacheService;
    
    @Override
    public boolean hasPermission(String userId, String permission) {
        if (userId == null || permission == null) {
            return false;
        }
        Set<String> userPermissions = getUserPermissions(userId);
        return userPermissions.contains(permission);
    }
    
    @Override
    public boolean hasAnyPermission(String userId, Set<String> permissions) {
        if (userId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        Set<String> userPermissions = getUserPermissions(userId);
        return permissions.stream().anyMatch(userPermissions::contains);
    }
    
    @Override
    public boolean hasAllPermissions(String userId, Set<String> permissions) {
        if (userId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        Set<String> userPermissions = getUserPermissions(userId);
        return userPermissions.containsAll(permissions);
    }
    
    @Override
    public boolean hasApiPermission(String userId, String apiPath, String method) {
        if (userId == null || apiPath == null || method == null) {
            return false;
        }
        
        Optional<ApiPermission> apiPermOpt = permissionRepository.findApiPermission(apiPath, method);
        if (apiPermOpt.isEmpty()) {
            // No specific permission configured, allow by default
            log.debug("No API permission configured for {} {}, allowing access", method, apiPath);
            return true;
        }
        
        ApiPermission apiPerm = apiPermOpt.get();
        if (!apiPerm.isEnabled()) {
            return true;
        }
        
        Set<String> userPermissions = getUserPermissions(userId);
        Set<String> userRoles = getUserRoles(userId);
        
        boolean hasRequiredPermission = checkRequiredPermissions(
                apiPerm.getRequiredPermissions(), userPermissions, apiPerm.isRequireAll());
        boolean hasRequiredRole = checkRequiredRoles(
                apiPerm.getRequiredRoles(), userRoles, apiPerm.isRequireAll());
        
        return hasRequiredPermission || hasRequiredRole;
    }
    
    @Override
    public DataFilter getDataFilter(String userId, String resourceType) {
        if (userId == null || resourceType == null) {
            return DataFilter.noFilter();
        }
        
        Optional<DataPermission> dataPermOpt = permissionRepository.findDataPermission(userId, resourceType);
        if (dataPermOpt.isEmpty() || !dataPermOpt.get().isEnabled()) {
            return DataFilter.noFilter();
        }
        
        DataPermission dataPerm = dataPermOpt.get();
        return DataFilter.of(dataPerm.getFilterExpression(), dataPerm.getFilterParameters());
    }
    
    @Override
    public List<String> getAccessibleColumns(String userId, String resourceType) {
        if (userId == null || resourceType == null) {
            return Collections.emptyList();
        }
        
        Optional<DataPermission> dataPermOpt = permissionRepository.findDataPermission(userId, resourceType);
        if (dataPermOpt.isEmpty() || !dataPermOpt.get().isEnabled()) {
            return Collections.emptyList();
        }
        
        DataPermission dataPerm = dataPermOpt.get();
        List<String> accessible = dataPerm.getAccessibleColumns();
        return accessible != null ? accessible : Collections.emptyList();
    }

    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getUserPermissions(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        
        String cacheKey = PERMISSION_CACHE_PREFIX + userId;
        Optional<Set> cached = cacheService.get(cacheKey, Set.class);
        if (cached.isPresent()) {
            return (Set<String>) cached.get();
        }
        
        Set<Permission> permissions = permissionRepository.findPermissionsByUserId(userId);
        Set<String> permissionCodes = permissions.stream()
                .filter(Permission::isEnabled)
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        
        // Also include permissions from roles
        Set<Role> roles = permissionRepository.findRolesByUserId(userId);
        for (Role role : roles) {
            if (role.isEnabled() && role.getPermissionCodes() != null) {
                permissionCodes.addAll(role.getPermissionCodes());
            }
        }
        
        cacheService.set(cacheKey, permissionCodes, CACHE_TTL);
        return permissionCodes;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getUserRoles(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        
        String cacheKey = ROLE_CACHE_PREFIX + userId;
        Optional<Set> cached = cacheService.get(cacheKey, Set.class);
        if (cached.isPresent()) {
            return (Set<String>) cached.get();
        }
        
        Set<Role> roles = permissionRepository.findRolesByUserId(userId);
        Set<String> roleCodes = roles.stream()
                .filter(Role::isEnabled)
                .map(Role::getCode)
                .collect(Collectors.toSet());
        
        cacheService.set(cacheKey, roleCodes, CACHE_TTL);
        return roleCodes;
    }
    
    @Override
    public boolean hasRole(String userId, String role) {
        if (userId == null || role == null) {
            return false;
        }
        Set<String> userRoles = getUserRoles(userId);
        return userRoles.contains(role);
    }
    
    @Override
    public void invalidateUserPermissionCache(String userId) {
        if (userId == null) {
            return;
        }
        cacheService.delete(PERMISSION_CACHE_PREFIX + userId);
        cacheService.delete(ROLE_CACHE_PREFIX + userId);
        log.info("Invalidated permission cache for user: {}", userId);
    }
    
    private boolean checkRequiredPermissions(Set<String> required, Set<String> userPerms, boolean requireAll) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (requireAll) {
            return userPerms.containsAll(required);
        }
        return required.stream().anyMatch(userPerms::contains);
    }
    
    private boolean checkRequiredRoles(Set<String> required, Set<String> userRoles, boolean requireAll) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (requireAll) {
            return userRoles.containsAll(required);
        }
        return required.stream().anyMatch(userRoles::contains);
    }
}

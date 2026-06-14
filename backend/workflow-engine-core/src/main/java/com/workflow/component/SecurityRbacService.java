package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.RoleAssignmentRequest;
import com.workflow.dto.response.PermissionCheckResult;
import com.workflow.dto.response.UserSecurityInfo;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全 RBAC 协作类
 *
 * 从 {@link SecurityManagerComponent} 拆分而来，负责角色/权限的定义、存储、查询，
 * 用户角色读写、权限判定（{@code checkPermission}/{@code hasRole}）、角色分配与撤销，
 * 以及用户安全信息聚合。纯结构搬迁，行为与原实现逐字一致。
 *
 * <p>内存缓存（{@code rolePermissions}/{@code userCache}）由本类持有；审计写入委托
 * {@link AuditManagerComponent}。
 */
@Slf4j
@Component
public class SecurityRbacService {

    private static final String USER_CACHE_PREFIX = "security:user:";
    private static final String ROLE_CACHE_PREFIX = "security:role:";
    private static final String TOKEN_CACHE_PREFIX = "security:token:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditManagerComponent auditManagerComponent;

    // 内存缓存（用于角色和权限定义）
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, UserSecurityInfo> userCache = new ConcurrentHashMap<>();

    public SecurityRbacService(StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               AuditManagerComponent auditManagerComponent) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.auditManagerComponent = auditManagerComponent;
    }

    /**
     * 检查用户权限
     */
    public PermissionCheckResult checkPermission(String username, String resource, String action) {
        log.debug("检查权限: username={}, resource={}, action={}", username, resource, action);

        try {
            // 获取用户角色
            Set<String> userRoles = getUserRoles(username);

            if (userRoles.isEmpty()) {
                return PermissionCheckResult.denied("用户没有分配任何角色");
            }

            // 构建权限标识
            String permission = resource + ":" + action;

            // 检查是否有权限
            for (String role : userRoles) {
                Set<String> permissions = getRolePermissions(role);
                if (permissions.contains(permission) || permissions.contains(resource + ":*")
                        || permissions.contains("*:*")) {
                    return PermissionCheckResult.allowed(role, permission);
                }
            }

            return PermissionCheckResult.denied("用户没有执行此操作的权限");

        } catch (Exception e) {
            log.error("权限检查失败: username={}, resource={}, action={}, error={}",
                    username, resource, action, e.getMessage(), e);
            return PermissionCheckResult.denied("权限检查过程发生错误");
        }
    }

    /**
     * 检查用户是否有指定角色
     */
    public boolean hasRole(String username, String role) {
        Set<String> userRoles = getUserRoles(username);
        return userRoles.contains(role);
    }

    /**
     * 检查用户是否有任意一个指定角色
     */
    public boolean hasAnyRole(String username, String... roles) {
        Set<String> userRoles = getUserRoles(username);
        for (String role : roles) {
            if (userRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 分配角色给用户
     */
    public boolean assignRole(RoleAssignmentRequest request) {
        log.info("分配角色: username={}, role={}, operator={}",
                request.getUsername(), request.getRole(), request.getOperator());

        try {
            // 检查操作者权限
            PermissionCheckResult permCheck = checkPermission(request.getOperator(), "USER", "ASSIGN_ROLE");
            if (!permCheck.isAllowed()) {
                log.warn("角色分配被拒绝: 操作者没有权限");
                return false;
            }

            // 获取用户当前角色
            Set<String> userRoles = getUserRoles(request.getUsername());
            userRoles.add(request.getRole());

            // 保存用户角色
            saveUserRoles(request.getUsername(), userRoles);

            // 清除用户缓存
            clearUserCache(request.getUsername());

            // 记录审计日志
            auditManagerComponent.recordAuditLog(
                    AuditOperationType.ASSIGN_ROLE,
                    AuditResourceType.USER,
                    request.getUsername(),
                    request.getOperator(),
                    "SUCCESS"
            );

            log.info("角色分配成功: username={}, role={}", request.getUsername(), request.getRole());
            return true;

        } catch (Exception e) {
            log.error("角色分配失败: username={}, role={}, error={}",
                    request.getUsername(), request.getRole(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 撤销用户角色
     */
    public boolean revokeRole(RoleAssignmentRequest request) {
        log.info("撤销角色: username={}, role={}, operator={}",
                request.getUsername(), request.getRole(), request.getOperator());

        try {
            // 检查操作者权限
            PermissionCheckResult permCheck = checkPermission(request.getOperator(), "USER", "REVOKE_ROLE");
            if (!permCheck.isAllowed()) {
                log.warn("角色撤销被拒绝: 操作者没有权限");
                return false;
            }

            // 获取用户当前角色
            Set<String> userRoles = getUserRoles(request.getUsername());
            userRoles.remove(request.getRole());

            // 保存用户角色
            saveUserRoles(request.getUsername(), userRoles);

            // 清除用户缓存
            clearUserCache(request.getUsername());

            // 记录审计日志
            auditManagerComponent.recordAuditLog(
                    AuditOperationType.REVOKE_ROLE,
                    AuditResourceType.USER,
                    request.getUsername(),
                    request.getOperator(),
                    "SUCCESS"
            );

            log.info("角色撤销成功: username={}, role={}", request.getUsername(), request.getRole());
            return true;

        } catch (Exception e) {
            log.error("角色撤销失败: username={}, role={}, error={}",
                    request.getUsername(), request.getRole(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 定义角色权限
     */
    public void defineRolePermissions(String role, Set<String> permissions) {
        log.info("定义角色权限: role={}, permissions={}", role, permissions);
        rolePermissions.put(role, new HashSet<>(permissions));

        // 缓存到Redis
        try {
            String cacheKey = ROLE_CACHE_PREFIX + role;
            String permissionsJson = objectMapper.writeValueAsString(permissions);
            stringRedisTemplate.opsForValue().set(cacheKey, permissionsJson, Duration.ofHours(24));
        } catch (JsonProcessingException e) {
            log.error("缓存角色权限失败: role={}", role, e);
        }
    }

    /**
     * 获取角色权限
     */
    public Set<String> getRolePermissions(String role) {
        // 先从内存缓存获取
        Set<String> permissions = rolePermissions.get(role);
        if (permissions != null) {
            return permissions;
        }

        // 从Redis获取
        try {
            String cacheKey = ROLE_CACHE_PREFIX + role;
            String permissionsJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (permissionsJson != null) {
                permissions = objectMapper.readValue(permissionsJson,
                        objectMapper.getTypeFactory().constructCollectionType(Set.class, String.class));
                rolePermissions.put(role, permissions);
                return permissions;
            }
        } catch (Exception e) {
            log.error("获取角色权限失败: role={}", role, e);
        }

        return Collections.emptySet();
    }

    /**
     * 获取用户安全信息
     */
    public UserSecurityInfo getUserSecurityInfo(String username) {
        // 先从缓存获取
        UserSecurityInfo cached = userCache.get(username);
        if (cached != null) {
            return cached;
        }

        Set<String> roles = getUserRoles(username);
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            permissions.addAll(getRolePermissions(role));
        }

        UserSecurityInfo userInfo = UserSecurityInfo.builder()
                .username(username)
                .roles(roles)
                .permissions(permissions)
                .lastLoginTime(LocalDateTime.now())
                .build();

        userCache.put(username, userInfo);

        return userInfo;
    }

    /**
     * 获取用户角色
     */
    public Set<String> getUserRoles(String username) {
        try {
            String cacheKey = USER_CACHE_PREFIX + username + ":roles";
            String rolesJson = stringRedisTemplate.opsForValue().get(cacheKey);

            if (rolesJson != null) {
                return objectMapper.readValue(rolesJson,
                        objectMapper.getTypeFactory().constructCollectionType(Set.class, String.class));
            }

            // 默认角色
            Set<String> defaultRoles = new HashSet<>();
            if ("admin".equals(username)) {
                defaultRoles.add("ADMIN");
                defaultRoles.add("USER");
            } else {
                defaultRoles.add("USER");
            }

            return defaultRoles;

        } catch (Exception e) {
            log.error("获取用户角色失败: username={}", username, e);
            return Collections.singleton("USER");
        }
    }

    /**
     * 保存用户角色
     */
    public void saveUserRoles(String username, Set<String> roles) {
        try {
            String cacheKey = USER_CACHE_PREFIX + username + ":roles";
            String rolesJson = objectMapper.writeValueAsString(roles);
            stringRedisTemplate.opsForValue().set(cacheKey, rolesJson, Duration.ofDays(30));
        } catch (JsonProcessingException e) {
            log.error("保存用户角色失败: username={}", username, e);
        }
    }

    /**
     * 清除用户缓存
     */
    public void clearUserCache(String username) {
        userCache.remove(username);
        stringRedisTemplate.delete(TOKEN_CACHE_PREFIX + username);
    }

    /**
     * 初始化默认角色权限
     */
    public void initializeDefaultRolePermissions() {
        // 管理员角色
        Set<String> adminPermissions = new HashSet<>(Arrays.asList(
                "*:*" // 所有权限
        ));
        defineRolePermissions("ADMIN", adminPermissions);

        // 经理角色
        Set<String> managerPermissions = new HashSet<>(Arrays.asList(
                "PROCESS:*",
                "TASK:*",
                "USER:VIEW",
                "REPORT:*"
        ));
        defineRolePermissions("MANAGER", managerPermissions);

        // 普通用户角色
        Set<String> userPermissions = new HashSet<>(Arrays.asList(
                "PROCESS:VIEW",
                "PROCESS:START",
                "TASK:VIEW",
                "TASK:COMPLETE",
                "TASK:CLAIM"
        ));
        defineRolePermissions("USER", userPermissions);

        log.info("默认角色权限初始化完成");
    }
}

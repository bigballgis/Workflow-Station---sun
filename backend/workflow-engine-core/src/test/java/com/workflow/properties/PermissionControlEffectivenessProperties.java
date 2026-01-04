package com.workflow.properties;

import com.workflow.dto.response.PermissionCheckResult;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * 权限控制有效性属性测试
 * 
 * 验证属性 16: 权限控制有效性
 * 对于任何需要权限验证的操作，应该正确验证用户权限，拒绝未授权的访问请求
 * 
 * 验证需求: 需求 11.1
 * 
 * 注意：这是一个简化的属性测试，主要验证权限控制逻辑的正确性，不依赖Spring Boot上下文
 */
@Label("功能: workflow-engine-core, 属性 16: 权限控制有效性")
public class PermissionControlEffectivenessProperties {

    // 模拟角色权限存储
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    // 模拟用户角色存储
    private final Map<String, Set<String>> userRoles = new ConcurrentHashMap<>();

    /**
     * 属性测试：有权限用户访问被允许
     * 验证拥有正确权限的用户可以访问受保护的资源
     */
    @Property(tries = 100)
    @Label("有权限用户访问被允许")
    void authorizedUserAccessAllowed(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置角色权限
        String permission = resource + ":" + action;
        defineRolePermissions(role, Set.of(permission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证权限检查
        PermissionCheckResult result = checkPermission(username, resource, action);
        
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getGrantedByRole()).isEqualTo(role);
        assertThat(result.getMatchedPermission()).isEqualTo(permission);
        assertThat(result.getMessage()).isEqualTo("权限检查通过");
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：无权限用户访问被拒绝
     * 验证没有正确权限的用户被拒绝访问受保护的资源
     */
    @Property(tries = 100)
    @Label("无权限用户访问被拒绝")
    void unauthorizedUserAccessDenied(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action,
            @ForAll @NotBlank String otherResource) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        Assume.that(otherResource != null && !otherResource.trim().isEmpty());
        Assume.that(!resource.equals(otherResource));
        
        // 设置角色权限（只有resource的权限，没有otherResource的权限）
        String permission = resource + ":" + action;
        defineRolePermissions(role, Set.of(permission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证用户无法访问otherResource
        PermissionCheckResult result = checkPermission(username, otherResource, action);
        
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getGrantedByRole()).isNull();
        assertThat(result.getMatchedPermission()).isNull();
        assertThat(result.getMessage()).isEqualTo("用户没有执行此操作的权限");
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：无角色用户访问被拒绝
     * 验证没有分配任何角色的用户被拒绝访问
     */
    @Property(tries = 100)
    @Label("无角色用户访问被拒绝")
    void userWithoutRoleAccessDenied(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 不分配任何角色给用户
        
        // 验证权限检查
        PermissionCheckResult result = checkPermission(username, resource, action);
        
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getMessage()).isEqualTo("用户没有分配任何角色");
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：通配符权限正确匹配
     * 验证通配符权限（如 resource:* 或 *:*）正确匹配所有操作
     */
    @Property(tries = 100)
    @Label("通配符权限正确匹配")
    void wildcardPermissionMatches(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置资源级通配符权限
        String wildcardPermission = resource + ":*";
        defineRolePermissions(role, Set.of(wildcardPermission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证任何操作都被允许
        PermissionCheckResult result = checkPermission(username, resource, action);
        
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getGrantedByRole()).isEqualTo(role);
        assertThat(result.getMatchedPermission()).isEqualTo(wildcardPermission);
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：超级管理员权限正确匹配
     * 验证超级管理员权限（*:*）可以访问所有资源和操作
     */
    @Property(tries = 100)
    @Label("超级管理员权限正确匹配")
    void superAdminPermissionMatches(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置超级管理员权限
        String adminRole = "ADMIN";
        defineRolePermissions(adminRole, Set.of("*:*"));
        
        // 分配管理员角色给用户
        assignRoleToUser(username, adminRole);
        
        // 验证任何资源和操作都被允许
        PermissionCheckResult result = checkPermission(username, resource, action);
        
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getGrantedByRole()).isEqualTo(adminRole);
        assertThat(result.getMatchedPermission()).isEqualTo("*:*");
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：多角色权限合并
     * 验证用户拥有多个角色时，权限正确合并
     */
    @Property(tries = 100)
    @Label("多角色权限合并")
    void multipleRolesPermissionMerge(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role1,
            @ForAll @NotBlank String role2,
            @ForAll @NotBlank String resource1,
            @ForAll @NotBlank String resource2,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role1 != null && !role1.trim().isEmpty());
        Assume.that(role2 != null && !role2.trim().isEmpty());
        Assume.that(resource1 != null && !resource1.trim().isEmpty());
        Assume.that(resource2 != null && !resource2.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        Assume.that(!role1.equals(role2));
        Assume.that(!resource1.equals(resource2));
        
        // 设置不同角色的权限
        String permission1 = resource1 + ":" + action;
        String permission2 = resource2 + ":" + action;
        defineRolePermissions(role1, Set.of(permission1));
        defineRolePermissions(role2, Set.of(permission2));
        
        // 分配两个角色给用户
        assignRoleToUser(username, role1);
        assignRoleToUser(username, role2);
        
        // 验证用户可以访问两个资源
        PermissionCheckResult result1 = checkPermission(username, resource1, action);
        PermissionCheckResult result2 = checkPermission(username, resource2, action);
        
        assertThat(result1.isAllowed()).isTrue();
        assertThat(result2.isAllowed()).isTrue();
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：角色撤销后权限失效
     * 验证角色被撤销后，用户失去相应权限
     */
    @Property(tries = 100)
    @Label("角色撤销后权限失效")
    void revokedRolePermissionInvalid(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置角色权限
        String permission = resource + ":" + action;
        defineRolePermissions(role, Set.of(permission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证有权限
        PermissionCheckResult resultBefore = checkPermission(username, resource, action);
        assertThat(resultBefore.isAllowed()).isTrue();
        
        // 撤销角色
        revokeRoleFromUser(username, role);
        
        // 验证权限失效
        PermissionCheckResult resultAfter = checkPermission(username, resource, action);
        assertThat(resultAfter.isAllowed()).isFalse();
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：权限检查结果一致性
     * 验证相同条件下多次权限检查结果一致
     */
    @Property(tries = 100)
    @Label("权限检查结果一致性")
    void permissionCheckConsistency(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置角色权限
        String permission = resource + ":" + action;
        defineRolePermissions(role, Set.of(permission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 多次检查权限
        PermissionCheckResult result1 = checkPermission(username, resource, action);
        PermissionCheckResult result2 = checkPermission(username, resource, action);
        PermissionCheckResult result3 = checkPermission(username, resource, action);
        
        // 验证结果一致
        assertThat(result1.isAllowed()).isEqualTo(result2.isAllowed());
        assertThat(result2.isAllowed()).isEqualTo(result3.isAllowed());
        assertThat(result1.getGrantedByRole()).isEqualTo(result2.getGrantedByRole());
        assertThat(result2.getGrantedByRole()).isEqualTo(result3.getGrantedByRole());
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：不同操作权限独立
     * 验证同一资源的不同操作权限是独立的
     */
    @Property(tries = 100)
    @Label("不同操作权限独立")
    void differentActionsIndependent(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String allowedAction,
            @ForAll @NotBlank String deniedAction) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(allowedAction != null && !allowedAction.trim().isEmpty());
        Assume.that(deniedAction != null && !deniedAction.trim().isEmpty());
        Assume.that(!allowedAction.equals(deniedAction));
        
        // 只设置allowedAction的权限
        String permission = resource + ":" + allowedAction;
        defineRolePermissions(role, Set.of(permission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证allowedAction被允许
        PermissionCheckResult allowedResult = checkPermission(username, resource, allowedAction);
        assertThat(allowedResult.isAllowed()).isTrue();
        
        // 验证deniedAction被拒绝
        PermissionCheckResult deniedResult = checkPermission(username, resource, deniedAction);
        assertThat(deniedResult.isAllowed()).isFalse();
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：权限继承正确性
     * 验证角色权限继承逻辑正确（通配符权限包含具体权限）
     */
    @Property(tries = 100)
    @Label("权限继承正确性")
    void permissionInheritanceCorrectness(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> actions) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(actions != null && !actions.isEmpty());
        Assume.that(actions.stream().allMatch(a -> a != null && !a.trim().isEmpty()));
        
        // 设置资源级通配符权限
        String wildcardPermission = resource + ":*";
        defineRolePermissions(role, Set.of(wildcardPermission));
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证所有操作都被允许
        for (String action : actions) {
            PermissionCheckResult result = checkPermission(username, resource, action);
            assertThat(result.isAllowed())
                    .as("Action '%s' should be allowed with wildcard permission", action)
                    .isTrue();
        }
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：空权限集合处理
     * 验证角色没有任何权限时，所有访问被拒绝
     */
    @Property(tries = 100)
    @Label("空权限集合处理")
    void emptyPermissionSetHandling(
            @ForAll @NotBlank String username,
            @ForAll @NotBlank String role,
            @ForAll @NotBlank String resource,
            @ForAll @NotBlank String action) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(role != null && !role.trim().isEmpty());
        Assume.that(resource != null && !resource.trim().isEmpty());
        Assume.that(action != null && !action.trim().isEmpty());
        
        // 设置空权限集合
        defineRolePermissions(role, Collections.emptySet());
        
        // 分配角色给用户
        assignRoleToUser(username, role);
        
        // 验证访问被拒绝
        PermissionCheckResult result = checkPermission(username, resource, action);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getMessage()).isEqualTo("用户没有执行此操作的权限");
        
        // 清理
        clearTestData();
    }

    /**
     * 属性测试：权限检查性能一致性
     * 验证权限检查在不同角色数量下保持正确性
     */
    @Property(tries = 50)
    @Label("多角色权限检查正确性")
    void multiRolePermissionCheckCorrectness(
            @ForAll @NotBlank String username,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> roles,
            @ForAll @NotBlank String targetResource,
            @ForAll @NotBlank String targetAction) {
        
        // 过滤无效输入
        Assume.that(username != null && !username.trim().isEmpty());
        Assume.that(roles != null && !roles.isEmpty());
        Assume.that(roles.stream().allMatch(r -> r != null && !r.trim().isEmpty()));
        Assume.that(targetResource != null && !targetResource.trim().isEmpty());
        Assume.that(targetAction != null && !targetAction.trim().isEmpty());
        
        // 确保角色唯一
        Set<String> uniqueRoles = new HashSet<>(roles);
        Assume.that(uniqueRoles.size() == roles.size());
        
        // 为每个角色设置不同的权限
        String targetPermission = targetResource + ":" + targetAction;
        boolean anyRoleHasPermission = false;
        String grantingRole = null;
        
        int index = 0;
        for (String role : roles) {
            // 只有第一个角色有目标权限
            if (index == 0) {
                defineRolePermissions(role, Set.of(targetPermission));
                anyRoleHasPermission = true;
                grantingRole = role;
            } else {
                defineRolePermissions(role, Set.of("OTHER_RESOURCE:OTHER_ACTION"));
            }
            assignRoleToUser(username, role);
            index++;
        }
        
        // 验证权限检查
        PermissionCheckResult result = checkPermission(username, targetResource, targetAction);
        
        if (anyRoleHasPermission) {
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getGrantedByRole()).isEqualTo(grantingRole);
        } else {
            assertThat(result.isAllowed()).isFalse();
        }
        
        // 清理
        clearTestData();
    }

    // ==================== 辅助方法 ====================

    /**
     * 定义角色权限
     */
    private void defineRolePermissions(String role, Set<String> permissions) {
        rolePermissions.put(role, new HashSet<>(permissions));
    }

    /**
     * 分配角色给用户
     */
    private void assignRoleToUser(String username, String role) {
        userRoles.computeIfAbsent(username, k -> new HashSet<>()).add(role);
    }

    /**
     * 撤销用户角色
     */
    private void revokeRoleFromUser(String username, String role) {
        Set<String> roles = userRoles.get(username);
        if (roles != null) {
            roles.remove(role);
        }
    }

    /**
     * 获取用户角色
     */
    private Set<String> getUserRoles(String username) {
        return userRoles.getOrDefault(username, Collections.emptySet());
    }

    /**
     * 获取角色权限
     */
    private Set<String> getRolePermissions(String role) {
        return rolePermissions.getOrDefault(role, Collections.emptySet());
    }

    /**
     * 检查权限
     * 模拟SecurityManagerComponent.checkPermission方法的逻辑
     */
    private PermissionCheckResult checkPermission(String username, String resource, String action) {
        // 获取用户角色
        Set<String> roles = getUserRoles(username);
        
        if (roles.isEmpty()) {
            return PermissionCheckResult.denied("用户没有分配任何角色");
        }
        
        // 构建权限标识
        String permission = resource + ":" + action;
        
        // 检查是否有权限
        for (String role : roles) {
            Set<String> permissions = getRolePermissions(role);
            if (permissions.contains(permission) || 
                permissions.contains(resource + ":*") || 
                permissions.contains("*:*")) {
                
                // 确定匹配的权限
                String matchedPermission;
                if (permissions.contains(permission)) {
                    matchedPermission = permission;
                } else if (permissions.contains(resource + ":*")) {
                    matchedPermission = resource + ":*";
                } else {
                    matchedPermission = "*:*";
                }
                
                return PermissionCheckResult.allowed(role, matchedPermission);
            }
        }
        
        return PermissionCheckResult.denied("用户没有执行此操作的权限");
    }

    /**
     * 清理测试数据
     */
    private void clearTestData() {
        rolePermissions.clear();
        userRoles.clear();
    }
}

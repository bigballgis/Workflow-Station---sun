package com.admin.properties;

import com.admin.component.PermissionConflictComponent;
import com.admin.component.PermissionDelegationComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.response.PermissionCheckResult;
import com.platform.security.entity.Permission;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserRole;
import com.platform.security.entity.User;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.admin.repository.*;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 属性 6: 权限检查一致性
 * 对于任何权限检查请求，系统应该根据用户的角色和权限配置返回一致的检查结果
 * 
 * 验证需求: 需求 3.2, 3.3, 3.4
 */
public class PermissionCheckConsistencyProperties {
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 对于任何拥有权限的用户，权限检查应该返回允许
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 拥有权限的用户应该通过权限检查")
    void userWithPermissionShouldBeAllowed(
            @ForAll("userWithPermissions") UserWithPermissions userWithPerms) {
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户角色和权限
        setupUserWithPermissions(ctx, userWithPerms);
        
        // When: 检查用户拥有的权限
        for (Permission permission : userWithPerms.permissions) {
            PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                    userWithPerms.userId, 
                    permission.getResource(), 
                    permission.getAction());
            
            // Then: 应该返回允许
            assertThat(result.isAllowed())
                    .as("用户拥有权限 %s:%s 应该通过检查", 
                            permission.getResource(), permission.getAction())
                    .isTrue();
        }
    }
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 对于任何没有权限的用户，权限检查应该返回拒绝
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 没有权限的用户应该被拒绝")
    void userWithoutPermissionShouldBeDenied(
            @ForAll("userWithPermissions") UserWithPermissions userWithPerms,
            @ForAll("nonExistentPermission") Permission nonExistentPermission) {
        
        // 确保非存在权限不在用户权限列表中
        Assume.that(userWithPerms.permissions.stream()
                .noneMatch(p -> p.getResource().equals(nonExistentPermission.getResource()) 
                        && p.getAction().equals(nonExistentPermission.getAction())));
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户角色和权限
        setupUserWithPermissions(ctx, userWithPerms);
        
        // When: 检查用户没有的权限
        PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                userWithPerms.userId, 
                nonExistentPermission.getResource(), 
                nonExistentPermission.getAction());
        
        // Then: 应该返回拒绝
        assertThat(result.isAllowed())
                .as("用户没有权限 %s:%s 应该被拒绝", 
                        nonExistentPermission.getResource(), nonExistentPermission.getAction())
                .isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 对于任何没有角色的用户，权限检查应该返回拒绝
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 没有角色的用户应该被拒绝")
    void userWithoutRoleShouldBeDenied(
            @ForAll("userId") String userId,
            @ForAll("nonExistentPermission") Permission permission) {
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 用户没有任何角色
        when(ctx.roleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        
        // When: 检查权限
        PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                userId, permission.getResource(), permission.getAction());
        
        // Then: 应该返回拒绝
        assertThat(result.isAllowed())
                .as("没有角色的用户应该被拒绝")
                .isFalse();
        assertThat(result.getReason())
                .as("应该返回没有角色的原因")
                .contains("没有分配任何角色");
    }

    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 权限检查应该是幂等的 - 多次检查同一权限应该返回相同结果
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 权限检查应该是幂等的")
    void permissionCheckShouldBeIdempotent(
            @ForAll("userWithPermissions") UserWithPermissions userWithPerms,
            @ForAll("nonExistentPermission") Permission permission) {
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户角色和权限
        setupUserWithPermissions(ctx, userWithPerms);
        
        // When: 多次检查同一权限
        PermissionCheckResult firstResult = ctx.rolePermissionManager.checkPermission(
                userWithPerms.userId, permission.getResource(), permission.getAction());
        PermissionCheckResult secondResult = ctx.rolePermissionManager.checkPermission(
                userWithPerms.userId, permission.getResource(), permission.getAction());
        PermissionCheckResult thirdResult = ctx.rolePermissionManager.checkPermission(
                userWithPerms.userId, permission.getResource(), permission.getAction());
        
        // Then: 结果应该一致
        assertThat(firstResult.isAllowed())
                .as("多次权限检查结果应该一致")
                .isEqualTo(secondResult.isAllowed())
                .isEqualTo(thirdResult.isAllowed());
    }
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 通配符权限应该匹配所有资源或操作
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 通配符权限应该匹配所有资源或操作")
    void wildcardPermissionShouldMatchAll(
            @ForAll("userWithWildcardPermission") UserWithWildcardPermission userWithWildcard,
            @ForAll("randomResource") String resource,
            @ForAll("randomAction") String action) {
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户拥有通配符权限
        setupUserWithWildcardPermission(ctx, userWithWildcard);
        
        // When: 检查任意资源和操作
        PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                userWithWildcard.userId, resource, action);
        
        // Then: 如果是全局通配符，应该允许
        if (userWithWildcard.wildcardType == WildcardType.ALL_RESOURCES) {
            assertThat(result.isAllowed())
                    .as("全局通配符权限应该允许所有资源")
                    .isTrue();
        }
        // 如果是资源级通配符，只有匹配的资源才允许
        else if (userWithWildcard.wildcardType == WildcardType.ALL_ACTIONS 
                && resource.equals(userWithWildcard.targetResource)) {
            assertThat(result.isAllowed())
                    .as("资源级通配符权限应该允许该资源的所有操作")
                    .isTrue();
        }
    }
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 多角色用户的权限应该是所有角色权限的并集
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 多角色用户权限应该是并集")
    void multiRoleUserPermissionsShouldBeUnion(
            @ForAll("userWithMultipleRoles") UserWithMultipleRoles userWithRoles) {
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户拥有多个角色
        setupUserWithMultipleRoles(ctx, userWithRoles);
        
        // When & Then: 检查所有角色的权限都应该通过
        Set<Permission> allPermissions = new HashSet<>();
        for (RoleWithPermissions roleWithPerms : userWithRoles.roles) {
            allPermissions.addAll(roleWithPerms.permissions);
        }
        
        for (Permission permission : allPermissions) {
            PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                    userWithRoles.userId, 
                    permission.getResource(), 
                    permission.getAction());
            
            assertThat(result.isAllowed())
                    .as("多角色用户应该拥有所有角色的权限: %s:%s", 
                            permission.getResource(), permission.getAction())
                    .isTrue();
        }
    }
    
    /**
     * 功能: admin-center, 属性 6: 权限检查一致性
     * 权限检查结果应该包含授权角色信息
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 6: 允许结果应该包含授权角色信息")
    void allowedResultShouldContainRoleInfo(
            @ForAll("userWithPermissions") UserWithPermissions userWithPerms) {
        
        // 确保用户至少有一个权限
        Assume.that(!userWithPerms.permissions.isEmpty());
        
        // 创建测试上下文
        TestContext ctx = createTestContext();
        
        // Given: 设置用户角色和权限
        setupUserWithPermissions(ctx, userWithPerms);
        
        // When: 检查用户拥有的权限
        Permission permission = userWithPerms.permissions.iterator().next();
        PermissionCheckResult result = ctx.rolePermissionManager.checkPermission(
                userWithPerms.userId, 
                permission.getResource(), 
                permission.getAction());
        
        // Then: 允许结果应该包含角色信息
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRoleId())
                .as("允许结果应该包含角色ID")
                .isNotNull()
                .isNotEmpty();
        assertThat(result.getRoleName())
                .as("允许结果应该包含角色名称")
                .isNotNull()
                .isNotEmpty();
    }
    
    // ==================== 测试上下文 ====================
    
    static class TestContext {
        RoleRepository roleRepository;
        PermissionRepository permissionRepository;
        RolePermissionRepository rolePermissionRepository;
        UserRoleRepository userRoleRepository;
        PermissionDelegationComponent delegationComponent;
        PermissionConflictComponent conflictComponent;
        RolePermissionManagerComponent rolePermissionManager;
    }
    
    private TestContext createTestContext() {
        TestContext ctx = new TestContext();
        ctx.roleRepository = mock(RoleRepository.class);
        ctx.permissionRepository = mock(PermissionRepository.class);
        ctx.rolePermissionRepository = mock(RolePermissionRepository.class);
        ctx.userRoleRepository = mock(UserRoleRepository.class);
        ctx.delegationComponent = mock(PermissionDelegationComponent.class);
        ctx.conflictComponent = mock(PermissionConflictComponent.class);
        
        // 默认委托权限检查返回 false
        when(ctx.delegationComponent.hasDelegatedPermission(anyString(), anyString())).thenReturn(false);
        
        // Use real PermissionHelper instead of mock for proper permission matching
        com.admin.helper.PermissionHelper permissionHelper = new com.admin.helper.PermissionHelper(ctx.permissionRepository);
        
        ctx.rolePermissionManager = new RolePermissionManagerComponent(
                ctx.roleRepository,
                ctx.permissionRepository,
                ctx.rolePermissionRepository,
                ctx.userRoleRepository,
                ctx.delegationComponent,
                ctx.conflictComponent,
                mock(com.admin.helper.RoleHelper.class),
                permissionHelper);
        return ctx;
    }
    
    // ==================== 辅助方法 ====================
    
    private void setupUserWithPermissions(TestContext ctx, UserWithPermissions userWithPerms) {
        // 设置用户角色
        when(ctx.roleRepository.findByUserId(userWithPerms.userId))
                .thenReturn(Collections.singletonList(userWithPerms.role));
        
        // 设置角色权限
        when(ctx.roleRepository.findById(userWithPerms.role.getId()))
                .thenReturn(Optional.of(userWithPerms.role));
        when(ctx.permissionRepository.findByRoleId(userWithPerms.role.getId()))
                .thenReturn(userWithPerms.permissions);
    }
    
    private void setupUserWithWildcardPermission(TestContext ctx, UserWithWildcardPermission userWithWildcard) {
        when(ctx.roleRepository.findByUserId(userWithWildcard.userId))
                .thenReturn(Collections.singletonList(userWithWildcard.role));
        
        when(ctx.roleRepository.findById(userWithWildcard.role.getId()))
                .thenReturn(Optional.of(userWithWildcard.role));
        when(ctx.permissionRepository.findByRoleId(userWithWildcard.role.getId()))
                .thenReturn(userWithWildcard.permissions);
    }
    
    private void setupUserWithMultipleRoles(TestContext ctx, UserWithMultipleRoles userWithRoles) {
        List<Role> roles = userWithRoles.roles.stream()
                .map(r -> r.role)
                .collect(Collectors.toList());
        
        when(ctx.roleRepository.findByUserId(userWithRoles.userId))
                .thenReturn(roles);
        
        for (RoleWithPermissions roleWithPerms : userWithRoles.roles) {
            when(ctx.roleRepository.findById(roleWithPerms.role.getId()))
                    .thenReturn(Optional.of(roleWithPerms.role));
            when(ctx.permissionRepository.findByRoleId(roleWithPerms.role.getId()))
                    .thenReturn(roleWithPerms.permissions);
        }
    }

    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<String> userId() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<UserWithPermissions> userWithPermissions() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.integers().between(1, 5)
        ).as((userId, roleCode, permCount) -> {
            String roleId = UUID.randomUUID().toString();
            
            Role role = Role.builder()
                    .id(roleId)
                    .name("Role " + roleCode)
                    .code("ROLE_" + roleCode.toUpperCase())
                    .type(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED))
                    .status("ACTIVE")
                    .build();
            
            Set<Permission> permissions = generatePermissions("perm", permCount);
            
            return new UserWithPermissions(userId.toString(), role, permissions);
        });
    }
    
    @Provide
    Arbitrary<Permission> nonExistentPermission() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
                Arbitraries.of("READ", "WRITE", "DELETE", "EXECUTE", "ADMIN")
        ).as((resource, action) -> Permission.builder()
                .id(UUID.randomUUID().toString())
                .name("Non-existent Permission")
                .code("NON_EXIST_" + resource.toUpperCase())
                .resource("nonexistent_" + resource)
                .action(action)
                .build());
    }
    
    @Provide
    Arbitrary<UserWithWildcardPermission> userWithWildcardPermission() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.of(WildcardType.ALL_RESOURCES, WildcardType.ALL_ACTIONS),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10)
        ).as((userId, roleCode, wildcardType, targetResource) -> {
            String roleId = UUID.randomUUID().toString();
            
            Role role = Role.builder()
                    .id(roleId)
                    .name("Wildcard Role " + roleCode)
                    .code("WILDCARD_" + roleCode.toUpperCase())
                    .type(EntityTypeConverter.fromRoleType(RoleType.ADMIN))
                    .status("ACTIVE")
                    .build();
            
            Set<Permission> permissions = new HashSet<>();
            if (wildcardType == WildcardType.ALL_RESOURCES) {
                permissions.add(Permission.builder()
                        .id(UUID.randomUUID().toString())
                        .name("All Resources Permission")
                        .code("ALL_RESOURCES")
                        .resource("*")
                        .action("*")
                        .build());
            } else {
                permissions.add(Permission.builder()
                        .id(UUID.randomUUID().toString())
                        .name("All Actions Permission")
                        .code("ALL_ACTIONS_" + targetResource.toUpperCase())
                        .resource(targetResource)
                        .action("*")
                        .build());
            }
            
            return new UserWithWildcardPermission(userId.toString(), role, permissions, 
                    wildcardType, targetResource);
        });
    }
    
    @Provide
    Arbitrary<UserWithMultipleRoles> userWithMultipleRoles() {
        return Arbitraries.create(UUID::randomUUID)
                .flatMap(userId -> 
                        Arbitraries.integers().between(2, 4).flatMap(roleCount -> {
                            List<Arbitrary<RoleWithPermissions>> roleArbitraries = new ArrayList<>();
                            for (int i = 0; i < roleCount; i++) {
                                final int index = i;
                                roleArbitraries.add(
                                        Combinators.combine(
                                                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(6),
                                                Arbitraries.integers().between(1, 3)
                                        ).as((roleCode, permCount) -> {
                                            String roleId = UUID.randomUUID().toString();
                                            Role role = Role.builder()
                                                    .id(roleId)
                                                    .name("Role " + index + " " + roleCode)
                                                    .code("ROLE_" + index + "_" + roleCode.toUpperCase())
                                                    .type(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED))
                                                    .status("ACTIVE")
                                                    .build();
                                            Set<Permission> permissions = generatePermissions("role" + index, permCount);
                                            return new RoleWithPermissions(role, permissions);
                                        })
                                );
                            }
                            
                            return Combinators.combine(roleArbitraries).as(roles -> 
                                    new UserWithMultipleRoles(userId.toString(), new ArrayList<>(roles)));
                        })
                );
    }
    
    @Provide
    Arbitrary<String> randomResource() {
        return Arbitraries.of(
                "user", "role", "department", "permission", "config",
                "audit", "monitor", "dictionary", "function_unit", "virtual_group"
        );
    }
    
    @Provide
    Arbitrary<String> randomAction() {
        return Arbitraries.of("READ", "WRITE", "DELETE", "EXECUTE", "ADMIN", "EXPORT", "IMPORT");
    }
    
    private static Set<Permission> generatePermissions(String prefix, int count) {
        Set<Permission> permissions = new HashSet<>();
        String[] actions = {"READ", "WRITE", "DELETE", "EXECUTE"};
        for (int i = 0; i < count; i++) {
            permissions.add(Permission.builder()
                    .id(UUID.randomUUID().toString())
                    .name(prefix + " Permission " + i)
                    .code(prefix.toUpperCase() + "_PERM_" + i)
                    .resource(prefix + "_resource_" + i)
                    .action(actions[i % actions.length])
                    .build());
        }
        return permissions;
    }
    
    // ==================== 数据类 ====================
    
    static class UserWithPermissions {
        final String userId;
        final Role role;
        final Set<Permission> permissions;
        
        UserWithPermissions(String userId, Role role, Set<Permission> permissions) {
            this.userId = userId;
            this.role = role;
            this.permissions = permissions;
        }
    }
    
    enum WildcardType {
        ALL_RESOURCES,
        ALL_ACTIONS
    }
    
    static class UserWithWildcardPermission {
        final String userId;
        final Role role;
        final Set<Permission> permissions;
        final WildcardType wildcardType;
        final String targetResource;
        
        UserWithWildcardPermission(String userId, Role role, Set<Permission> permissions,
                                   WildcardType wildcardType, String targetResource) {
            this.userId = userId;
            this.role = role;
            this.permissions = permissions;
            this.wildcardType = wildcardType;
            this.targetResource = targetResource;
        }
    }
    
    static class RoleWithPermissions {
        final Role role;
        final Set<Permission> permissions;
        
        RoleWithPermissions(Role role, Set<Permission> permissions) {
            this.role = role;
            this.permissions = permissions;
        }
    }
    
    static class UserWithMultipleRoles {
        final String userId;
        final List<RoleWithPermissions> roles;
        
        UserWithMultipleRoles(String userId, List<RoleWithPermissions> roles) {
            this.userId = userId;
            this.roles = roles;
        }
    }
}


package com.admin.properties;

import com.admin.component.PermissionConflictComponent;
import com.admin.component.PermissionDelegationComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.entity.Permission;
import com.admin.entity.Role;
import com.admin.enums.RoleType;
import com.admin.repository.*;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 属性 5: 权限继承正确性
 * 对于任何具有继承关系的角色，子角色应该正确继承父角色的所有权限
 * 
 * 验证需求: 需求 3.5
 */
public class PermissionInheritanceProperties {
    
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
        
        when(ctx.delegationComponent.hasDelegatedPermission(anyString(), anyString())).thenReturn(false);
        
        ctx.rolePermissionManager = new RolePermissionManagerComponent(
                ctx.roleRepository,
                ctx.permissionRepository,
                ctx.rolePermissionRepository,
                ctx.userRoleRepository,
                ctx.delegationComponent,
                ctx.conflictComponent);
        return ctx;
    }

    
    /**
     * 功能: admin-center, 属性 5: 权限继承正确性
     * 对于任何子角色，其有效权限应该包含父角色的所有权限
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 5: 子角色应该继承父角色的所有权限")
    void childRoleShouldInheritParentPermissions(
            @ForAll("roleHierarchy") RoleHierarchy hierarchy) {
        
        TestContext ctx = createTestContext();
        setupRoleHierarchy(ctx, hierarchy);
        
        Set<Permission> childEffectivePermissions = ctx.rolePermissionManager.getEffectivePermissions(hierarchy.childRole.getId());
        
        for (Permission parentPermission : hierarchy.parentPermissions) {
            assertThat(childEffectivePermissions)
                    .as("子角色应该继承父角色的权限: %s", parentPermission.getCode())
                    .contains(parentPermission);
        }
    }
    
    /**
     * 功能: admin-center, 属性 5: 权限继承正确性
     * 对于任何子角色，其有效权限应该包含自身的权限
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 5: 子角色应该保留自身权限")
    void childRoleShouldRetainOwnPermissions(
            @ForAll("roleHierarchy") RoleHierarchy hierarchy) {
        
        TestContext ctx = createTestContext();
        setupRoleHierarchy(ctx, hierarchy);
        
        Set<Permission> childEffectivePermissions = ctx.rolePermissionManager.getEffectivePermissions(hierarchy.childRole.getId());
        
        for (Permission childPermission : hierarchy.childPermissions) {
            assertThat(childEffectivePermissions)
                    .as("子角色应该保留自身权限: %s", childPermission.getCode())
                    .contains(childPermission);
        }
    }
    
    /**
     * 功能: admin-center, 属性 5: 权限继承正确性
     * 对于任何没有父角色的角色，其有效权限应该等于自身权限
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 5: 无父角色时有效权限等于自身权限")
    void roleWithoutParentShouldHaveOnlyOwnPermissions(
            @ForAll("singleRole") SingleRole singleRole) {
        
        TestContext ctx = createTestContext();
        setupSingleRole(ctx, singleRole);
        
        Set<Permission> effectivePermissions = ctx.rolePermissionManager.getEffectivePermissions(singleRole.role.getId());
        
        assertThat(effectivePermissions)
                .as("无父角色时有效权限应该等于自身权限")
                .containsExactlyInAnyOrderElementsOf(singleRole.permissions);
    }
    
    /**
     * 功能: admin-center, 属性 5: 权限继承正确性
     * 对于多级继承，孙角色应该继承祖父角色的权限
     */
    @Property(tries = 50)
    @Label("功能: admin-center, 属性 5: 多级继承应该正确传递权限")
    void multiLevelInheritanceShouldWork(
            @ForAll("multiLevelHierarchy") MultiLevelHierarchy hierarchy) {
        
        TestContext ctx = createTestContext();
        setupMultiLevelHierarchy(ctx, hierarchy);
        
        Set<Permission> grandchildEffectivePermissions = 
                ctx.rolePermissionManager.getEffectivePermissions(hierarchy.grandchildRole.getId());
        
        for (Permission grandparentPermission : hierarchy.grandparentPermissions) {
            assertThat(grandchildEffectivePermissions)
                    .as("孙角色应该继承祖父角色的权限: %s", grandparentPermission.getCode())
                    .contains(grandparentPermission);
        }
        
        for (Permission parentPermission : hierarchy.parentPermissions) {
            assertThat(grandchildEffectivePermissions)
                    .as("孙角色应该继承父角色的权限: %s", parentPermission.getCode())
                    .contains(parentPermission);
        }
    }
    
    /**
     * 功能: admin-center, 属性 5: 权限继承正确性
     * 权限继承应该是幂等的 - 多次获取有效权限应该返回相同结果
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 5: 权限继承应该是幂等的")
    void permissionInheritanceShouldBeIdempotent(
            @ForAll("roleHierarchy") RoleHierarchy hierarchy) {
        
        TestContext ctx = createTestContext();
        setupRoleHierarchy(ctx, hierarchy);
        
        Set<Permission> firstResult = ctx.rolePermissionManager.getEffectivePermissions(hierarchy.childRole.getId());
        Set<Permission> secondResult = ctx.rolePermissionManager.getEffectivePermissions(hierarchy.childRole.getId());
        
        assertThat(firstResult)
                .as("多次获取有效权限应该返回相同结果")
                .containsExactlyInAnyOrderElementsOf(secondResult);
    }

    
    // ==================== 辅助方法 ====================
    
    private void setupRoleHierarchy(TestContext ctx, RoleHierarchy hierarchy) {
        when(ctx.roleRepository.findById(hierarchy.parentRole.getId()))
                .thenReturn(Optional.of(hierarchy.parentRole));
        when(ctx.roleRepository.findById(hierarchy.childRole.getId()))
                .thenReturn(Optional.of(hierarchy.childRole));
        
        when(ctx.permissionRepository.findByRoleId(hierarchy.parentRole.getId()))
                .thenReturn(hierarchy.parentPermissions);
        when(ctx.permissionRepository.findByRoleId(hierarchy.childRole.getId()))
                .thenReturn(hierarchy.childPermissions);
    }
    
    private void setupSingleRole(TestContext ctx, SingleRole singleRole) {
        when(ctx.roleRepository.findById(singleRole.role.getId()))
                .thenReturn(Optional.of(singleRole.role));
        when(ctx.permissionRepository.findByRoleId(singleRole.role.getId()))
                .thenReturn(singleRole.permissions);
    }
    
    private void setupMultiLevelHierarchy(TestContext ctx, MultiLevelHierarchy hierarchy) {
        when(ctx.roleRepository.findById(hierarchy.grandparentRole.getId()))
                .thenReturn(Optional.of(hierarchy.grandparentRole));
        when(ctx.roleRepository.findById(hierarchy.parentRole.getId()))
                .thenReturn(Optional.of(hierarchy.parentRole));
        when(ctx.roleRepository.findById(hierarchy.grandchildRole.getId()))
                .thenReturn(Optional.of(hierarchy.grandchildRole));
        
        when(ctx.permissionRepository.findByRoleId(hierarchy.grandparentRole.getId()))
                .thenReturn(hierarchy.grandparentPermissions);
        when(ctx.permissionRepository.findByRoleId(hierarchy.parentRole.getId()))
                .thenReturn(hierarchy.parentPermissions);
        when(ctx.permissionRepository.findByRoleId(hierarchy.grandchildRole.getId()))
                .thenReturn(hierarchy.grandchildPermissions);
    }
    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<RoleHierarchy> roleHierarchy() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.integers().between(1, 5),
                Arbitraries.integers().between(1, 5)
        ).as((parentCode, childCode, parentPermCount, childPermCount) -> {
            String parentId = UUID.randomUUID().toString();
            String childId = UUID.randomUUID().toString();
            
            Role parentRole = Role.builder()
                    .id(parentId)
                    .name("Parent Role " + parentCode)
                    .code("PARENT_" + parentCode.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .status("ACTIVE")
                    .build();
            
            Role childRole = Role.builder()
                    .id(childId)
                    .name("Child Role " + childCode)
                    .code("CHILD_" + childCode.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .parentRoleId(parentId)
                    .status("ACTIVE")
                    .build();
            
            Set<Permission> parentPermissions = generatePermissions("parent", parentPermCount);
            Set<Permission> childPermissions = generatePermissions("child", childPermCount);
            
            return new RoleHierarchy(parentRole, childRole, parentPermissions, childPermissions);
        });
    }
    
    @Provide
    Arbitrary<SingleRole> singleRole() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.integers().between(1, 5)
        ).as((code, permCount) -> {
            Role role = Role.builder()
                    .id(UUID.randomUUID().toString())
                    .name("Single Role " + code)
                    .code("SINGLE_" + code.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .parentRoleId(null)
                    .status("ACTIVE")
                    .build();
            
            Set<Permission> permissions = generatePermissions("single", permCount);
            
            return new SingleRole(role, permissions);
        });
    }
    
    @Provide
    Arbitrary<MultiLevelHierarchy> multiLevelHierarchy() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(1, 3)
        ).as((gpCode, pCode, gcCode, gpPermCount, pPermCount, gcPermCount) -> {
            String grandparentId = UUID.randomUUID().toString();
            String parentId = UUID.randomUUID().toString();
            String grandchildId = UUID.randomUUID().toString();
            
            Role grandparentRole = Role.builder()
                    .id(grandparentId)
                    .name("Grandparent Role " + gpCode)
                    .code("GP_" + gpCode.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .status("ACTIVE")
                    .build();
            
            Role parentRole = Role.builder()
                    .id(parentId)
                    .name("Parent Role " + pCode)
                    .code("P_" + pCode.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .parentRoleId(grandparentId)
                    .status("ACTIVE")
                    .build();
            
            Role grandchildRole = Role.builder()
                    .id(grandchildId)
                    .name("Grandchild Role " + gcCode)
                    .code("GC_" + gcCode.toUpperCase())
                    .type(RoleType.BUSINESS)
                    .parentRoleId(parentId)
                    .status("ACTIVE")
                    .build();
            
            Set<Permission> grandparentPermissions = generatePermissions("gp", gpPermCount);
            Set<Permission> parentPermissions = generatePermissions("p", pPermCount);
            Set<Permission> grandchildPermissions = generatePermissions("gc", gcPermCount);
            
            return new MultiLevelHierarchy(
                    grandparentRole, parentRole, grandchildRole,
                    grandparentPermissions, parentPermissions, grandchildPermissions);
        });
    }
    
    private static Set<Permission> generatePermissions(String prefix, int count) {
        Set<Permission> permissions = new HashSet<>();
        for (int i = 0; i < count; i++) {
            permissions.add(Permission.builder()
                    .id(UUID.randomUUID().toString())
                    .name(prefix + " Permission " + i)
                    .code(prefix.toUpperCase() + "_PERM_" + i)
                    .type("MENU")
                    .resource(prefix + "_resource_" + i)
                    .action("READ")
                    .build());
        }
        return permissions;
    }

    
    // ==================== 数据类 ====================
    
    static class RoleHierarchy {
        final Role parentRole;
        final Role childRole;
        final Set<Permission> parentPermissions;
        final Set<Permission> childPermissions;
        
        RoleHierarchy(Role parentRole, Role childRole, 
                      Set<Permission> parentPermissions, Set<Permission> childPermissions) {
            this.parentRole = parentRole;
            this.childRole = childRole;
            this.parentPermissions = parentPermissions;
            this.childPermissions = childPermissions;
        }
    }
    
    static class SingleRole {
        final Role role;
        final Set<Permission> permissions;
        
        SingleRole(Role role, Set<Permission> permissions) {
            this.role = role;
            this.permissions = permissions;
        }
    }
    
    static class MultiLevelHierarchy {
        final Role grandparentRole;
        final Role parentRole;
        final Role grandchildRole;
        final Set<Permission> grandparentPermissions;
        final Set<Permission> parentPermissions;
        final Set<Permission> grandchildPermissions;
        
        MultiLevelHierarchy(Role grandparentRole, Role parentRole, Role grandchildRole,
                           Set<Permission> grandparentPermissions, 
                           Set<Permission> parentPermissions,
                           Set<Permission> grandchildPermissions) {
            this.grandparentRole = grandparentRole;
            this.parentRole = parentRole;
            this.grandchildRole = grandchildRole;
            this.grandparentPermissions = grandparentPermissions;
            this.parentPermissions = parentPermissions;
            this.grandchildPermissions = grandchildPermissions;
        }
    }
}

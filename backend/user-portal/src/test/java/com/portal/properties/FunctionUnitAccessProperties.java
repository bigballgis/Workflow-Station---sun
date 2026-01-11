package com.portal.properties;

import com.portal.component.FunctionUnitAccessComponent;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 功能单元访问控制属性测试
 * 验证禁用状态访问控制和功能单元列表过滤
 */
class FunctionUnitAccessProperties {

    private FunctionUnitAccessComponent accessComponent;
    private RestTemplate restTemplate;

    @BeforeTry
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        accessComponent = new FunctionUnitAccessComponent(restTemplate);
    }

    // ==================== Property 6: 禁用状态访问控制 ====================

    @Property(tries = 20)
    @Label("属性: 禁用的功能单元应阻止访问")
    void disabledFunctionUnitShouldBlockAccess(
            @ForAll("validUserIds") String userId,
            @ForAll("functionUnitIds") String functionUnitId) {
        // 模拟功能单元已禁用
        mockFunctionUnitEnabled(functionUnitId, false);
        
        assertThatThrownBy(() -> accessComponent.checkFunctionUnitAccess(userId, functionUnitId))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitDisabledException.class)
                .hasMessageContaining("禁用");
    }

    @Property(tries = 20)
    @Label("属性: 启用的功能单元应允许有权限用户访问")
    void enabledFunctionUnitShouldAllowAuthorizedAccess(
            @ForAll("validUserIds") String userId,
            @ForAll("functionUnitIds") String functionUnitId) {
        // 模拟功能单元已启用
        mockFunctionUnitEnabled(functionUnitId, true);
        // 模拟无访问限制（空角色列表）
        mockFunctionUnitAccess(functionUnitId, Collections.emptyList());
        // 模拟用户角色
        mockUserRoles(userId, Collections.emptyList());
        
        // 不应抛出异常
        accessComponent.checkFunctionUnitAccess(userId, functionUnitId);
    }

    @Property(tries = 20)
    @Label("属性: isFunctionUnitEnabled 应正确返回启用状态")
    void isFunctionUnitEnabledShouldReturnCorrectStatus(
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll boolean enabled) {
        mockFunctionUnitEnabled(functionUnitId, enabled);
        
        boolean result = accessComponent.isFunctionUnitEnabled(functionUnitId);
        
        assertThat(result).isEqualTo(enabled);
    }

    @Property(tries = 20)
    @Label("属性: 无权限用户访问启用的功能单元应被拒绝")
    void unauthorizedUserShouldBeDeniedAccess(
            @ForAll("validUserIds") String userId,
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll("roleIds") String requiredRoleId) {
        // 模拟功能单元已启用
        mockFunctionUnitEnabled(functionUnitId, true);
        // 模拟需要特定角色
        mockFunctionUnitAccess(functionUnitId, List.of(requiredRoleId));
        // 模拟用户没有该角色
        mockUserRoles(userId, Collections.emptyList());
        
        assertThatThrownBy(() -> accessComponent.checkFunctionUnitAccess(userId, functionUnitId))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class)
                .hasMessageContaining("权限");
    }

    // ==================== Property 7: 功能单元列表过滤 ====================

    @Property(tries = 20)
    @Label("属性: 禁用的功能单元应从列表中过滤掉")
    void disabledFunctionUnitsShouldBeFilteredOut(
            @ForAll("validUserIds") String userId) {
        // 创建混合列表：一些启用，一些禁用
        List<Map<String, Object>> units = new ArrayList<>();
        units.add(createFunctionUnit("unit-1", "Unit 1", true));
        units.add(createFunctionUnit("unit-2", "Unit 2", false));
        units.add(createFunctionUnit("unit-3", "Unit 3", true));
        units.add(createFunctionUnit("unit-4", "Unit 4", false));
        
        // 模拟无访问限制
        for (Map<String, Object> unit : units) {
            mockFunctionUnitAccess((String) unit.get("id"), Collections.emptyList());
        }
        mockUserRoles(userId, Collections.emptyList());
        
        List<Map<String, Object>> filtered = accessComponent.filterAccessibleFunctionUnits(userId, units);
        
        // 只应返回启用的功能单元
        assertThat(filtered).hasSize(2);
        assertThat(filtered).allMatch(u -> Boolean.TRUE.equals(u.get("enabled")));
        assertThat(filtered.stream().map(u -> u.get("id")))
                .containsExactlyInAnyOrder("unit-1", "unit-3");
    }

    @Property(tries = 20)
    @Label("属性: 过滤后的列表应只包含用户有权限的功能单元")
    void filteredListShouldOnlyContainAuthorizedUnits(
            @ForAll("validUserIds") String userId,
            @ForAll("roleIds") String userRoleId) {
        // 创建功能单元列表
        List<Map<String, Object>> units = new ArrayList<>();
        units.add(createFunctionUnit("unit-1", "Unit 1", true));
        units.add(createFunctionUnit("unit-2", "Unit 2", true));
        units.add(createFunctionUnit("unit-3", "Unit 3", true));
        
        // unit-1 需要用户的角色
        mockFunctionUnitAccess("unit-1", List.of(userRoleId));
        // unit-2 需要其他角色
        mockFunctionUnitAccess("unit-2", List.of("other-role"));
        // unit-3 无限制
        mockFunctionUnitAccess("unit-3", Collections.emptyList());
        
        // 用户有 userRoleId
        mockUserRoles(userId, List.of(userRoleId));
        
        List<Map<String, Object>> filtered = accessComponent.filterAccessibleFunctionUnits(userId, units);
        
        // 应返回 unit-1（有权限）和 unit-3（无限制）
        assertThat(filtered).hasSize(2);
        assertThat(filtered.stream().map(u -> u.get("id")))
                .containsExactlyInAnyOrder("unit-1", "unit-3");
    }

    @Property(tries = 20)
    @Label("属性: 空列表过滤应返回空列表")
    void emptyListFilterShouldReturnEmpty(@ForAll("validUserIds") String userId) {
        List<Map<String, Object>> filtered = accessComponent.filterAccessibleFunctionUnits(userId, Collections.emptyList());
        
        assertThat(filtered).isEmpty();
    }

    @Property(tries = 20)
    @Label("属性: null列表过滤应返回空列表")
    void nullListFilterShouldReturnEmpty(@ForAll("validUserIds") String userId) {
        List<Map<String, Object>> filtered = accessComponent.filterAccessibleFunctionUnits(userId, null);
        
        assertThat(filtered).isEmpty();
    }

    @Property(tries = 20)
    @Label("属性: 所有功能单元禁用时应返回空列表")
    void allDisabledShouldReturnEmpty(@ForAll("validUserIds") String userId) {
        List<Map<String, Object>> units = new ArrayList<>();
        units.add(createFunctionUnit("unit-1", "Unit 1", false));
        units.add(createFunctionUnit("unit-2", "Unit 2", false));
        
        mockUserRoles(userId, Collections.emptyList());
        
        List<Map<String, Object>> filtered = accessComponent.filterAccessibleFunctionUnits(userId, units);
        
        assertThat(filtered).isEmpty();
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createFunctionUnit(String id, String name, boolean enabled) {
        Map<String, Object> unit = new HashMap<>();
        unit.put("id", id);
        unit.put("name", name);
        unit.put("enabled", enabled);
        return unit;
    }

    @SuppressWarnings("unchecked")
    private void mockFunctionUnitEnabled(String functionUnitId, boolean enabled) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", functionUnitId);
        response.put("enabled", enabled);
        
        when(restTemplate.exchange(
                contains("/function-units/" + functionUnitId),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(response));
    }

    @SuppressWarnings("unchecked")
    private void mockFunctionUnitAccess(String functionUnitId, List<String> roleIds) {
        List<Map<String, Object>> accessList = new ArrayList<>();
        for (String roleId : roleIds) {
            Map<String, Object> access = new HashMap<>();
            access.put("roleId", roleId);
            accessList.add(access);
        }
        
        when(restTemplate.exchange(
                contains("/function-units/" + functionUnitId + "/access"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(accessList));
    }

    @SuppressWarnings("unchecked")
    private void mockUserRoles(String userId, List<String> roleIds) {
        List<Map<String, Object>> roleList = new ArrayList<>();
        for (String roleId : roleIds) {
            Map<String, Object> role = new HashMap<>();
            role.put("id", roleId);
            roleList.add(role);
        }
        
        when(restTemplate.exchange(
                contains("/users/" + userId + "/roles"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(roleList));
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "user-" + s);
    }

    @Provide
    Arbitrary<String> functionUnitIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "fu-" + s);
    }

    @Provide
    Arbitrary<String> roleIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "role-" + s);
    }
}

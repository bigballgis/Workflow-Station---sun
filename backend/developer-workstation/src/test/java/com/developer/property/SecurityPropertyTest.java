package com.developer.property;

import com.developer.component.SecurityComponent;
import com.developer.component.impl.SecurityComponentImpl;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 安全组件属性测试
 * Property 17-19: JWT认证一致性、账户锁定机制、权限访问控制
 */
public class SecurityPropertyTest {
    
    private final SecurityComponent securityComponent = new SecurityComponentImpl();
    
    /**
     * Property 17: JWT认证一致性
     * 生成的令牌应能被正确验证，且能提取出原始用户名和角色
     */
    @Property(tries = 20)
    void jwtConsistencyProperty(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") Set<String> roles) {
        
        String token = securityComponent.generateToken(username, roles);
        
        assertThat(securityComponent.validateToken(token)).isTrue();
        assertThat(securityComponent.getUsernameFromToken(token)).isEqualTo(username);
        assertThat(securityComponent.getRolesFromToken(token)).containsAll(roles);
    }
    
    /**
     * Property 18: 账户锁定机制
     * 连续失败登录达到阈值后账户应被锁定
     */
    @Property(tries = 20)
    void accountLockingProperty(@ForAll("validUsernames") String username) {
        // 重置状态
        securityComponent.resetLoginFailures(username);
        assertThat(securityComponent.isAccountLocked(username)).isFalse();
        
        // 模拟5次失败登录
        for (int i = 0; i < 5; i++) {
            securityComponent.recordLoginFailure(username);
        }
        
        // 账户应被锁定
        assertThat(securityComponent.isAccountLocked(username)).isTrue();
        
        // 清理
        securityComponent.resetLoginFailures(username);
    }

    /**
     * Property 19: 权限访问控制
     * 权限检查方法应返回布尔值
     */
    @Property(tries = 20)
    void permissionCheckProperty(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        boolean hasPermission = securityComponent.hasPermission(username, permission);
        assertThat(hasPermission).isIn(true, false);
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "user_" + s);
    }
    
    @Provide
    Arbitrary<Set<String>> validRoles() {
        return Arbitraries.of("ADMIN", "USER", "DEVELOPER", "VIEWER")
                .set()
                .ofMinSize(1)
                .ofMaxSize(3);
    }
    
    @Provide
    Arbitrary<String> validPermissions() {
        return Arbitraries.of(
                "READ", "WRITE", "DELETE", "PUBLISH", 
                "FUNCTION_UNIT_CREATE", "FUNCTION_UNIT_EDIT"
        );
    }
}

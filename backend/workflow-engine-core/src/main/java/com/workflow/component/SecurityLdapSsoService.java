package com.workflow.component;

import com.workflow.dto.response.AuthenticationResult;
import com.workflow.dto.response.UserSecurityInfo;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全 LDAP/SSO 协作类
 *
 * 从 {@link SecurityManagerComponent} 拆分而来，负责 LDAP 与 SSO 外部认证接入：
 * 提供者注册、认证流程、LDAP 用户信息同步与组到角色的映射。纯结构搬迁，行为与原实现逐字一致。
 *
 * <p>令牌生成/缓存、安全事件记录、用户信息与角色读写等共享能力委托回门面
 * {@link SecurityManagerComponent}。认证提供者接口类型仍定义在门面中以保持签名兼容。
 */
@Slf4j
@Component
public class SecurityLdapSsoService {

    // 与门面互为循环依赖，使用 @Lazy 字段注入破环
    @Lazy
    @Autowired
    private SecurityManagerComponent securityManager;

    // LDAP/SSO配置接口
    private SecurityManagerComponent.LdapAuthenticationProvider ldapProvider;
    private SecurityManagerComponent.SsoAuthenticationProvider ssoProvider;

    /**
     * 注入门面引用（用于无 Spring 上下文的单元测试后备实例；Spring 环境下由字段注入覆盖）
     */
    void setSecurityManager(SecurityManagerComponent securityManager) {
        this.securityManager = securityManager;
    }

    /**
     * 设置LDAP认证提供者
     */
    public void setLdapProvider(SecurityManagerComponent.LdapAuthenticationProvider provider) {
        this.ldapProvider = provider;
        log.info("LDAP认证提供者已配置");
    }

    /**
     * 设置SSO认证提供者
     */
    public void setSsoProvider(SecurityManagerComponent.SsoAuthenticationProvider provider) {
        this.ssoProvider = provider;
        log.info("SSO认证提供者已配置");
    }

    /**
     * 使用LDAP认证
     */
    public AuthenticationResult authenticateWithLdap(String username, String password, String ipAddress) {
        log.info("LDAP认证请求: username={}", username);

        if (ldapProvider == null) {
            return AuthenticationResult.failure("LDAP认证未配置");
        }

        try {
            boolean isValid = ldapProvider.authenticate(username, password);

            if (!isValid) {
                securityManager.recordSecurityEvent(username, "LDAP_LOGIN_FAILED",
                        "LDAP认证失败", ipAddress);
                return AuthenticationResult.failure("LDAP认证失败");
            }

            // 同步LDAP用户信息
            syncLdapUserInfo(username);

            // 生成JWT令牌
            String accessToken = securityManager.generateAccessToken(username);
            String refreshToken = securityManager.generateRefreshToken(username);
            securityManager.cacheToken(username, accessToken, refreshToken);

            UserSecurityInfo userInfo = securityManager.getUserSecurityInfo(username);

            securityManager.recordSecurityEvent(username, "LDAP_LOGIN_SUCCESS",
                    "LDAP认证成功", ipAddress);

            return AuthenticationResult.success(accessToken, refreshToken,
                    securityManager.getJwtExpirationMs(), userInfo);

        } catch (Exception e) {
            log.error("LDAP认证失败: username={}, error={}", username, e.getMessage(), e);
            return AuthenticationResult.failure("LDAP认证过程发生错误");
        }
    }

    /**
     * 使用SSO令牌认证
     */
    public AuthenticationResult authenticateWithSso(String ssoToken, String ipAddress) {
        log.info("SSO认证请求");

        if (ssoProvider == null) {
            return AuthenticationResult.failure("SSO认证未配置");
        }

        try {
            AuthenticationResult ssoResult = ssoProvider.authenticateWithSsoToken(ssoToken);

            if (!ssoResult.isSuccess()) {
                securityManager.recordSecurityEvent("unknown", "SSO_LOGIN_FAILED",
                        "SSO认证失败: " + ssoResult.getMessage(), ipAddress);
                return ssoResult;
            }

            String username = ssoResult.getUserInfo().getUsername();

            // 生成本地JWT令牌
            String accessToken = securityManager.generateAccessToken(username);
            String refreshToken = securityManager.generateRefreshToken(username);
            securityManager.cacheToken(username, accessToken, refreshToken);

            securityManager.recordSecurityEvent(username, "SSO_LOGIN_SUCCESS",
                    "SSO认证成功", ipAddress);

            return AuthenticationResult.success(accessToken, refreshToken,
                    securityManager.getJwtExpirationMs(), ssoResult.getUserInfo());

        } catch (Exception e) {
            log.error("SSO认证失败: error={}", e.getMessage(), e);
            return AuthenticationResult.failure("SSO认证过程发生错误");
        }
    }

    /**
     * 获取SSO登录URL
     */
    public String getSsoLoginUrl(String callbackUrl) {
        if (ssoProvider == null) {
            throw new WorkflowBusinessException("SSO_NOT_CONFIGURED", "SSO authentication not configured");
        }
        return ssoProvider.getSsoLoginUrl(callbackUrl);
    }

    /**
     * 同步LDAP用户信息
     */
    private void syncLdapUserInfo(String username) {
        if (ldapProvider == null) {
            return;
        }

        try {
            Map<String, Object> attributes = ldapProvider.getUserAttributes(username);
            List<String> groups = ldapProvider.getUserGroups(username);

            // 将LDAP组映射为系统角色
            Set<String> roles = mapLdapGroupsToRoles(groups);
            securityManager.saveUserRoles(username, roles);

            log.info("LDAP用户信息同步成功: username={}, roles={}", username, roles);

        } catch (Exception e) {
            log.error("LDAP用户信息同步失败: username={}", username, e);
        }
    }

    /**
     * 将LDAP组映射为系统角色
     */
    private Set<String> mapLdapGroupsToRoles(List<String> ldapGroups) {
        Set<String> roles = new HashSet<>();

        for (String group : ldapGroups) {
            // 简单映射规则，可以根据需要扩展
            if (group.toLowerCase().contains("admin")) {
                roles.add("ADMIN");
            } else if (group.toLowerCase().contains("manager")) {
                roles.add("MANAGER");
            } else if (group.toLowerCase().contains("user")) {
                roles.add("USER");
            }
        }

        // 默认角色
        if (roles.isEmpty()) {
            roles.add("USER");
        }

        return roles;
    }
}

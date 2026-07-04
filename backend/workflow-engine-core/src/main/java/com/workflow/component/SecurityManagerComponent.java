package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.AuthenticationRequest;
import com.workflow.dto.request.RoleAssignmentRequest;
import com.workflow.dto.response.AuthenticationResult;
import com.workflow.dto.response.PermissionCheckResult;
import com.workflow.dto.response.SecurityAuditResult;
import com.workflow.dto.response.UserSecurityInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全管理组件
 *
 * 负责JWT令牌认证、RBAC权限控制、LDAP/SSO集成接口、
 * 敏感数据加密存储和传输、完整的审计日志记录
 *
 * <p>本类为门面：保留全部 public 方法签名与 public 内部类型不变，方法体委托给同包协作类：
 * <ul>
 *   <li>{@link SecurityCryptoHelper} —— 数据加解密、密码哈希</li>
 *   <li>{@link SecurityTokenManager} —— JWT 令牌生成/解析/缓存/黑名单</li>
 *   <li>{@link SecurityRbacService} —— 角色权限定义与判定、用户角色读写</li>
 *   <li>{@link SecurityAuditService} —— 安全事件记录与审计报告</li>
 *   <li>{@link SecurityLdapSsoService} —— LDAP/SSO 外部认证接入</li>
 * </ul>
 *
 * <p>构造签名保持不变（7 参），单元测试无需 Spring 容器即可构造；协作类在构造时直接 new，
 * Spring 环境下由 {@code @Lazy @Autowired} 字段覆盖以支持循环依赖，访问统一走 lazy accessor 兜底。
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component("workflowSecurityManager")
public class SecurityManagerComponent {

    // 凭证校验仍直接读取 Redis 中存储的用户口令
    private final StringRedisTemplate stringRedisTemplate;

    // JWT配置 - 从环境变量/配置文件读取
    private final String jwtSecretKey;
    private final long jwtExpirationMs;
    private final long refreshTokenExpirationMs;

    // 加密配置 - 从环境变量/配置文件读取
    private final String encryptionKey;

    // 协作类：Spring 环境下由 @Lazy @Autowired 注入（破循环依赖）；
    // 单元测试（无 Spring 上下文）下构造器直接 new，accessor 提供 null 兜底。
    @Lazy
    @Autowired
    private SecurityCryptoHelper cryptoHelperBean;
    @Lazy
    @Autowired
    private SecurityTokenManager tokenManagerBean;
    @Lazy
    @Autowired
    private SecurityRbacService rbacServiceBean;
    @Lazy
    @Autowired
    private SecurityAuditService auditServiceBean;
    @Lazy
    @Autowired
    private SecurityLdapSsoService ldapSsoServiceBean;

    // 构造期创建的后备实例（保证无 Spring 上下文的单元测试可用）
    private final SecurityCryptoHelper cryptoHelperFallback;
    private final SecurityTokenManager tokenManagerFallback;
    private final SecurityRbacService rbacServiceFallback;
    private final SecurityAuditService auditServiceFallback;
    private final SecurityLdapSsoService ldapSsoServiceFallback;

    public SecurityManagerComponent(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AuditManagerComponent auditManagerComponent,
            @Value("${jwt.secret}") String jwtSecretKey,
            @Value("${jwt.expiration:86400000}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration:604800000}") long refreshTokenExpirationMs,
            @Value("${platform.encryption.secret-key}") String encryptionKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtSecretKey = jwtSecretKey;
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.encryptionKey = encryptionKey;

        // 构造后备协作实例：保持单元测试（直接 new 门面）的行为不变。
        this.cryptoHelperFallback = new SecurityCryptoHelper();
        this.tokenManagerFallback = new SecurityTokenManager(stringRedisTemplate, objectMapper, this.cryptoHelperFallback);
        this.rbacServiceFallback = new SecurityRbacService(stringRedisTemplate, objectMapper, auditManagerComponent);
        this.auditServiceFallback = new SecurityAuditService(stringRedisTemplate, auditManagerComponent);
        this.ldapSsoServiceFallback = new SecurityLdapSsoService();
        // 后备 LDAP/SSO 协作类需要回指门面以委托共享能力
        this.ldapSsoServiceFallback.setSecurityManager(this);

        // 验证密钥长度
        if (jwtSecretKey.length() < 32) {
            log.warn("JWT密钥长度不足32字符，建议使用更长的密钥以提高安全性");
        }
        if (encryptionKey.length() < 32) {
            log.warn("加密密钥长度不足32字符，AES-256需要32字节密钥");
        }
    }

    // ==================== Lazy accessor（@Lazy 字段为 null 时回退到构造后备实例）====================

    private SecurityCryptoHelper crypto() {
        return cryptoHelperBean != null ? cryptoHelperBean : cryptoHelperFallback;
    }

    private SecurityTokenManager tokenManager() {
        return tokenManagerBean != null ? tokenManagerBean : tokenManagerFallback;
    }

    private SecurityRbacService rbac() {
        return rbacServiceBean != null ? rbacServiceBean : rbacServiceFallback;
    }

    private SecurityAuditService audit() {
        return auditServiceBean != null ? auditServiceBean : auditServiceFallback;
    }

    private SecurityLdapSsoService ldapSso() {
        return ldapSsoServiceBean != null ? ldapSsoServiceBean : ldapSsoServiceFallback;
    }

    // ==================== JWT认证方法 ====================

    /**
     * 用户认证并生成JWT令牌
     */
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        log.info("用户认证请求: username={}", request.getUsername());

        try {
            // 验证用户凭证
            boolean isValid = validateCredentials(request.getUsername(), request.getPassword());

            if (!isValid) {
                recordSecurityEvent(request.getUsername(), "LOGIN_FAILED",
                        "认证失败: 用户名或密码错误", request.getIpAddress());
                return AuthenticationResult.failure("用户名或密码错误");
            }

            // 生成JWT令牌
            String accessToken = generateAccessToken(request.getUsername());
            String refreshToken = generateRefreshToken(request.getUsername());

            // 缓存令牌
            cacheToken(request.getUsername(), accessToken, refreshToken);

            // 获取用户安全信息
            UserSecurityInfo userInfo = getUserSecurityInfo(request.getUsername());

            // 记录审计日志
            recordSecurityEvent(request.getUsername(), "LOGIN_SUCCESS",
                    "用户登录成功", request.getIpAddress());

            log.info("用户认证成功: username={}", request.getUsername());

            return AuthenticationResult.success(accessToken, refreshToken,
                    jwtExpirationMs, userInfo);

        } catch (Exception e) {
            log.error("用户认证失败: username={}, error={}", request.getUsername(), e.getMessage(), e);
            return AuthenticationResult.failure("认证过程发生错误: " + e.getMessage());
        }
    }


    /**
     * 刷新访问令牌
     */
    public AuthenticationResult refreshToken(String refreshToken) {
        log.info("刷新令牌请求");

        try {
            // 验证刷新令牌
            String username = validateRefreshToken(refreshToken);

            if (username == null) {
                return AuthenticationResult.failure("刷新令牌无效或已过期");
            }

            // 检查令牌是否在黑名单中
            if (isTokenBlacklisted(refreshToken)) {
                return AuthenticationResult.failure("刷新令牌已被撤销");
            }

            // 生成新的访问令牌
            String newAccessToken = generateAccessToken(username);
            String newRefreshToken = generateRefreshToken(username);

            // 将旧的刷新令牌加入黑名单
            blacklistToken(refreshToken);

            // 缓存新令牌
            cacheToken(username, newAccessToken, newRefreshToken);

            UserSecurityInfo userInfo = getUserSecurityInfo(username);

            log.info("令牌刷新成功: username={}", username);

            return AuthenticationResult.success(newAccessToken, newRefreshToken,
                    jwtExpirationMs, userInfo);

        } catch (Exception e) {
            log.error("令牌刷新失败: error={}", e.getMessage(), e);
            return AuthenticationResult.failure("令牌刷新失败: " + e.getMessage());
        }
    }

    /**
     * 验证访问令牌
     */
    public AuthenticationResult validateToken(String accessToken) {
        try {
            // 检查令牌是否在黑名单中
            if (isTokenBlacklisted(accessToken)) {
                return AuthenticationResult.failure("令牌已被撤销");
            }

            // 解析并验证令牌
            Map<String, Object> claims = parseToken(accessToken);

            if (claims == null) {
                return AuthenticationResult.failure("令牌无效");
            }

            // 检查过期时间
            long expiration = (Long) claims.get("exp");
            if (System.currentTimeMillis() > expiration) {
                return AuthenticationResult.failure("令牌已过期");
            }

            String username = (String) claims.get("sub");
            UserSecurityInfo userInfo = getUserSecurityInfo(username);

            return AuthenticationResult.builder()
                    .success(true)
                    .message("令牌有效")
                    .accessToken(accessToken)
                    .userInfo(userInfo)
                    .build();

        } catch (Exception e) {
            log.error("令牌验证失败: error={}", e.getMessage());
            return AuthenticationResult.failure("令牌验证失败: " + e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    public boolean logout(String username, String accessToken) {
        log.info("用户登出: username={}", username);

        try {
            // 将令牌加入黑名单
            blacklistToken(accessToken);

            // 清除用户缓存
            clearUserCache(username);

            // 记录审计日志
            recordSecurityEvent(username, "LOGOUT", "用户登出", null);

            log.info("用户登出成功: username={}", username);
            return true;

        } catch (Exception e) {
            log.error("用户登出失败: username={}, error={}", username, e.getMessage(), e);
            return false;
        }
    }

    // ==================== RBAC权限控制方法 ====================

    /**
     * 检查用户权限
     */
    public PermissionCheckResult checkPermission(String username, String resource, String action) {
        return rbac().checkPermission(username, resource, action);
    }

    /**
     * 检查用户是否有指定角色
     */
    public boolean hasRole(String username, String role) {
        return rbac().hasRole(username, role);
    }

    /**
     * 检查用户是否有任意一个指定角色
     */
    public boolean hasAnyRole(String username, String... roles) {
        return rbac().hasAnyRole(username, roles);
    }

    /**
     * 分配角色给用户
     */
    public boolean assignRole(RoleAssignmentRequest request) {
        return rbac().assignRole(request);
    }

    /**
     * 撤销用户角色
     */
    public boolean revokeRole(RoleAssignmentRequest request) {
        return rbac().revokeRole(request);
    }

    /**
     * 定义角色权限
     */
    public void defineRolePermissions(String role, Set<String> permissions) {
        rbac().defineRolePermissions(role, permissions);
    }

    /**
     * 获取角色权限
     */
    public Set<String> getRolePermissions(String role) {
        return rbac().getRolePermissions(role);
    }


    // ==================== LDAP/SSO集成接口 ====================

    /**
     * LDAP认证提供者接口
     */
    public interface LdapAuthenticationProvider {
        boolean authenticate(String username, String password);
        Map<String, Object> getUserAttributes(String username);
        List<String> getUserGroups(String username);
    }

    /**
     * SSO认证提供者接口
     */
    public interface SsoAuthenticationProvider {
        AuthenticationResult authenticateWithSsoToken(String ssoToken);
        String getSsoLoginUrl(String callbackUrl);
        boolean validateSsoSession(String sessionId);
        void logout(String sessionId);
    }

    /**
     * 设置LDAP认证提供者
     */
    public void setLdapProvider(LdapAuthenticationProvider provider) {
        ldapSso().setLdapProvider(provider);
    }

    /**
     * 设置SSO认证提供者
     */
    public void setSsoProvider(SsoAuthenticationProvider provider) {
        ldapSso().setSsoProvider(provider);
    }

    /**
     * 使用LDAP认证
     */
    public AuthenticationResult authenticateWithLdap(String username, String password, String ipAddress) {
        return ldapSso().authenticateWithLdap(username, password, ipAddress);
    }

    /**
     * 使用SSO令牌认证
     */
    public AuthenticationResult authenticateWithSso(String ssoToken, String ipAddress) {
        return ldapSso().authenticateWithSso(ssoToken, ipAddress);
    }

    /**
     * 获取SSO登录URL
     */
    public String getSsoLoginUrl(String callbackUrl) {
        return ldapSso().getSsoLoginUrl(callbackUrl);
    }

    // ==================== 数据加密方法 ====================

    /**
     * 加密敏感数据
     */
    public String encryptData(String plainText) {
        return crypto().encryptData(plainText, encryptionKey);
    }

    /**
     * 解密敏感数据
     */
    public String decryptData(String encryptedText) {
        return crypto().decryptData(encryptedText, encryptionKey);
    }

    /**
     * 哈希密码
     */
    public String hashPassword(String password) {
        return crypto().hashPassword(password);
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String password, String hashedPassword) {
        return crypto().verifyPassword(password, hashedPassword);
    }


    // ==================== 安全审计方法 ====================

    /**
     * 获取安全审计报告
     */
    public SecurityAuditResult getSecurityAuditReport(LocalDateTime startTime, LocalDateTime endTime) {
        return audit().getSecurityAuditReport(startTime, endTime);
    }

    /**
     * 记录安全事件
     */
    public void recordSecurityEvent(String username, String eventType, String description, String ipAddress) {
        audit().recordSecurityEvent(username, eventType, description, ipAddress);
    }


    // ==================== 私有辅助方法 ====================

    /**
     * 验证用户凭证。
     *
     * <p>凭证来源为 {@code security:user:{username}:password} 中存储的口令哈希；
     * 未命中时 <strong>fail-closed 返回 false</strong>，不再放行任何硬编码默认账号
     * （历史上的 {@code admin/admin123}、{@code user/user123} 后门已移除，见 SAST #1472）。</p>
     *
     * <p>注意：生产环境的用户登录由 platform-security 的 {@code BCryptPasswordEncoder} 负责；
     * 本方法及其配套的 {@code authenticate} 仅用于引擎内的令牌流转与相关测试。</p>
     */
    private boolean validateCredentials(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        String cacheKey = "security:user:" + username + ":password";
        String storedPassword = stringRedisTemplate.opsForValue().get(cacheKey);
        if (storedPassword == null) {
            return false;
        }
        return verifyPassword(password, storedPassword);
    }

    // ==================== 包内协作（供同包协作类委托回门面共享能力）====================

    /**
     * 生成访问令牌（委托令牌管理器，角色声明来自 RBAC 服务）
     */
    String generateAccessToken(String username) {
        return tokenManager().generateAccessToken(username, jwtSecretKey, jwtExpirationMs, getUserRoles(username));
    }

    /**
     * 生成刷新令牌
     */
    String generateRefreshToken(String username) {
        return tokenManager().generateRefreshToken(username, jwtSecretKey, refreshTokenExpirationMs);
    }

    /**
     * 解析令牌
     */
    private Map<String, Object> parseToken(String token) {
        return tokenManager().parseToken(token, jwtSecretKey);
    }

    /**
     * 验证刷新令牌
     */
    private String validateRefreshToken(String refreshToken) {
        return tokenManager().validateRefreshToken(refreshToken, jwtSecretKey);
    }

    /**
     * 缓存令牌
     */
    void cacheToken(String username, String accessToken, String refreshToken) {
        tokenManager().cacheToken(username, accessToken, refreshToken);
    }

    /**
     * 将令牌加入黑名单
     */
    private void blacklistToken(String token) {
        tokenManager().blacklistToken(token);
    }

    /**
     * 检查令牌是否在黑名单中
     */
    private boolean isTokenBlacklisted(String token) {
        return tokenManager().isTokenBlacklisted(token);
    }

    /**
     * 获取用户安全信息
     */
    public UserSecurityInfo getUserSecurityInfo(String username) {
        return rbac().getUserSecurityInfo(username);
    }

    /**
     * 获取用户角色
     */
    public Set<String> getUserRoles(String username) {
        return rbac().getUserRoles(username);
    }

    /**
     * 保存用户角色
     */
    void saveUserRoles(String username, Set<String> roles) {
        rbac().saveUserRoles(username, roles);
    }

    /**
     * 清除用户缓存
     */
    private void clearUserCache(String username) {
        rbac().clearUserCache(username);
    }

    /**
     * 访问 JWT 访问令牌有效期（供 LDAP/SSO 协作类构造认证结果）
     */
    long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    /**
     * 初始化默认角色权限
     */
    public void initializeDefaultRolePermissions() {
        rbac().initializeDefaultRolePermissions();
    }
}

package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.AuthenticationRequest;
import com.workflow.dto.request.RoleAssignmentRequest;
import com.workflow.dto.response.AuthenticationResult;
import com.workflow.dto.response.PermissionCheckResult;
import com.workflow.dto.response.SecurityAuditResult;
import com.workflow.dto.response.UserSecurityInfo;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 安全管理组件
 * 
 * 负责JWT令牌认证、RBAC权限控制、LDAP/SSO集成接口、
 * 敏感数据加密存储和传输、完整的审计日志记录
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
public class SecurityManagerComponent {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditManagerComponent auditManagerComponent;
    
    // JWT配置 - 从环境变量/配置文件读取
    private final String jwtSecretKey;
    private final long jwtExpirationMs;
    private final long refreshTokenExpirationMs;
    
    // 加密配置 - 从环境变量/配置文件读取
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private final String encryptionKey;
    
    public SecurityManagerComponent(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AuditManagerComponent auditManagerComponent,
            @Value("${jwt.secret}") String jwtSecretKey,
            @Value("${jwt.expiration:86400000}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration:604800000}") long refreshTokenExpirationMs,
            @Value("${platform.encryption.secret-key}") String encryptionKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.auditManagerComponent = auditManagerComponent;
        this.jwtSecretKey = jwtSecretKey;
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.encryptionKey = encryptionKey;
        
        // 验证密钥长度
        if (jwtSecretKey.length() < 32) {
            log.warn("JWT密钥长度不足32字符，建议使用更长的密钥以提高安全性");
        }
        if (encryptionKey.length() < 32) {
            log.warn("加密密钥长度不足32字符，AES-256需要32字节密钥");
        }
    }
    
    // 缓存键前缀
    private static final String TOKEN_CACHE_PREFIX = "security:token:";
    private static final String USER_CACHE_PREFIX = "security:user:";
    private static final String ROLE_CACHE_PREFIX = "security:role:";
    private static final String PERMISSION_CACHE_PREFIX = "security:permission:";
    private static final String BLACKLIST_PREFIX = "security:blacklist:";
    
    // 内存缓存（用于角色和权限定义）
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, UserSecurityInfo> userCache = new ConcurrentHashMap<>();
    
    // LDAP/SSO配置接口
    private LdapAuthenticationProvider ldapProvider;
    private SsoAuthenticationProvider ssoProvider;

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
        this.ldapProvider = provider;
        log.info("LDAP认证提供者已配置");
    }

    /**
     * 设置SSO认证提供者
     */
    public void setSsoProvider(SsoAuthenticationProvider provider) {
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
                recordSecurityEvent(username, "LDAP_LOGIN_FAILED", 
                        "LDAP认证失败", ipAddress);
                return AuthenticationResult.failure("LDAP认证失败");
            }
            
            // 同步LDAP用户信息
            syncLdapUserInfo(username);
            
            // 生成JWT令牌
            String accessToken = generateAccessToken(username);
            String refreshToken = generateRefreshToken(username);
            cacheToken(username, accessToken, refreshToken);
            
            UserSecurityInfo userInfo = getUserSecurityInfo(username);
            
            recordSecurityEvent(username, "LDAP_LOGIN_SUCCESS", 
                    "LDAP认证成功", ipAddress);
            
            return AuthenticationResult.success(accessToken, refreshToken, 
                    jwtExpirationMs, userInfo);
                    
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
                recordSecurityEvent("unknown", "SSO_LOGIN_FAILED", 
                        "SSO认证失败: " + ssoResult.getMessage(), ipAddress);
                return ssoResult;
            }
            
            String username = ssoResult.getUserInfo().getUsername();
            
            // 生成本地JWT令牌
            String accessToken = generateAccessToken(username);
            String refreshToken = generateRefreshToken(username);
            cacheToken(username, accessToken, refreshToken);
            
            recordSecurityEvent(username, "SSO_LOGIN_SUCCESS", 
                    "SSO认证成功", ipAddress);
            
            return AuthenticationResult.success(accessToken, refreshToken, 
                    jwtExpirationMs, ssoResult.getUserInfo());
                    
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
            throw new WorkflowBusinessException("SSO_NOT_CONFIGURED", "SSO认证未配置");
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
            saveUserRoles(username, roles);
            
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

    // ==================== 数据加密方法 ====================

    /**
     * 加密敏感数据
     */
    public String encryptData(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // 将IV和加密数据组合
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            log.error("数据加密失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("ENCRYPTION_FAILED", "数据加密失败");
        }
    }

    /**
     * 解密敏感数据
     */
    public String decryptData(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);
            
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("数据解密失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("DECRYPTION_FAILED", "数据解密失败");
        }
    }

    /**
     * 哈希密码
     */
    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("密码哈希失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("HASH_FAILED", "密码哈希失败");
        }
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String password, String hashedPassword) {
        String hash = hashPassword(password);
        return hash.equals(hashedPassword);
    }

    /**
     * 获取加密密钥
     */
    private SecretKey getEncryptionKey() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32]; // AES-256需要32字节密钥
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key, "AES");
    }


    // ==================== 安全审计方法 ====================

    /**
     * 获取安全审计报告
     */
    public SecurityAuditResult getSecurityAuditReport(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("生成安全审计报告: startTime={}, endTime={}", startTime, endTime);
        
        try {
            SecurityAuditResult result = SecurityAuditResult.builder()
                    .reportTime(LocalDateTime.now())
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
            
            // 统计登录事件
            long successfulLogins = countSecurityEvents("LOGIN_SUCCESS", startTime, endTime);
            long failedLogins = countSecurityEvents("LOGIN_FAILED", startTime, endTime);
            result.setSuccessfulLogins(successfulLogins);
            result.setFailedLogins(failedLogins);
            
            // 统计权限变更
            long roleAssignments = countSecurityEvents("ROLE_ASSIGNED", startTime, endTime);
            long roleRevocations = countSecurityEvents("ROLE_REVOKED", startTime, endTime);
            result.setRoleAssignments(roleAssignments);
            result.setRoleRevocations(roleRevocations);
            
            // 检测可疑活动
            List<SecurityAuditResult.SuspiciousActivity> suspiciousActivities = 
                    detectSuspiciousActivities(startTime, endTime);
            result.setSuspiciousActivities(suspiciousActivities);
            
            // 计算安全评分
            int securityScore = calculateSecurityScore(result);
            result.setSecurityScore(securityScore);
            
            log.info("安全审计报告生成完成: securityScore={}", securityScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("生成安全审计报告失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("AUDIT_REPORT_FAILED", "生成安全审计报告失败");
        }
    }

    /**
     * 检测可疑活动
     */
    private List<SecurityAuditResult.SuspiciousActivity> detectSuspiciousActivities(
            LocalDateTime startTime, LocalDateTime endTime) {
        
        List<SecurityAuditResult.SuspiciousActivity> activities = new ArrayList<>();
        
        // 检测暴力破解尝试（同一用户多次登录失败）
        Map<String, Long> failedLoginsByUser = getFailedLoginsByUser(startTime, endTime);
        for (Map.Entry<String, Long> entry : failedLoginsByUser.entrySet()) {
            if (entry.getValue() >= 5) {
                activities.add(SecurityAuditResult.SuspiciousActivity.builder()
                        .type("BRUTE_FORCE_ATTEMPT")
                        .description("用户 " + entry.getKey() + " 在时间段内有 " + entry.getValue() + " 次登录失败")
                        .severity("HIGH")
                        .username(entry.getKey())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }
        
        // 检测异常IP登录
        Map<String, Set<String>> userIpMap = getUserLoginIps(startTime, endTime);
        for (Map.Entry<String, Set<String>> entry : userIpMap.entrySet()) {
            if (entry.getValue().size() > 5) {
                activities.add(SecurityAuditResult.SuspiciousActivity.builder()
                        .type("MULTIPLE_IP_LOGIN")
                        .description("用户 " + entry.getKey() + " 从 " + entry.getValue().size() + " 个不同IP登录")
                        .severity("MEDIUM")
                        .username(entry.getKey())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }
        
        return activities;
    }

    /**
     * 计算安全评分
     */
    private int calculateSecurityScore(SecurityAuditResult result) {
        int score = 100;
        
        // 登录失败率影响评分
        long totalLogins = result.getSuccessfulLogins() + result.getFailedLogins();
        if (totalLogins > 0) {
            double failureRate = (double) result.getFailedLogins() / totalLogins;
            if (failureRate > 0.3) {
                score -= 20;
            } else if (failureRate > 0.1) {
                score -= 10;
            }
        }
        
        // 可疑活动影响评分
        if (result.getSuspiciousActivities() != null) {
            for (SecurityAuditResult.SuspiciousActivity activity : result.getSuspiciousActivities()) {
                if ("HIGH".equals(activity.getSeverity())) {
                    score -= 15;
                } else if ("MEDIUM".equals(activity.getSeverity())) {
                    score -= 10;
                } else {
                    score -= 5;
                }
            }
        }
        
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 记录安全事件
     */
    public void recordSecurityEvent(String username, String eventType, String description, String ipAddress) {
        try {
            // 存储到Redis用于统计
            String eventKey = "security:event:" + eventType + ":" + username + ":" + System.currentTimeMillis();
            Map<String, String> eventData = new HashMap<>();
            eventData.put("username", username);
            eventData.put("eventType", eventType);
            eventData.put("description", description);
            eventData.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
            eventData.put("timestamp", LocalDateTime.now().toString());
            
            stringRedisTemplate.opsForHash().putAll(eventKey, eventData);
            stringRedisTemplate.expire(eventKey, Duration.ofDays(30));
            
            // 记录到审计日志
            AuditOperationType operationType = mapEventTypeToAuditOperation(eventType);
            if (operationType != null) {
                auditManagerComponent.recordAuditLog(
                        operationType,
                        AuditResourceType.USER,
                        username,
                        username,
                        eventType.contains("SUCCESS") ? "SUCCESS" : "FAILED"
                );
            }
            
        } catch (Exception e) {
            log.error("记录安全事件失败: username={}, eventType={}", username, eventType, e);
        }
    }

    /**
     * 统计安全事件数量
     */
    private long countSecurityEvents(String eventType, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:" + eventType + ":*");
            if (keys == null) {
                return 0;
            }
            
            long count = 0;
            for (String key : keys) {
                Object timestampObj = stringRedisTemplate.opsForHash().get(key, "timestamp");
                if (timestampObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        count++;
                    }
                }
            }
            
            return count;
            
        } catch (Exception e) {
            log.error("统计安全事件失败: eventType={}", eventType, e);
            return 0;
        }
    }

    /**
     * 获取用户登录失败次数
     */
    private Map<String, Long> getFailedLoginsByUser(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Long> result = new HashMap<>();
        
        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:LOGIN_FAILED:*");
            if (keys == null) {
                return result;
            }
            
            for (String key : keys) {
                Map<Object, Object> eventData = stringRedisTemplate.opsForHash().entries(key);
                Object timestampObj = eventData.get("timestamp");
                Object usernameObj = eventData.get("username");
                
                if (timestampObj != null && usernameObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        String username = usernameObj.toString();
                        result.merge(username, 1L, Long::sum);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("获取用户登录失败次数失败", e);
        }
        
        return result;
    }

    /**
     * 获取用户登录IP
     */
    private Map<String, Set<String>> getUserLoginIps(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Set<String>> result = new HashMap<>();
        
        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:LOGIN_SUCCESS:*");
            if (keys == null) {
                return result;
            }
            
            for (String key : keys) {
                Map<Object, Object> eventData = stringRedisTemplate.opsForHash().entries(key);
                Object timestampObj = eventData.get("timestamp");
                Object usernameObj = eventData.get("username");
                Object ipAddressObj = eventData.get("ipAddress");
                
                if (timestampObj != null && usernameObj != null && ipAddressObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        String username = usernameObj.toString();
                        String ipAddress = ipAddressObj.toString();
                        result.computeIfAbsent(username, k -> new HashSet<>()).add(ipAddress);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("获取用户登录IP失败", e);
        }
        
        return result;
    }

    /**
     * 映射事件类型到审计操作类型
     */
    private AuditOperationType mapEventTypeToAuditOperation(String eventType) {
        return switch (eventType) {
            case "LOGIN_SUCCESS", "LDAP_LOGIN_SUCCESS", "SSO_LOGIN_SUCCESS" -> AuditOperationType.LOGIN;
            case "LOGOUT" -> AuditOperationType.LOGOUT;
            case "ROLE_ASSIGNED" -> AuditOperationType.ASSIGN_ROLE;
            case "ROLE_REVOKED" -> AuditOperationType.REVOKE_ROLE;
            default -> null;
        };
    }


    // ==================== 私有辅助方法 ====================

    /**
     * 验证用户凭证
     */
    private boolean validateCredentials(String username, String password) {
        // 简化实现：从缓存或数据库验证
        // 实际应用中应该从用户数据库验证
        String cacheKey = USER_CACHE_PREFIX + username + ":password";
        String storedPassword = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (storedPassword == null) {
            // 默认测试用户
            if ("admin".equals(username) && "admin123".equals(password)) {
                return true;
            }
            if ("user".equals(username) && "user123".equals(password)) {
                return true;
            }
            return false;
        }
        
        return verifyPassword(password, storedPassword);
    }

    /**
     * 生成访问令牌
     */
    private String generateAccessToken(String username) {
        long now = System.currentTimeMillis();
        long expiration = now + jwtExpirationMs;
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("iat", now);
        claims.put("exp", expiration);
        claims.put("type", "access");
        claims.put("roles", getUserRoles(username));
        
        try {
            String payload = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            
            // 简化的签名（实际应使用HMAC-SHA256）
            String signature = hashPassword(encodedPayload + jwtSecretKey);
            
            return encodedPayload + "." + signature;
            
        } catch (JsonProcessingException e) {
            throw new WorkflowBusinessException("TOKEN_GENERATION_FAILED", "令牌生成失败");
        }
    }

    /**
     * 生成刷新令牌
     */
    private String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        long expiration = now + refreshTokenExpirationMs;
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("iat", now);
        claims.put("exp", expiration);
        claims.put("type", "refresh");
        claims.put("jti", UUID.randomUUID().toString());
        
        try {
            String payload = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            
            String signature = hashPassword(encodedPayload + jwtSecretKey);
            
            return encodedPayload + "." + signature;
            
        } catch (JsonProcessingException e) {
            throw new WorkflowBusinessException("TOKEN_GENERATION_FAILED", "刷新令牌生成失败");
        }
    }

    /**
     * 解析令牌
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return null;
            }
            
            String encodedPayload = parts[0];
            String signature = parts[1];
            
            // 验证签名
            String expectedSignature = hashPassword(encodedPayload + jwtSecretKey);
            if (!expectedSignature.equals(signature)) {
                return null;
            }
            
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), 
                    StandardCharsets.UTF_8);
            
            return objectMapper.readValue(payload, Map.class);
            
        } catch (Exception e) {
            log.error("令牌解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证刷新令牌
     */
    private String validateRefreshToken(String refreshToken) {
        Map<String, Object> claims = parseToken(refreshToken);
        
        if (claims == null) {
            return null;
        }
        
        // 检查令牌类型
        if (!"refresh".equals(claims.get("type"))) {
            return null;
        }
        
        // 检查过期时间
        long expiration = ((Number) claims.get("exp")).longValue();
        if (System.currentTimeMillis() > expiration) {
            return null;
        }
        
        return (String) claims.get("sub");
    }

    /**
     * 缓存令牌
     */
    private void cacheToken(String username, String accessToken, String refreshToken) {
        String tokenKey = TOKEN_CACHE_PREFIX + username;
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("createdAt", LocalDateTime.now().toString());
        
        stringRedisTemplate.opsForHash().putAll(tokenKey, tokens);
        stringRedisTemplate.expire(tokenKey, Duration.ofDays(7));
    }

    /**
     * 将令牌加入黑名单
     */
    private void blacklistToken(String token) {
        String blacklistKey = BLACKLIST_PREFIX + hashPassword(token).substring(0, 32);
        stringRedisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofDays(7));
    }

    /**
     * 检查令牌是否在黑名单中
     */
    private boolean isTokenBlacklisted(String token) {
        String blacklistKey = BLACKLIST_PREFIX + hashPassword(token).substring(0, 32);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey));
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
    private void saveUserRoles(String username, Set<String> roles) {
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
    private void clearUserCache(String username) {
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

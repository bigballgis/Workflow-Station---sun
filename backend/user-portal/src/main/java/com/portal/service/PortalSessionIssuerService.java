package com.portal.service;

import com.platform.common.i18n.I18nService;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.User;
import com.platform.security.service.UserRoleService;
import com.portal.dto.LoginRequest;
import com.portal.dto.LoginResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门户登录成功后签发 JWT（密码登录与 SSO exchange 共用），含工作台 / UBR 逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalSessionIssuerService {

    private static final String CLAIM_ACTIVE_BUSINESS_UNIT_ID = "activeBusinessUnitId";
    private static final String CLAIM_ACTIVE_ROLE_ID = "activeRoleId";
    private static final String CLAIM_PORTAL_ACCESS_MODE = "portalAccessMode";
    public static final String PORTAL_ACCESS_MODE_SELF_SERVICE = "PERMISSION_SELF_SERVICE_ONLY";
    public static final String PORTAL_ACCESS_MODE_FULL = "FULL";
    private static final String REFRESH_TYPE = "refresh";

    private final JdbcTemplate jdbcTemplate;
    private final UserRoleService userRoleService;
    private final I18nService i18nService;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    /**
     * 密码已校验且用户已持久化（含 lastLogin）后调用；{@link LoginRequest} 中 workspace 字段用于多条 UBR 场景。
     */
    public ResponseEntity<LoginResponse> issuePortalSession(User user, LoginRequest request, HttpServletRequest httpRequest) {
        return issuePortalSession(user, request.getWorkspaceBusinessUnitId(), request.getWorkspaceRoleId(), httpRequest,
                user.getUsername());
    }

    /**
     * SSO exchange：身份已由 admin-center 校验，仅处理工作台与签发令牌。
     */
    public ResponseEntity<LoginResponse> issuePortalSession(User user, String workspaceBusinessUnitId,
                                                             String workspaceRoleId, HttpServletRequest httpRequest,
                                                             String logUsername) {
        try {
            String userId = user.getId();
            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);

            if (wctx.size() > 1) {
                boolean missingSelection = workspaceBusinessUnitId == null || workspaceBusinessUnitId.isBlank()
                        || workspaceRoleId == null || workspaceRoleId.isBlank();
                if (missingSelection) {
                    List<LoginResponse.WorkspaceContextOption> options = wctx.stream()
                            .map(r -> LoginResponse.WorkspaceContextOption.builder()
                                    .businessUnitId(r.getBusinessUnitId())
                                    .roleId(r.getRoleId())
                                    .businessUnitName(r.getBusinessUnitName())
                                    .roleCode(r.getRoleCode())
                                    .roleName(r.getRoleName())
                                    .build())
                            .collect(Collectors.toList());
                    return ResponseEntity.badRequest().body(LoginResponse.builder()
                            .loginErrorCode("WORKSPACE_CONTEXT_REQUIRED")
                            .workspaceContexts(options)
                            .build());
                }
            }

            String activeBu = null;
            String activeRoleId = null;
            if (!wctx.isEmpty()) {
                if (wctx.size() == 1) {
                    activeBu = wctx.get(0).getBusinessUnitId();
                    activeRoleId = wctx.get(0).getRoleId();
                } else {
                    activeBu = workspaceBusinessUnitId.trim();
                    activeRoleId = workspaceRoleId.trim();
                }
                if (!portalWorkspaceAuthService.hasContext(userId, activeBu, activeRoleId)) {
                    throw new RuntimeException(i18nService.getMessage("auth.invalid_credentials"));
                }
            }

            LoginBundle bundle = buildRolesAndPermissions(user, activeBu, activeRoleId);
            String portalAccessMode = portalAccessModeForWorkspace(wctx);
            String accessToken = generateToken(user, bundle.roles, bundle.permissions, activeBu, activeRoleId, portalAccessMode);
            String refreshToken = generateRefreshToken(userId, activeBu, activeRoleId, portalAccessMode);

            log.info("User {} portal session issued", logUsername);

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtExpiration / 1000)
                    .user(toUserLoginInfo(user, bundle, activeBu, activeRoleId, wctx.size() > 1, portalAccessMode))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Portal session issue failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .message(e.getMessage() != null ? e.getMessage() : "Login failed")
                    .build());
        }
    }

    private List<LoginResponse.RoleWithSource> buildRolesWithSources(List<UserEffectiveRole> effectiveRoles) {
        List<LoginResponse.RoleWithSource> result = new ArrayList<>();
        for (UserEffectiveRole role : effectiveRoles) {
            for (var source : role.getSources()) {
                result.add(LoginResponse.RoleWithSource.builder()
                        .roleCode(role.getRoleCode())
                        .roleName(role.getRoleName())
                        .sourceType(source.getSourceType())
                        .sourceId(source.getSourceId())
                        .sourceName(source.getSourceName())
                        .build());
            }
        }
        return result;
    }

    private String generateToken(User user, List<String> roles, List<String> permissions,
                                 String activeBusinessUnitId, String activeRoleId, String portalAccessMode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        var builder = Jwts.builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("language", user.getLanguage())
                .claim(CLAIM_PORTAL_ACCESS_MODE, portalAccessMode != null ? portalAccessMode : PORTAL_ACCESS_MODE_FULL)
                .issuedAt(now)
                .expiration(expiry);
        if (activeBusinessUnitId != null && !activeBusinessUnitId.isBlank()) {
            builder.claim(CLAIM_ACTIVE_BUSINESS_UNIT_ID, activeBusinessUnitId);
        }
        if (activeRoleId != null && !activeRoleId.isBlank()) {
            builder.claim(CLAIM_ACTIVE_ROLE_ID, activeRoleId);
        }
        return builder.signWith(getSigningKey()).compact();
    }

    private String generateRefreshToken(String userId, String activeBusinessUnitId, String activeRoleId, String portalAccessMode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 604800000);

        var builder = Jwts.builder()
                .subject(userId)
                .claim("type", REFRESH_TYPE)
                .claim(CLAIM_PORTAL_ACCESS_MODE, portalAccessMode != null ? portalAccessMode : PORTAL_ACCESS_MODE_FULL)
                .issuedAt(now)
                .expiration(expiry);
        if (activeBusinessUnitId != null && !activeBusinessUnitId.isBlank()) {
            builder.claim(CLAIM_ACTIVE_BUSINESS_UNIT_ID, activeBusinessUnitId);
        }
        if (activeRoleId != null && !activeRoleId.isBlank()) {
            builder.claim(CLAIM_ACTIVE_ROLE_ID, activeRoleId);
        }
        return builder.signWith(getSigningKey()).compact();
    }

    private static String portalAccessModeForWorkspace(List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx) {
        if (wctx == null || wctx.isEmpty()) {
            return PORTAL_ACCESS_MODE_SELF_SERVICE;
        }
        return PORTAL_ACCESS_MODE_FULL;
    }

    private record LoginBundle(List<String> roles, List<String> permissions,
                              List<LoginResponse.RoleWithSource> rolesWithSources,
                              String activeBusinessUnitName, String activeRoleName) {
    }

    private LoginBundle buildRolesAndPermissions(User user, String activeBusinessUnitId, String activeRoleId) {
        String userId = user.getId();
        List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
        List<UserEffectiveRole> effectiveAll = userRoleService.getEffectiveRolesForUser(userId);

        if (wctx.isEmpty()) {
            List<String> roles = effectiveAll.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());
            if (roles.isEmpty()) {
                roles = new ArrayList<>(getRolesForUserLegacy(userId));
            }
            List<String> permissions = new ArrayList<>(userRoleService.getPermissionsForUser(userId));
            if (permissions.isEmpty()) {
                permissions = new ArrayList<>(getPermissionsForRoles(roles));
            }
            return new LoginBundle(roles, permissions, buildRolesWithSources(effectiveAll), null, null);
        }

        List<String> unboundedCodes = effectiveAll.stream()
                .filter(r -> "BU_UNBOUNDED".equals(r.getRoleType()))
                .map(UserEffectiveRole::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        PortalWorkspaceAuthService.WorkspaceContextRow row = wctx.stream()
                .filter(r -> activeBusinessUnitId != null && activeBusinessUnitId.equals(r.getBusinessUnitId())
                        && activeRoleId != null && activeRoleId.equals(r.getRoleId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("workspace context not found"));

        List<String> roles = new ArrayList<>();
        if (row.getRoleCode() != null && !row.getRoleCode().isBlank()) {
            roles.add(row.getRoleCode());
        }
        roles.addAll(unboundedCodes);
        roles = roles.stream().distinct().collect(Collectors.toList());

        LinkedHashSet<String> perms = new LinkedHashSet<>(portalWorkspaceAuthService.permissionsForRoleId(activeRoleId));
        perms.addAll(userRoleService.getPermissionsForRoleCodes(unboundedCodes));

        return new LoginBundle(new ArrayList<>(roles), new ArrayList<>(perms), buildRolesWithSources(effectiveAll),
                row.getBusinessUnitName(), row.getRoleName());
    }

    private LoginResponse.UserLoginInfo toUserLoginInfo(User user, LoginBundle bundle,
                                                        String activeBusinessUnitId, String activeRoleId,
                                                        boolean workspaceSwitcherVisible, String portalAccessMode) {
        return LoginResponse.UserLoginInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getFullName() != null && !user.getFullName().isEmpty()
                        ? user.getFullName()
                        : (user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                        ? user.getDisplayName()
                        : user.getUsername()))
                .email(user.getEmail())
                .roles(bundle.roles)
                .permissions(bundle.permissions)
                .rolesWithSources(bundle.rolesWithSources)
                .language(user.getLanguage())
                .activeBusinessUnitId(activeBusinessUnitId)
                .activeBusinessUnitName(bundle.activeBusinessUnitName)
                .activeRoleId(activeRoleId)
                .activeRoleName(bundle.activeRoleName)
                .workspaceSwitcherVisible(workspaceSwitcherVisible)
                .portalAccessMode(portalAccessMode != null ? portalAccessMode : PORTAL_ACCESS_MODE_FULL)
                .build();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private List<String> getRolesForUserLegacy(String userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT r.code FROM sys_role_assignments ra " +
                            "JOIN sys_roles r ON ra.role_id = r.id " +
                            "WHERE ra.target_type = 'USER' AND ra.target_id = ? AND r.status = 'ACTIVE' " +
                            "AND (ra.valid_from IS NULL OR ra.valid_from <= NOW()) " +
                            "AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())",
                    String.class, userId);
        } catch (Exception e) {
            return List.of("USER");
        }
    }

    private List<String> getPermissionsForRoles(List<String> roles) {
        List<String> permissions = new ArrayList<>();
        for (String role : roles) {
            switch (role) {
                case "MANAGER" -> permissions.addAll(List.of("task:read", "task:write", "task:approve", "team:manage"));
                case "USER" -> permissions.addAll(List.of("task:read", "task:write", "process:start"));
                default -> permissions.add("basic:access");
            }
        }
        return permissions.stream().distinct().toList();
    }
}

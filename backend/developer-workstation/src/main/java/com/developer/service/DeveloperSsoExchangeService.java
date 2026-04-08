package com.developer.service;

import com.developer.dto.LoginResponse;
import com.developer.entity.User;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.UserRoleService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DEV 专用：SSO exchange 后为设计器签发与密码登录一致的 JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperSsoExchangeService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRoleService userRoleService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public LoginResponse issueSession(User user) {
        List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(user.getId().toString());
        List<String> roles = effectiveRoles.stream()
                .map(UserEffectiveRole::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
        if (roles.isEmpty()) {
            roles = getRolesForUserLegacy(user.getId());
        }
        List<String> permissions = getPermissionsForRoles(roles);
        List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);
        String accessToken = generateToken(user, roles, permissions);
        String refreshToken = generateRefreshToken(user.getId());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpiration / 1000)
                .user(LoginResponse.UserLoginInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getFullName() != null && !user.getFullName().isEmpty()
                                ? user.getFullName()
                                : (user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                                ? user.getDisplayName()
                                : user.getUsername()))
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .rolesWithSources(rolesWithSources)
                        .language(user.getLanguage())
                        .build())
                .build();
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

    private String generateToken(User user, List<String> roles, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("displayName", user.getFullName() != null && !user.getFullName().isEmpty()
                        ? user.getFullName()
                        : (user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                        ? user.getDisplayName()
                        : user.getUsername()))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("language", user.getLanguage())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 604800000);
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
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
            String sql = "SELECT DISTINCT r.code FROM sys_virtual_group_members vgm " +
                    "JOIN sys_virtual_group_roles vgr ON vgm.group_id = vgr.virtual_group_id " +
                    "JOIN sys_roles r ON vgr.role_id = r.id " +
                    "WHERE vgm.user_id = ?";
            List<String> roles = jdbcTemplate.queryForList(sql, String.class, userId);
            if (roles.isEmpty()) {
                return List.of("DEVELOPER");
            }
            return roles;
        } catch (Exception e) {
            return List.of("DEVELOPER");
        }
    }

    private List<String> getPermissionsForRoles(List<String> roles) {
        List<String> permissions = userRoleService.getPermissionsForRoleCodes(roles);
        if (permissions.isEmpty()) {
            return List.of("basic:access");
        }
        return permissions;
    }
}

package com.portal.controller;

import com.platform.security.config.JwtProperties;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import com.platform.common.i18n.I18nService;
import com.portal.dto.ChangePasswordRequest;
import com.portal.dto.LoginRequest;
import com.portal.dto.LoginResponse;
import com.portal.dto.SwitchWorkspaceRequest;
import com.portal.service.PortalSessionIssuerService;
import com.portal.service.PortalWorkspaceAuthService;
import com.platform.security.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLAIM_ACTIVE_BUSINESS_UNIT_ID = "activeBusinessUnitId";
    private static final String CLAIM_ACTIVE_ROLE_ID = "activeRoleId";
    private static final String CLAIM_PORTAL_ACCESS_MODE = "portalAccessMode";
    /** When no UBR (|C|=0), portal allows only permission self-service and other whitelist APIs */
    public static final String PORTAL_ACCESS_MODE_SELF_SERVICE = "PERMISSION_SELF_SERVICE_ONLY";
    public static final String PORTAL_ACCESS_MODE_FULL = "FULL";
    private static final String REFRESH_TYPE = "refresh";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final UserRoleService userRoleService;
    private final I18nService i18nService;
    private final JwtTokenService jwtTokenService;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;
    private final PortalSessionIssuerService portalSessionIssuerService;
    private final JwtProperties jwtProperties;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String ipAddress = getClientIpAddress(httpRequest);
        log.info("Login attempt for user: {} from {}", request.getUsername(), ipAddress);
        
        try {
            log.debug("Looking up user: {}", request.getUsername());
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.invalid_credentials")));
            
            log.debug("User found: {}, status: {}", user.getUsername(), user.getStatus());
            
            if (user.isLocked()) {
                log.warn("User {} is locked", user.getUsername());
                throw new RuntimeException(i18nService.getMessage("auth.account_locked"));
            }
            
            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                log.warn("User {} is inactive", user.getUsername());
                throw new RuntimeException(i18nService.getMessage("auth.account_disabled"));
            }
            
            log.debug("Checking password for user: {}", user.getUsername());
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Password mismatch for user: {}", user.getUsername());
                user.incrementFailedLoginCount();
                if (user.getFailedLoginCount() >= 5) {
                    user.setStatus(UserStatus.LOCKED);
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                }
                userRepository.save(user);
                throw new RuntimeException(i18nService.getMessage("auth.invalid_credentials"));
            }
            
            log.debug("Password matched, updating login info");
            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ipAddress);
            userRepository.save(user);

            return portalSessionIssuerService.issuePortalSession(user, request, httpRequest, httpResponse);
        } catch (RuntimeException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .message(e.getMessage() != null ? e.getMessage() : i18nService.getMessage("auth.login_failed"))
                    .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtTokenService.blacklistToken(token);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> request, HttpServletResponse httpResponse) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            if (jwtTokenService.isBlacklisted(refreshToken)) {
                return ResponseEntity.status(401).build();
            }
            Claims claims = parseToken(refreshToken);
            if (claims.getExpiration().before(new Date())) {
                return ResponseEntity.status(401).build();
            }
            jwtTokenService.blacklistToken(refreshToken);
            String userId = claims.getSubject();
            if (!REFRESH_TYPE.equals(claims.get("type", String.class))) {
                return ResponseEntity.status(401).build();
            }
            String activeBu = claims.get(CLAIM_ACTIVE_BUSINESS_UNIT_ID, String.class);
            String activeRoleId = claims.get(CLAIM_ACTIVE_ROLE_ID, String.class);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
            String[] resolved = resolveActiveWorkspaceClaims(wctx, activeBu, activeRoleId);
            activeBu = resolved[0];
            activeRoleId = resolved[1];
            if (!wctx.isEmpty()) {
                if (activeBu == null || activeRoleId == null
                        || !portalWorkspaceAuthService.hasContext(userId, activeBu, activeRoleId)) {
                    return ResponseEntity.status(401).build();
                }
            }

            String portalAccessMode = portalAccessModeForWorkspace(wctx);
            LoginBundle bundle = buildRolesAndPermissions(user, activeBu, activeRoleId);
            String newAccessToken = generateToken(user, bundle.roles, bundle.permissions, activeBu, activeRoleId, portalAccessMode);
            String newRefreshToken = generateRefreshToken(userId, activeBu, activeRoleId, portalAccessMode);

            // Set httpOnly cookies for new tokens
            // Service-specific cookie names (e.g. up_access_token) avoid cross-app overwrite on same origin. See JwtProperties#cookieNames.
            Cookie accessTokenCookie = new Cookie(jwtProperties.getPrimaryCookieName(), newAccessToken);
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(false);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge((int)(jwtExpiration / 1000));
            accessTokenCookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(accessTokenCookie);

            Cookie refreshTokenCookie = new Cookie(jwtProperties.getRefreshCookieName(), newRefreshToken);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
            refreshTokenCookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(refreshTokenCookie);

            return ResponseEntity.ok(Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", newRefreshToken,
                    "expiresIn", jwtExpiration / 1000
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Void> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.user_not_found")));
            if (!passwordEncoder.matches(body.getOldPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(400).build();
            }
            user.setPasswordHash(passwordEncoder.encode(body.getNewPassword()));
            userRepository.save(user);
            jwtTokenService.blacklistToken(token);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserLoginInfo> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String token = extractAccessToken(request, authHeader);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            String activeBu = claims.get(CLAIM_ACTIVE_BUSINESS_UNIT_ID, String.class);
            String activeRoleId = claims.get(CLAIM_ACTIVE_ROLE_ID, String.class);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.user_not_found")));

            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
            String[] resolved = resolveActiveWorkspaceClaims(wctx, activeBu, activeRoleId);
            activeBu = resolved[0];
            activeRoleId = resolved[1];
            if (!wctx.isEmpty()) {
                if (activeBu == null || activeRoleId == null
                        || !portalWorkspaceAuthService.hasContext(userId, activeBu, activeRoleId)) {
                    return ResponseEntity.status(401).build();
                }
            }

            String portalAccessMode = portalAccessModeForWorkspace(wctx);
            LoginBundle bundle = buildRolesAndPermissions(user, activeBu, activeRoleId);
            return ResponseEntity.ok(toUserLoginInfo(user, bundle, activeBu, activeRoleId, wctx.size() > 1, portalAccessMode));
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/workspace-contexts")
    public ResponseEntity<List<Map<String, String>>> listWorkspaceContexts(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String token = extractAccessToken(request, authHeader);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            List<Map<String, String>> out = portalWorkspaceAuthService.listWorkspaceContexts(userId).stream()
                    .map(r -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("businessUnitId", r.getBusinessUnitId());
                        m.put("roleId", r.getRoleId());
                        m.put("businessUnitName", r.getBusinessUnitName() != null ? r.getBusinessUnitName() : "");
                        m.put("roleCode", r.getRoleCode() != null ? r.getRoleCode() : "");
                        m.put("roleName", r.getRoleName() != null ? r.getRoleName() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/switch-workspace")
    public ResponseEntity<LoginResponse> switchWorkspace(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody SwitchWorkspaceRequest body,
            HttpServletRequest request,
            HttpServletResponse httpResponse) {
        String token = extractAccessToken(request, authHeader);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            if (!portalWorkspaceAuthService.hasContext(userId, body.getBusinessUnitId(), body.getRoleId())) {
                return ResponseEntity.status(403).build();
            }
            jwtTokenService.blacklistToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.user_not_found")));
            String activeBu = body.getBusinessUnitId();
            String activeRoleId = body.getRoleId();
            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
            String portalAccessMode = portalAccessModeForWorkspace(wctx);
            LoginBundle bundle = buildRolesAndPermissions(user, activeBu, activeRoleId);
            String accessToken = generateToken(user, bundle.roles, bundle.permissions, activeBu, activeRoleId, portalAccessMode);
            String refreshToken = generateRefreshToken(userId, activeBu, activeRoleId, portalAccessMode);

            // Set httpOnly cookies for new tokens (same pattern as /refresh and issuePortalSession)
            Cookie accessTokenCookie = new Cookie(jwtProperties.getPrimaryCookieName(), accessToken);
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(false);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge((int)(jwtExpiration / 1000));
            accessTokenCookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(accessTokenCookie);

            Cookie refreshTokenCookie = new Cookie(jwtProperties.getRefreshCookieName(), refreshToken);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60);
            refreshTokenCookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(refreshTokenCookie);

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtExpiration / 1000)
                    .user(toUserLoginInfo(user, bundle, activeBu, activeRoleId, wctx.size() > 1, portalAccessMode))
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(false);
        }
        try {
            parseToken(authHeader.substring(7));
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
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

    /**
     * JWT may be issued before UBR exists (no active BU/Role in claims). After admin adds UBR,
     * Auto-selects first entry from {@link PortalWorkspaceAuthService#listWorkspaceContexts} sort order,
     * Matches single-UBR login behavior; user can still switch workspace in header when multiple UBRs.
     */
    private static String[] resolveActiveWorkspaceClaims(
            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx,
            String activeBusinessUnitId,
            String activeRoleId) {
        if (wctx == null || wctx.isEmpty()) {
            return new String[] { activeBusinessUnitId, activeRoleId };
        }
        boolean claimsMissing = activeBusinessUnitId == null || activeBusinessUnitId.isBlank()
                || activeRoleId == null || activeRoleId.isBlank();
        if (claimsMissing) {
            PortalWorkspaceAuthService.WorkspaceContextRow row = wctx.get(0);
            return new String[] { row.getBusinessUnitId(), row.getRoleId() };
        }
        return new String[] { activeBusinessUnitId, activeRoleId };
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

    /**
     * Extract access token from Authorization header or httpOnly cookie.
     * The JwtAuthenticationFilter skips /auth/ paths, so we must read cookies
     * directly for authenticated auth endpoints (me, workspace-contexts, etc.).
     */
    private String extractAccessToken(HttpServletRequest request, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                List<String> names = jwtProperties.getCookieNames();
                if (names == null || names.isEmpty()) {
                    names = List.of("up_access_token");
                }
                for (String name : names) {
                    for (Cookie cookie : cookies) {
                        if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                            return cookie.getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

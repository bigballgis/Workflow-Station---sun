package com.portal.controller;

import com.platform.security.config.JwtProperties;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.LoginAudit;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import com.platform.common.i18n.I18nService;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.dto.ChangePasswordRequest;
import com.portal.dto.LoginRequest;
import com.portal.dto.LoginResponse;
import com.portal.dto.SwitchWorkspaceRequest;
import com.portal.repository.LoginAuditQueryRepository;
import com.portal.service.PortalSessionIssuerService;
import com.portal.service.PortalWorkspaceAuthService;
import com.platform.security.repository.UserRepository;
import com.platform.security.service.PortalEntryService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final UserRoleService userRoleService;
    private final I18nService i18nService;
    private final JwtTokenService jwtTokenService;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;
    private final PortalEntryService portalEntryService;
    private final PortalSessionIssuerService portalSessionIssuerService;
    private final LoginAuditQueryRepository loginAuditQueryRepository;
    private final JwtProperties jwtProperties;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        log.info("Login attempt for user: {} from {}", request.getUsername(), ipAddress);

        try {
            log.debug("Looking up user: {}", request.getUsername());
            User user = userRepository.findByUsername(request.getUsername())
                    .orElse(null);

            if (user == null) {
                log.warn("User not found: {}", request.getUsername());
                recordLoginAuditFailure(null, request.getUsername(), ipAddress, userAgent,
                        i18nService.getMessage("auth.invalid_credentials"));
                throw new RuntimeException(i18nService.getMessage("auth.invalid_credentials"));
            }

            log.debug("User found: {}, status: {}", user.getUsername(), user.getStatus());

            if (user.isLocked()) {
                log.warn("User {} is locked", user.getUsername());
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        i18nService.getMessage("auth.account_locked"));
                throw new RuntimeException(i18nService.getMessage("auth.account_locked"));
            }

            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                log.warn("User {} is inactive", user.getUsername());
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        i18nService.getMessage("auth.account_disabled"));
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
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        i18nService.getMessage("auth.invalid_credentials"));
                throw new RuntimeException(i18nService.getMessage("auth.invalid_credentials"));
            }

            log.debug("Password matched; clear failed-login counter (lastLogin only after session issued)");
            user.resetFailedLoginCount();
            userRepository.save(user);

            // Role grants happen in admin-center and cannot invalidate this portal-local cache,
            // so login is the refresh point: without this, a freshly granted role stays invisible
            // in New Requests until the 5-minute role-code cache expires.
            functionUnitAccessComponent.clearUserRolesCache(user.getId());

            ResponseEntity<LoginResponse> sessionResponse =
                    portalSessionIssuerService.issuePortalSession(user, request, httpRequest, httpResponse);
            LoginResponse body = sessionResponse.getBody();
            if (body != null && body.getAccessToken() != null) {
                user.setLastLoginAt(LocalDateTime.now());
                user.setLastLoginIp(ipAddress);
                userRepository.save(user);
                recordLoginAuditSuccess(user.getId(), user.getUsername(), ipAddress, userAgent);
            } else if (body != null
                    && PortalSessionIssuerService.LOGIN_ERROR_PORTAL_ENTITLEMENT_DENIED.equals(body.getLoginErrorCode())) {
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        body.getMessage() != null ? body.getMessage()
                                : i18nService.getMessage("auth.portal_entitlement_denied"));
            } else if (body == null
                    || !("WORKSPACE_CONTEXT_REQUIRED".equals(body.getLoginErrorCode()))) {
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        body != null && body.getMessage() != null
                                ? body.getMessage()
                                : i18nService.getMessage("auth.login_failed"));
            }
            return sessionResponse;
        } catch (RuntimeException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .message(e.getMessage() != null ? e.getMessage() : i18nService.getMessage("auth.login_failed"))
                    .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token != null) {
            jwtTokenService.blacklistToken(token);
        }
        return ResponseEntity.ok().build();
    }
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody(required = false) Map<String, String> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = request != null ? request.get("refreshToken") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = resolveRefreshToken(httpRequest);
        }
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
            if (!portalEntryService.hasPortalEntry(userId)) {
                log.warn("Portal refresh denied: user {} has no Portal entry", userId);
                return ResponseEntity.status(401).build();
            }
            String activeBu = claims.get(CLAIM_ACTIVE_BUSINESS_UNIT_ID, String.class);
            String activeRoleId = claims.get(CLAIM_ACTIVE_ROLE_ID, String.class);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (!isAccountUsable(user)) {
                log.warn("Portal refresh denied: user {} is not active (status={})", userId, user.getStatus());
                return ResponseEntity.status(401).build();
            }
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
            setAuthCookie(httpResponse, jwtProperties.getPrimaryCookieName(), newAccessToken, jwtProperties.getSessionCookieMaxAgeSeconds());
            setAuthCookie(httpResponse, jwtProperties.getRefreshCookieName(), newRefreshToken, jwtProperties.getSessionCookieMaxAgeSeconds());
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
            HttpServletRequest httpRequest,
            @Valid @RequestBody ChangePasswordRequest body) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parsePortalSessionToken(token);
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

    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String token = resolveAccessToken(authHeader, request);
        if (token == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parsePortalSessionToken(token);
            String userId = claims.getSubject();
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getAvatar() == null || user.getAvatar().length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.IMAGE_JPEG)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS).cachePrivate())
                    .body(user.getAvatar());
        } catch (Exception e) {
            log.debug("Avatar resolve failed: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserLoginInfo> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parsePortalSessionToken(token);
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
            HttpServletRequest httpRequest) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parsePortalSessionToken(token);
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
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @Valid @RequestBody SwitchWorkspaceRequest body) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = parsePortalSessionToken(token);
            String userId = claims.getSubject();
            if (!portalEntryService.hasPortalEntry(userId)) {
                return ResponseEntity.status(403).build();
            }
            if (!portalWorkspaceAuthService.hasContext(userId, body.getBusinessUnitId(), body.getRoleId())) {
                return ResponseEntity.status(403).build();
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.user_not_found")));
            if (!isAccountUsable(user)) {
                log.warn("Portal switch-workspace denied: user {} is not active (status={})", userId, user.getStatus());
                return ResponseEntity.status(401).build();
            }
            jwtTokenService.blacklistToken(token);
            // A role granted since sign-in must count at once in the workspace just switched to.
            functionUnitAccessComponent.clearUserRolesCache(userId);
            String activeBu = body.getBusinessUnitId();
            String activeRoleId = body.getRoleId();
            List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
            String portalAccessMode = portalAccessModeForWorkspace(wctx);
            LoginBundle bundle = buildRolesAndPermissions(user, activeBu, activeRoleId);
            String accessToken = generateToken(user, bundle.roles, bundle.permissions, activeBu, activeRoleId, portalAccessMode);
            String refreshToken = generateRefreshToken(userId, activeBu, activeRoleId, portalAccessMode);
            setAuthCookie(httpResponse, jwtProperties.getPrimaryCookieName(), accessToken, jwtProperties.getSessionCookieMaxAgeSeconds());
            setAuthCookie(httpResponse, jwtProperties.getRefreshCookieName(), refreshToken, jwtProperties.getSessionCookieMaxAgeSeconds());
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
    public ResponseEntity<Boolean> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String token = resolveAccessToken(authHeader, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(false);
        }
        try {
            parsePortalSessionToken(token);
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
                .issuer("platform")
                .id(java.util.UUID.randomUUID().toString())
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

    /** Same rule as login: a locked or non-ACTIVE account gets no new token. */
    private static boolean isAccountUsable(User user) {
        return user.isActive() && !user.isLocked();
    }

    private String generateRefreshToken(String userId, String activeBusinessUnitId, String activeRoleId, String portalAccessMode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpirationMs());

        var builder = Jwts.builder()
                .issuer("platform")
                .id(java.util.UUID.randomUUID().toString())
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
            List<String> permissions = new ArrayList<>(userRoleService.getPermissionsForUser(userId));
            return new LoginBundle(roles, permissions, buildRolesWithSources(effectiveAll), null, null);
        }

        PortalWorkspaceAuthService.WorkspaceContextRow row = wctx.stream()
                .filter(r -> activeBusinessUnitId != null && activeBusinessUnitId.equals(r.getBusinessUnitId())
                        && activeRoleId != null && activeRoleId.equals(r.getRoleId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("workspace context not found"));

        List<String> roles = new ArrayList<>();
        if (row.getRoleCode() != null && !row.getRoleCode().isBlank()) {
            roles.add(row.getRoleCode());
        }

        LinkedHashSet<String> perms = new LinkedHashSet<>(portalWorkspaceAuthService.permissionsForRoleId(activeRoleId));

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
                .hasAvatar(user.getAvatar() != null && user.getAvatar().length > 0)
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

    /**
     * Claims of an access token this Portal issued: signed and unexpired, not logged out, not a refresh token, and
     * carrying the Portal access mode. A Developer Workstation or Admin Center token never opens a Portal session
     * (platform access model §7.1).
     */
    private Claims parsePortalSessionToken(String token) {
        if (jwtTokenService.isBlacklisted(token)) {
            throw new JwtException("Token has been revoked");
        }
        Claims claims = parseToken(token);
        String accessMode = claims.get(CLAIM_PORTAL_ACCESS_MODE, String.class);
        if (REFRESH_TYPE.equals(claims.get("type", String.class)) || accessMode == null || accessMode.isBlank()) {
            throw new JwtException("Not a Portal session token");
        }
        return claims;
    }

    /**
     * Extract access token from Authorization header or httpOnly cookie.
     * The JwtAuthenticationFilter skips /auth/ paths, so we must read cookies
     * directly for authenticated auth endpoints (me, workspace-contexts, etc.).
     */
    private String resolveAccessToken(String authHeader, HttpServletRequest request) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        List<String> names = jwtProperties.getCookieNames();
        if (names == null || names.isEmpty()) {
            names = List.of("up_access_token");
        }
        return resolveCookie(request, names);
    }
    private String resolveRefreshToken(HttpServletRequest request) {
        return resolveCookie(request, List.of(jwtProperties.getRefreshCookieName()));
    }
    private String resolveCookie(HttpServletRequest request, List<String> names) {
        Cookie[] cookies = request != null ? request.getCookies() : null;
        if (cookies == null || names == null || names.isEmpty()) {
            return null;
        }
        for (String name : names) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    private void setAuthCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Record a successful login audit entry.
     */
    private void recordLoginAuditSuccess(String userId, String username, String ipAddress, String userAgent) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                    .loginPlatform(LoginAudit.LoginPlatform.USER_PORTAL)
                    .success(true)
                    .build();
            loginAuditQueryRepository.save(audit);
            log.debug("Recorded login success audit for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to record login success audit: {}", e.getMessage());
        }
    }
    
    private void recordLoginAuditFailure(String userId, String username, String ipAddress,
                                         String userAgent, String reason) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                .loginPlatform(LoginAudit.LoginPlatform.USER_PORTAL)
                    .success(false)
                    .failureReason(reason != null && reason.length() > 255
                            ? reason.substring(0, 255) : reason)
                    .build();
            loginAuditQueryRepository.save(audit);
            log.debug("Recorded login failure audit for user: {}, reason: {}", username, reason);
        } catch (Exception e) {
            log.error("Failed to record login failure audit: {}", e.getMessage());
        }
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}

package com.admin.bi.controller;

import com.admin.bi.service.BiRbacMappingService;
import com.admin.repository.UserRoleRepository;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Superset 网关鉴权端点（供边缘网关 auth_request 调用）。
 *
 * <p>作者/管理员打开 Superset 原生 UI（/superset/）时，网关用此端点完成统一 SSO：
 * <ol>
 *   <li>网关把原始请求的 Cookie 透传到本子请求，JwtAuthenticationFilter 校验平台 JWT 并填充
 *       SecurityContext（auth_request 子请求恒为 GET，故本端点用 GET）。</li>
 *   <li>本端点解析当前用户 + 其平台角色 → 经 {@code ac_bi_rbac_mappings} 映射成 Superset 角色名。</li>
 *   <li>返回 200 + 响应头 {@code X-Remote-User} / {@code X-Remote-Roles}，由网关注入给 Superset。</li>
 * </ol>
 *
 * <p>安全模型（不依赖内部 token）：Superset 仅经网关可达（裸端口已封）、网关剥离客户端伪造的
 * {@code X-Remote-*} 并只用本端点返回值注入。本端点仅反射「调用者自身」的身份，凭其自带的合法
 * JWT；无 JWT → 401；无 BI 角色映射 → 403（仅有映射的作者/管理员可进 Superset，避免账号泛滥）。
 *
 * <p>注意：返回的是裸响应头（非统一 {@code ApiResponse}）——auth_request 只看状态码与响应头，
 * 这是基础设施端点的特例。
 */
@RestController
@RequestMapping("/internal/bi/superset")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Superset Gateway Auth", description = "auth_request endpoint for Superset native-UI SSO")
public class BiSupersetAuthController {

    static final String HDR_USER = "X-Remote-User";
    static final String HDR_ROLES = "X-Remote-Roles";
    static final String HDR_EMAIL = "X-Remote-Email";
    // Display name is URL-encoded because it may contain non-ASCII (e.g. Chinese),
    // which is unsafe in raw HTTP headers. The Superset SM URL-decodes it.
    static final String HDR_FIRSTNAME = "X-Remote-Firstname";

    private final UserRoleRepository userRoleRepository;
    private final BiRbacMappingService rbacMappingService;
    private final com.admin.repository.UserRepository userRepository;

    // Two patterns: the exact path (dev nginx auth_request calls it directly) and
    // "/authorize/**" because Istio's envoyExtAuthzHttp appends the original request
    // path to the provider pathPrefix (e.g. /authorize/superset/welcome).
    @GetMapping({"/authorize", "/authorize/**"})
    @Operation(summary = "Authorize Superset UI access",
            description = "Validates the platform JWT and returns X-Remote-User / X-Remote-Roles "
                    + "for the edge gateway to inject into Superset. 401 if unauthenticated, "
                    + "403 if the user has no Superset role mapping.")
    public ResponseEntity<Void> authorize() {
        Optional<UserPrincipal> userOpt = SecurityContextUtils.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        UserPrincipal user = userOpt.get();
        String login = (user.getUsername() != null && !user.getUsername().isBlank())
                ? user.getUsername()
                : user.getUserId();

        List<String> sysRoleIds = userRoleRepository.findAllRoleIdsByUserId(user.getUserId());
        List<String> supersetRoles = rbacMappingService.getEffectiveSupersetRoleNames(sysRoleIds);

        if (supersetRoles.isEmpty()) {
            // 无 BI 角色映射 → 非 Superset 作者/管理员，拒绝（避免 Superset 账号泛滥）。
            log.debug("Superset authorize denied for {} (no mapped roles)", login);
            return ResponseEntity.status(403).build();
        }

        log.debug("Superset authorize ok for {} roles={}", login, supersetRoles);

        // The platform JWT does NOT carry email/displayName, so read them from the
        // user record (sys_users) by id; fall back to the login name.
        String email = null;
        String displayName = login;
        var dbUser = userRepository.findById(user.getUserId());
        if (dbUser.isPresent()) {
            if (dbUser.get().getEmail() != null && !dbUser.get().getEmail().isBlank()) {
                email = dbUser.get().getEmail();
            }
            if (dbUser.get().getDisplayName() != null && !dbUser.get().getDisplayName().isBlank()) {
                displayName = dbUser.get().getDisplayName();
            }
        }

        ResponseEntity.BodyBuilder resp = ResponseEntity.ok()
                .header(HDR_USER, login)
                .header(HDR_ROLES, String.join(",", supersetRoles))
                .header(HDR_FIRSTNAME, java.net.URLEncoder.encode(displayName, java.nio.charset.StandardCharsets.UTF_8));
        if (email != null) {
            resp.header(HDR_EMAIL, email);  // emails are ASCII -> safe raw
        }
        return resp.build();
    }
}

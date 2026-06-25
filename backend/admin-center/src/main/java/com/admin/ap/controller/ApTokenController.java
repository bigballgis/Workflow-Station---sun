package com.admin.ap.controller;

import com.admin.ap.client.ActivepiecesApiClient;
import com.admin.ap.config.ActivepiecesProperties;
import com.platform.common.dto.ApiResponse;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Activepieces 共享账号登录桥后端（供边缘网关 :8085 调用）。
 *
 * <p>方案：社区版 + 网关共享账号。流程：
 * <ol>
 *   <li>浏览器经 :8085 AP 网关，nginx {@code auth_request} 调本控制器 {@link #authz()} 校验平台 JWT
 *       （cookie 透传 → JwtAuthenticationFilter 填充 SecurityContext）。无 JWT → 401 → 网关跳平台登录。</li>
 *   <li>登录桥页 JS 取本控制器 {@link #token()}：服务端用共享账号调 AP sign-in 换取 AP token，返回给桥页
 *       写入 {@code localStorage['token']}，再跳 :8085/ 进入 AP（共享账号 ADMIN 身份）。</li>
 * </ol>
 *
 * <p>安全模型：AP token 由服务端用共享账号换取，浏览器从不接触共享账号口令；两个端点都要求合法平台 JWT
 * （未登录 401）。生产 runtime 不开 UI（{@code activepieces.bridge.enabled=false}）→ 两端点恒 404。
 */
@RestController
@RequestMapping("/internal/ap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activepieces Login Bridge", description = "Shared-account login bridge endpoints for the :8085 AP gateway")
public class ApTokenController {

    private final ActivepiecesProperties properties;
    private final ActivepiecesApiClient apiClient;

    /** Bridge HTML, loaded once from the classpath. */
    private volatile String bridgeHtml;

    /**
     * 网关 auth_request 端点：仅校验平台 JWT 是否合法（恒为 GET、无副作用）。
     * 200 = 已认证、可进 AP 网关；401 = 未认证、网关跳平台登录。桥关闭时 404。
     */
    @GetMapping({"/authz", "/authz/**"})
    @Operation(summary = "Authorize AP gateway access (auth_request)",
            description = "200 if the platform JWT is valid, 401 otherwise. 404 when the login bridge is disabled.")
    public ResponseEntity<Void> authz() {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        Optional<UserPrincipal> user = SecurityContextUtils.getCurrentUser();
        return user.isEmpty() ? ResponseEntity.status(401).build() : ResponseEntity.ok().build();
    }

    /**
     * 登录桥页：返回桥 HTML。由 AP 网关在 /__ap/bridge 暴露，故页内相对路径解析到网关 origin。
     * 要求合法平台 JWT（未认证 401 → 网关跳登录）；桥关闭时 404。dev 与非生产 k8s 共用这一份。
     */
    @GetMapping(value = "/bridge", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Serve the AP login bridge page",
            description = "Returns the client bridge HTML that mints an AP token and stores it in "
                    + "localStorage. 401 if unauthenticated, 404 when the bridge is disabled.")
    public ResponseEntity<String> bridge() {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (SecurityContextUtils.getCurrentUser().isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(loadBridgeHtml());
    }

    private String loadBridgeHtml() {
        String html = bridgeHtml;
        if (html == null) {
            try {
                html = StreamUtils.copyToString(
                        new ClassPathResource("ap/ap-bridge.html").getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load AP bridge HTML", e);
            }
            bridgeHtml = html;
        }
        return html;
    }

    /**
     * 登录桥 token 端点：用共享账号服务端 sign-in 换取 AP token。
     * 要求合法平台 JWT（未认证 401）；桥关闭时 404。
     */
    @GetMapping("/token")
    @Operation(summary = "Mint an AP token via the shared service account",
            description = "Validates the platform JWT, then server-side signs into AP with the shared "
                    + "account and returns the AP token for the bridge to store in localStorage.")
    public ResponseEntity<ApiResponse<Map<String, String>>> token() {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        Optional<UserPrincipal> userOpt = SecurityContextUtils.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        String login = userOpt.get().getUsername() != null ? userOpt.get().getUsername() : userOpt.get().getUserId();
        ActivepiecesApiClient.ApSession session = apiClient.signInShared();
        log.debug("Issued AP shared session for platform user {}", login);
        // A complete AP session is token + projectId (AP clears both on logout). Returning
        // only the token leaves AP without a current project -> it bounces /flows <-> /sign-in.
        Map<String, String> data = new HashMap<>();
        data.put("token", session.token());
        if (session.projectId() != null) {
            data.put("projectId", session.projectId());
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}

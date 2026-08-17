package com.admin.servicetask.controller;

import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.admin.servicetask.service.ServiceTaskBridgeNonceStore;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Activepieces 登录桥后端（per-user：谁进去就是谁）。
 *
 * <p>方案：社区版 + 网关登录桥，会话一律按当前操作人经 managed-authn 换取。两种进入路径：
 * <ol>
 *   <li><b>跨域握手（方案 B，推荐）</b>：admin 域调 {@link #launch()}（cookie 在自己域有效）→ 验平台 JWT、
 *       按该用户换 AP 会话、签发一次性 nonce → 返回 {@code bridgeUrl = <AP 桥页>#nonce=<票>}。浏览器整页跳到
 *       AP 域桥页，桥页凭 {@link #token(String) nonce} 换回 AP token 写 localStorage。<b>AP 域全程无需平台 cookie</b>，
 *       故 admin 与 AP 分属不同父域也能用。</li>
 *   <li><b>同源回退（dev）</b>：admin 与 AP 同源（:8085）时，桥页直接带 cookie 调 {@link #token(String)}（无 nonce），
 *       走 {@link SecurityContextUtils} 校验——与历史行为一致，不回归。</li>
 * </ol>
 *
 * <p>安全模型：换 AP token 必经"已认证的 admin 域 {@code /launch}"或"同源带 cookie 的 {@code /token}"；
 * nonce 不可猜、单次、短时效；AP token 从不进入 URL。外部 token 的签名私钥仅服务端持有。
 * 生产 runtime 不开 UI（{@code activepieces.bridge.enabled=false}）→ 端点恒 404。
 */
@RestController
@RequestMapping("/internal/ap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activepieces Login Bridge", description = "Per-user login bridge endpoints for the AP gateway")
public class ServiceTaskTokenController {

    private final ServiceTaskProperties properties;
    private final ServiceTaskApiClient apiClient;
    private final ServiceTaskBridgeNonceStore nonceStore;

    /** Bridge HTML, loaded once from the classpath. */
    private volatile String bridgeHtml;

    /**
     * 跨域握手入口（方案 B）。在 <b>admin 域</b>命中（经 Kong {@code /api/v1/admin} 路由），平台 JWT cookie
     * 在自己域上有效，故 {@link SecurityContextUtils#getCurrentUser()} 可校验当前用户。
     * 验过后按<b>该用户</b>换其专属 AP 会话、签发一次性 nonce，返回 {@code bridgeUrl}（桥页地址 + {@code #nonce=}）。
     * 前端拿到后 {@code window.location.assign(bridgeUrl)} 整页跳转进 AP 域。
     *
     * @return {@code {bridgeUrl}}；未认证 401；桥关闭 404；桥页公网地址未配置 502。
     */
    @GetMapping("/launch")
    @Operation(summary = "Mint a cross-domain AP bridge launch URL",
            description = "Validates the platform JWT (cookie on the admin origin), exchanges a per-user "
                    + "managed external token for an AP session, issues a one-time nonce, and returns the AP "
                    + "bridge URL carrying it. The AP domain then needs no platform cookie.")
    public ResponseEntity<ApiResponse<Map<String, String>>> launch() {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        Optional<UserPrincipal> userOpt = SecurityContextUtils.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        String publicUrl = properties.getBridge().getPublicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            log.error("AP bridge launch requested but activepieces.bridge.public-url is not configured");
            return ResponseEntity.status(502).build();
        }

        UserPrincipal user = userOpt.get();
        String login = user.getUsername() != null ? user.getUsername() : user.getUserId();
        // 谁进去就是谁：按当前平台用户换取其专属 AP token（AP 侧 externalId=平台 userId）。
        // 没有回退分支——共享账号已移除，未配置签名密钥时 signInManaged 直接 fail-loud。
        ServiceTaskApiClient.ApSession session = apiClient.signInManaged(user);
        String nonce = nonceStore.issue(session, properties.getBridge().getNonceTtlSeconds());
        log.debug("Issued AP bridge launch nonce for platform user {}", login);

        String bridgeUrl = publicUrl + "#nonce=" + URLEncoder.encode(nonce, StandardCharsets.UTF_8);
        Map<String, String> data = new HashMap<>();
        data.put("bridgeUrl", bridgeUrl);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 网关 auth_request 端点：仅校验平台 JWT 是否合法（恒为 GET、无副作用）。
     * 200 = 已认证；401 = 未认证。桥关闭时 404。
     * （方案 B 下 AP 网关不再需要它；保留以兼容 dev nginx 既有配置。）
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
     * 登录桥页：返回桥 HTML。由 AP 网关在 {@code /__ap/bridge} 暴露，故页内相对路径解析到网关 origin。
     *
     * <p>方案 B：本页<b>不再要求平台 JWT</b>（鉴权已在 admin 域 {@code /launch} 完成、由 nonce 承载）。
     * 桥页本身不含任何机密；无有效 nonce 时 {@code /token} 会拒绝。桥关闭时 404。dev 与非生产 k8s 共用这一份。
     */
    @GetMapping(value = "/bridge", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Serve the AP login bridge page",
            description = "Returns the client bridge HTML that exchanges a nonce (or, in same-origin dev, the "
                    + "platform cookie) for an AP token and stores it in localStorage. 404 when the bridge is disabled.")
    public ResponseEntity<String> bridge() {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
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
     * 登录桥 token 端点：返回 AP 会话（token + projectId）供桥页写入 localStorage。
     *
     * <p>两种鉴权：
     * <ul>
     *   <li><b>nonce（方案 B，跨域）</b>：带 {@code ?nonce=}，单次兑换 {@code /launch} 暂存的 AP 会话——
     *       <b>不依赖平台 cookie</b>，故在 AP 域可用。无效 / 过期 / 已用 → 401。</li>
     *   <li><b>cookie（dev 同源回退）</b>：无 {@code nonce} 时校验平台 JWT，现场按该用户换其专属 AP token。未认证 401。</li>
     * </ul>
     * 桥关闭时 404。
     */
    @GetMapping("/token")
    @Operation(summary = "Resolve an AP session via nonce (cross-domain) or platform cookie (same-origin dev)",
            description = "With ?nonce= consumes the one-time session minted by /launch (no platform cookie "
                    + "needed). Without nonce, validates the platform JWT and mints that user's own AP session.")
    public ResponseEntity<ApiResponse<Map<String, String>>> token(
            @RequestParam(value = "nonce", required = false) String nonce) {
        if (!properties.getBridge().isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        ServiceTaskApiClient.ApSession session;
        if (nonce != null && !nonce.isBlank()) {
            // 跨域路径：用一次性 nonce 兑换，AP 域无平台 cookie 也可。
            Optional<ServiceTaskApiClient.ApSession> resolved = nonceStore.consume(nonce);
            if (resolved.isEmpty()) {
                return ResponseEntity.status(401).build();
            }
            session = resolved.get();
        } else {
            // 同源：校验平台 JWT，现场按该用户换其专属 AP token（与 /launch 同一条路径，无回退）。
            // DW 内嵌 builder（lib-mode 挂载）走的正是这条路径。
            Optional<UserPrincipal> userOpt = SecurityContextUtils.getCurrentUser();
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).build();
            }
            UserPrincipal user = userOpt.get();
            String login = user.getUsername() != null ? user.getUsername() : user.getUserId();
            session = apiClient.signInManaged(user);
            log.debug("Issued AP session (same-origin) for platform user {}", login);
        }

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

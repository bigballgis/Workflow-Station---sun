package com.admin.ap.client;

import com.admin.ap.config.ActivepiecesProperties;
import com.admin.exception.ActivepiecesApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Activepieces REST API 客户端。
 *
 * <p>仅封装「共享账号服务端 sign-in」：用平台持有的共享服务账号（ADMIN）登录 AP，换取 AP token。
 * 浏览器永不接触共享账号口令——登录桥只拿到换来的 token 写入 localStorage。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivepiecesApiClient {

    private final RestTemplate restTemplate;
    private final ActivepiecesProperties properties;

    /**
     * 共享账号 sign-in 的结果。AP 的一个完整前端会话需要 token + projectId
     * （AP 的 clearSession 同时清这两个 key），故桥页必须两者都写入 localStorage。
     */
    public record ApSession(String token, String projectId) {}

    /**
     * 调用 AP {@code POST /api/v1/authentication/sign-in}，用共享账号换取 AP 会话。
     *
     * @return {@link ApSession}（token 写 localStorage['token']，projectId 写 localStorage['projectId']）
     * @throws ActivepiecesApiException 未配置共享账号、登录失败或超时
     */
    public ApSession signInShared() {
        String email = properties.getSharedAccount().getEmail();
        String password = properties.getSharedAccount().getPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ActivepiecesApiException("Activepieces shared account not configured");
        }

        String base = properties.getInternalUrl();
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/api/v1/authentication/sign-in";

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Activepieces shared sign-in: {}", url);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object token = response.getBody().get("token");
                Object projectId = response.getBody().get("projectId");
                if (token != null && !token.toString().isBlank()) {
                    log.debug("Activepieces shared sign-in successful (projectId={})", projectId);
                    return new ApSession(token.toString(), projectId != null ? projectId.toString() : null);
                }
            }
            throw new ActivepiecesApiException("Activepieces sign-in failed: no token in response");

        } catch (ResourceAccessException e) {
            log.error("Activepieces sign-in timeout or connection error: {}", e.getMessage(), e);
            throw new ActivepiecesApiException("Activepieces API timeout or connection error", e);
        } catch (RestClientException e) {
            log.error("Activepieces sign-in failed: {}", e.getMessage(), e);
            throw new ActivepiecesApiException("Activepieces API call failed: " + e.getMessage(), e);
        }
    }
}

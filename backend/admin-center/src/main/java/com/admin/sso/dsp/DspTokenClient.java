package com.admin.sso.dsp;

import com.admin.config.PlatformSsoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * DSP token 交换客户端：用 AMToken 调 translator 换取 E2E/JWT（issued_token）。
 *
 * <p>请求体结构遵循 DSP STS 规范：{@code input_token_state{token_type,tokenId} + output_token_state{token_type}}。
 * 从响应中递归提取 issued_token 的多种可能字段名。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DspTokenClient {

    private static final List<String> ISSUED_TOKEN_KEYS = List.of(
            "issued_token", "issuedToken", "jwt", "token", "access_token", "id_token");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformSsoProperties ssoProperties;

    /**
     * 用 AMToken 交换 E2E/JWT。
     *
     * @return issued_token 字符串
    * @throws IllegalArgumentException translator 拒绝 AMToken、调用失败或响应中无可用 token
     */
    public String translate(String amToken) {
        PlatformSsoProperties.Dsp dsp = ssoProperties.getDsp();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Client-Id", dsp.getClientId());
        headers.set("X-Client-Secret", dsp.getClientSecret());
        headers.set("Accept-API-Version", dsp.getAcceptApiVersion());
        headers.set("X-Requested-With", "XMLHttpRequest");
        HttpEntity<String> entity = new HttpEntity<>(buildBody(amToken, dsp), headers);
        try {
            String resp = restTemplate.postForObject(dsp.getTranslatorUrl(), entity, String.class);
            return extractIssuedToken(resp)
                    .orElseThrow(() -> new IllegalArgumentException("DSP translator returned no issued_token"));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DSP token translation failed: {}", e.getMessage());
            throw new IllegalArgumentException("DSP token translation failed", e);
        }
    }

    private String buildBody(String amToken, PlatformSsoProperties.Dsp dsp) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode input = root.putObject("input_token_state");
            input.put("token_type", dsp.getInputTokenType());
            input.put("tokenId", amToken);
            ObjectNode output = root.putObject("output_token_state");
            output.put("token_type", dsp.getOutputTokenType());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build DSP translator body", e);
        }
    }

    private Optional<String> extractIssuedToken(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return findFirstKey(root);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 递归查找首个命中 ISSUED_TOKEN_KEYS 的字符串值。 */
    private Optional<String> findFirstKey(JsonNode node) {
        if (node == null) {
            return Optional.empty();
        }
        for (String key : ISSUED_TOKEN_KEYS) {
            JsonNode v = node.get(key);
            if (v != null && v.isValueNode() && !v.asText().isBlank()) {
                return Optional.of(v.asText());
            }
        }
        for (JsonNode child : node) {
            Optional<String> found = findFirstKey(child);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}

package com.admin.sso.dsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * DSP 返回的 E2E/JWT 解析器。
 *
 * <p><b>安全说明（整改点）：</b>当前仅 Base64URL 解 JWT 第二段（payload），<b>不做签名验证</b>。
 * 这是从源项目沿用的现状；生产环境必须接入 {@code platform.sso.dsp.manifest-locations} 公钥做验签，
 * 否则伪造 JWT 即可冒充身份。此实现已隔离在独立类，便于后续替换为验签实现。</p>
 */
@Component
@RequiredArgsConstructor
public class DspJwtDecoder {

    private final ObjectMapper objectMapper;

    /**
     * 解析 JWT payload 为 JSON 树。
     *
     * @return payload 节点；格式非法返回 empty
     */
    public Optional<JsonNode> decodePayload(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return Optional.empty();
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return Optional.of(objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 按候选键顺序取第一个非空字符串 claim（支持点号路径，如 {@code user.employeeId}）。
     */
    public Optional<String> firstClaim(JsonNode payload, List<String> candidateKeys) {
        if (payload == null || candidateKeys == null) {
            return Optional.empty();
        }
        for (String key : candidateKeys) {
            String value = readPath(payload, key);
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private String readPath(JsonNode payload, String key) {
        JsonNode node = payload;
        for (String segment : key.split("\\.")) {
            if (node == null) {
                return null;
            }
            node = node.get(segment);
        }
        return node != null && node.isValueNode() ? node.asText() : null;
    }

    private String padBase64(String value) {
        int pad = value.length() % 4;
        return pad == 0 ? value : value + "====".substring(pad);
    }
}

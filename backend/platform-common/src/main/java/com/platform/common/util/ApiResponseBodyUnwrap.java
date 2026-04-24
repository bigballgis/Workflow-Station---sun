package com.platform.common.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 将 RestTemplate 反序列化得到的「外层 JSON 对象」解包为业务负载。
 * 适用于 admin-center、workflow-engine、developer-workstation 等返回
 * {@code { "success": true, "data": ... }} 风格（与 {@link com.platform.common.dto.ApiResponse} 一致）的接口。
 */
public final class ApiResponseBodyUnwrap {

    private ApiResponseBodyUnwrap() {
    }

    /**
     * 若 {@code body} 为 ApiResponse 且 {@code data} 是对象，返回 {@code data}；否则返回 {@code body} 本身（兼容未包装的 DTO）。
     * 兼容两种 ApiResponse 格式：{@code success: true}（platform-common）和 {@code code: "SUCCESS"}（workflow-engine）。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> unwrapDataMap(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Collections.emptyMap();
        }
        boolean isSuccess = Boolean.TRUE.equals(body.get("success"))
                || "SUCCESS".equals(body.get("code"));
        if (isSuccess && body.get("data") instanceof Map<?, ?>) {
            return (Map<String, Object>) body.get("data");
        }
        return body;
    }

    /**
     * 从可能为 ApiResponse、PageResult、Spring Page 或裸列表外层的 Map 中解析出 {@code List<Map<String, Object>>}。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> normalizeToListOfMaps(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        boolean isSuccess = Boolean.TRUE.equals(map.get("success"))
                || "SUCCESS".equals(map.get("code"));
        if (isSuccess) {
            Object data = map.get("data");
            if (data instanceof List<?>) {
                return (List<Map<String, Object>>) data;
            }
            if (data instanceof Map<?, ?>) {
                return normalizeToListOfMaps((Map<String, Object>) data);
            }
            return Collections.emptyList();
        }
        if (map.get("content") instanceof List<?>) {
            return (List<Map<String, Object>>) map.get("content");
        }
        return Collections.emptyList();
    }
}

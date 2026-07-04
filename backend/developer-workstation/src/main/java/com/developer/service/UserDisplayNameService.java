package com.developer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户显示名称解析服务
 * 通过 REST 调用 admin-center 获取用户信息，带有界 LRU 缓存
 */
@Slf4j
@Service
public class UserDisplayNameService {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 500;
                }
            });

    public UserDisplayNameService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String resolve(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        String cached = cache.get(userId);
        if (cached != null) {
            return cached;
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId);
            Map<String, Object> raw = restTemplate.getForObject(url, Map.class);
            Map<String, Object> userInfo = raw != null ? ApiResponseBodyUnwrap.unwrapDataMap(raw) : null;
            if (userInfo != null && !userInfo.isEmpty()) {
                String displayName = extractDisplayName(userInfo);
                if (displayName != null) {
                    cache.put(userId, displayName);
                    return displayName;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        cache.put(userId, userId);
        return userId;
    }

    private String extractDisplayName(Map<String, Object> userInfo) {
        String fullName = (String) userInfo.get("fullName");
        if (fullName != null && !fullName.isEmpty()) return fullName;
        String displayName = (String) userInfo.get("displayName");
        if (displayName != null && !displayName.isEmpty()) return displayName;
        String username = (String) userInfo.get("username");
        if (username != null && !username.isEmpty()) return username;
        return null;
    }
}

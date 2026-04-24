package com.admin.service.impl;

import com.admin.dto.response.N8nWorkflowDTO;
import com.admin.entity.N8nConfig;
import com.admin.service.N8nConfigService;
import com.admin.service.N8nWorkflowProxyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * N8N 工作流列表代理服务实现
 * 需求: 2.1, 2.2, 2.3, 2.4, 2.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class N8nWorkflowProxyServiceImpl implements N8nWorkflowProxyService {

    private final N8nConfigService n8nConfigService;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "n8n:workflows:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Override
    public List<N8nWorkflowDTO> listWorkflows(String configId) {
        // 1. Try cache first
        String cacheKey = CACHE_KEY_PREFIX + configId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for N8N workflows, configId: {}", configId);
                return objectMapper.readValue(cached, new TypeReference<List<N8nWorkflowDTO>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to read N8N workflow cache for configId: {}, proceeding with API call", configId, e);
        }

        // 2. Get N8N config (with decrypted apiKey)
        N8nConfig config = n8nConfigService.getByIdInternal(configId);
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/api/v1/workflows";

        try {
            // 3. Call N8N API
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-N8N-API-KEY", config.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                String msg = "N8N API 返回非成功状态: " + response.getStatusCode();
                log.error("Failed to fetch N8N workflows for configId {}: {}", configId, msg);
                throw new RuntimeException(msg);
            }

            // 4. Parse and filter active workflows
            List<N8nWorkflowDTO> workflows = parseAndFilterWorkflows(response.getBody());

            // 5. Cache the result
            try {
                String json = objectMapper.writeValueAsString(workflows);
                stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Failed to cache N8N workflows for configId: {}", configId, e);
            }

            return workflows;

        } catch (RuntimeException e) {
            log.error("Failed to fetch N8N workflows for configId {}: {}", configId, e.getMessage());
            throw new RuntimeException("Failed to fetch N8N workflow list: " + e.getMessage(), e);
        }
    }

    /**
     * Parse N8N API response and filter only active workflows.
     * This method is package-private for testing.
     */
    List<N8nWorkflowDTO> parseAndFilterWorkflows(Map<String, Object> responseBody) {
        Object data = responseBody.get("data");
        if (!(data instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workflowList = (List<Map<String, Object>>) data;

        return workflowList.stream()
                .filter(wf -> Boolean.TRUE.equals(wf.get("active")))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private N8nWorkflowDTO mapToDto(Map<String, Object> wf) {
        List<String> tags = Collections.emptyList();
        Object tagsObj = wf.get("tags");
        if (tagsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tagList = (List<Map<String, Object>>) tagsObj;
            tags = tagList.stream()
                    .map(tag -> String.valueOf(tag.getOrDefault("name", "")))
                    .collect(Collectors.toList());
        }

        return N8nWorkflowDTO.builder()
                .id(String.valueOf(wf.get("id")))
                .name(String.valueOf(wf.getOrDefault("name", "")))
                .active(Boolean.TRUE.equals(wf.get("active")))
                .tags(tags)
                .createdAt(wf.get("createdAt") != null ? String.valueOf(wf.get("createdAt")) : null)
                .build();
    }

    /**
     * Static utility method for filtering active workflows from a list.
     * Used by property tests to verify the filtering logic independently.
     */
    public static List<N8nWorkflowDTO> filterActiveWorkflows(List<N8nWorkflowDTO> workflows) {
        if (workflows == null) {
            return Collections.emptyList();
        }
        return workflows.stream()
                .filter(wf -> Boolean.TRUE.equals(wf.getActive()))
                .collect(Collectors.toList());
    }
}

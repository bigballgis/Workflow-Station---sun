package com.admin.service.impl;

import com.admin.dto.request.N8nConfigCreateRequest;
import com.admin.dto.request.N8nConfigUpdateRequest;
import com.admin.dto.response.N8nConnectionTestResult;
import com.admin.entity.N8nConfig;
import com.admin.repository.N8nConfigRepository;
import com.admin.service.N8nConfigService;
import com.platform.security.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * N8N 连接配置管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class N8nConfigServiceImpl implements N8nConfigService {

    private final N8nConfigRepository n8nConfigRepository;
    private final EncryptionService encryptionService;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public N8nConfig create(N8nConfigCreateRequest dto) {
        log.info("Creating N8N config: {}", dto.getName());

        N8nConfig config = N8nConfig.builder()
                .id(UUID.randomUUID().toString())
                .name(dto.getName())
                .baseUrl(dto.getBaseUrl())
                .apiKey(encryptionService.encrypt(dto.getApiKey()))
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return n8nConfigRepository.save(config);
    }

    @Override
    @Transactional
    public N8nConfig update(String id, N8nConfigUpdateRequest dto) {
        log.info("Updating N8N config: {}", id);

        N8nConfig config = n8nConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("N8N 配置不存在: " + id));

        if (dto.getName() != null) {
            config.setName(dto.getName());
        }
        if (dto.getBaseUrl() != null) {
            config.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            config.setApiKey(encryptionService.encrypt(dto.getApiKey()));
        }
        if (dto.getIsActive() != null) {
            config.setIsActive(dto.getIsActive());
        }

        return n8nConfigRepository.save(config);
    }

    @Override
    @Transactional
    public void delete(String id) {
        log.info("Deleting N8N config: {}", id);

        if (!n8nConfigRepository.existsById(id)) {
            throw new RuntimeException("N8N configuration not found: " + id);
        }
        n8nConfigRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public N8nConfig getById(String id) {
        N8nConfig config = n8nConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("N8N 配置不存在: " + id));

        return maskApiKey(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<N8nConfig> list() {
        return n8nConfigRepository.findAll().stream()
                .map(this::maskApiKey)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public N8nConfig getByIdInternal(String id) {
        N8nConfig config = n8nConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("N8N 配置不存在: " + id));

        // 解密 apiKey 返回
        config.setApiKey(encryptionService.decrypt(config.getApiKey()));
        return config;
    }

    @Override
    @Transactional(readOnly = true)
    public N8nConnectionTestResult testConnection(String id) {
        log.info("Testing N8N connection for config: {}", id);

        N8nConfig config = n8nConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("N8N 配置不存在: " + id));

        String decryptedApiKey = encryptionService.decrypt(config.getApiKey());
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/api/v1/workflows";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-N8N-API-KEY", decryptedApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map body = response.getBody();
                Integer count = null;
                if (body != null && body.containsKey("data")) {
                    Object data = body.get("data");
                    if (data instanceof List) {
                        count = ((List<?>) data).size();
                    }
                }
                return N8nConnectionTestResult.builder()
                        .success(true)
                        .message("连接成功")
                        .workflowCount(count)
                        .build();
            }

            return N8nConnectionTestResult.builder()
                    .success(false)
                    .message("连接失败: HTTP " + response.getStatusCode().value())
                    .build();

        } catch (Exception e) {
            log.error("N8N connection test failed for config {}: {}", id, e.getMessage());
            return N8nConnectionTestResult.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 对 apiKey 进行脱敏处理
     * 显示 "****" + 最后4位字符
     */
    private N8nConfig maskApiKey(N8nConfig config) {
        String encryptedKey = config.getApiKey();
        if (encryptedKey != null && !encryptedKey.isEmpty()) {
            // 先解密获取原始 apiKey，再脱敏
            try {
                String decrypted = encryptionService.decrypt(encryptedKey);
                if (decrypted.length() > 4) {
                    config.setApiKey("****" + decrypted.substring(decrypted.length() - 4));
                } else {
                    config.setApiKey("****");
                }
            } catch (Exception e) {
                log.warn("Failed to decrypt apiKey for masking, config id: {}", config.getId());
                config.setApiKey("****");
            }
        }
        return config;
    }
}

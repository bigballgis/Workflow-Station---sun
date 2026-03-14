package com.admin.service;

import com.platform.security.encryption.EncryptionService;
import com.platform.security.encryption.impl.AesEncryptionService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N8nConfigService 属性测试
 * Feature: n8n-workflow-integration, Property 1: API 密钥加密往返一致性
 * 验证需求: 1.3
 *
 * Validates: Requirements 1.3
 */
class N8nConfigServicePropertyTest {

    private EncryptionService encryptionService;

    @BeforeTry
    void setUp() {
        AesEncryptionService aesEncryptionService = new AesEncryptionService();
        ReflectionTestUtils.setField(aesEncryptionService, "encryptionKey", "test-256-bit-key-for-property!!");
        aesEncryptionService.init();
        this.encryptionService = aesEncryptionService;
    }

    @Provide
    Arbitrary<String> apiKeys() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(200)
                .filter(s -> !s.isEmpty());
    }

    /**
     * Feature: n8n-workflow-integration, Property 1: API 密钥加密往返一致性
     *
     * 对于任意有效的 API 密钥字符串：
     * 1. 加密后的值不等于原始明文
     * 2. 解密后的值等于原始明文
     *
     * Validates: Requirements 1.3
     */
    @Property(tries = 100)
    void apiKeyEncryptDecryptRoundTrip(@ForAll("apiKeys") String originalApiKey) {
        // Encrypt the API key
        String encrypted = encryptionService.encrypt(originalApiKey);

        // Encrypted value must NOT equal the original plaintext
        assertThat(encrypted).isNotEqualTo(originalApiKey);

        // Encrypted value should have the ENC: prefix
        assertThat(encrypted).startsWith("ENC:");

        // Decrypt the encrypted value
        String decrypted = encryptionService.decrypt(encrypted);

        // Decrypted value must equal the original plaintext
        assertThat(decrypted).isEqualTo(originalApiKey);
    }
}

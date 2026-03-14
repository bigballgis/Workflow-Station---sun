package com.admin.service;

import com.admin.dto.request.N8nConfigCreateRequest;
import com.admin.dto.request.N8nConfigUpdateRequest;
import com.admin.dto.response.N8nConnectionTestResult;
import com.admin.entity.N8nConfig;
import com.admin.repository.N8nConfigRepository;
import com.admin.service.impl.N8nConfigServiceImpl;
import com.platform.security.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8nConfigServiceImpl 单元测试
 * 测试 CRUD 操作、连接测试成功/失败场景、API 密钥加密存储验证
 * 需求: 1.1, 1.3, 1.4, 1.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nConfigServiceImpl Tests")
class N8nConfigServiceImplTest {

    @Mock
    private N8nConfigRepository n8nConfigRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private N8nConfigServiceImpl n8nConfigService;

    private static final String CONFIG_ID = "test-config-id";
    private static final String CONFIG_NAME = "Test N8N Config";
    private static final String BASE_URL = "https://n8n.example.com";
    private static final String RAW_API_KEY = "n8n_api_key_12345678";
    private static final String ENCRYPTED_API_KEY = "ENC:encrypted_value_abc";
    private static final String DECRYPTED_API_KEY = RAW_API_KEY;

    private N8nConfig buildSavedConfig() {
        return N8nConfig.builder()
                .id(CONFIG_ID)
                .name(CONFIG_NAME)
                .baseUrl(BASE_URL)
                .apiKey(ENCRYPTED_API_KEY)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should encrypt apiKey before saving")
        void shouldEncryptApiKeyBeforeSaving() {
            N8nConfigCreateRequest request = N8nConfigCreateRequest.builder()
                    .name(CONFIG_NAME)
                    .baseUrl(BASE_URL)
                    .apiKey(RAW_API_KEY)
                    .isActive(true)
                    .build();

            when(encryptionService.encrypt(RAW_API_KEY)).thenReturn(ENCRYPTED_API_KEY);
            when(n8nConfigRepository.save(any(N8nConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            N8nConfig result = n8nConfigService.create(request);

            ArgumentCaptor<N8nConfig> captor = ArgumentCaptor.forClass(N8nConfig.class);
            verify(n8nConfigRepository).save(captor.capture());
            N8nConfig saved = captor.getValue();

            assertThat(saved.getApiKey()).isEqualTo(ENCRYPTED_API_KEY);
            assertThat(saved.getName()).isEqualTo(CONFIG_NAME);
            assertThat(saved.getBaseUrl()).isEqualTo(BASE_URL);
            assertThat(saved.getIsActive()).isTrue();
            assertThat(saved.getId()).isNotNull();
            verify(encryptionService).encrypt(RAW_API_KEY);
        }

        @Test
        @DisplayName("Should default isActive to true when null")
        void shouldDefaultIsActiveToTrue() {
            N8nConfigCreateRequest request = N8nConfigCreateRequest.builder()
                    .name(CONFIG_NAME)
                    .baseUrl(BASE_URL)
                    .apiKey(RAW_API_KEY)
                    .isActive(null)
                    .build();

            when(encryptionService.encrypt(RAW_API_KEY)).thenReturn(ENCRYPTED_API_KEY);
            when(n8nConfigRepository.save(any(N8nConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            N8nConfig result = n8nConfigService.create(request);

            assertThat(result.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should encrypt apiKey when provided in update")
        void shouldEncryptApiKeyWhenProvided() {
            N8nConfig existing = buildSavedConfig();
            String newApiKey = "new_api_key_999";
            String newEncryptedKey = "ENC:new_encrypted_value";

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(existing));
            when(encryptionService.encrypt(newApiKey)).thenReturn(newEncryptedKey);
            when(n8nConfigRepository.save(any(N8nConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            N8nConfigUpdateRequest request = N8nConfigUpdateRequest.builder()
                    .apiKey(newApiKey)
                    .build();

            N8nConfig result = n8nConfigService.update(CONFIG_ID, request);

            assertThat(result.getApiKey()).isEqualTo(newEncryptedKey);
            verify(encryptionService).encrypt(newApiKey);
        }

        @Test
        @DisplayName("Should not change apiKey when null in update")
        void shouldNotChangeApiKeyWhenNull() {
            N8nConfig existing = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(existing));
            when(n8nConfigRepository.save(any(N8nConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            N8nConfigUpdateRequest request = N8nConfigUpdateRequest.builder()
                    .name("Updated Name")
                    .apiKey(null)
                    .build();

            N8nConfig result = n8nConfigService.update(CONFIG_ID, request);

            assertThat(result.getApiKey()).isEqualTo(ENCRYPTED_API_KEY);
            assertThat(result.getName()).isEqualTo("Updated Name");
            verify(encryptionService, never()).encrypt(anyString());
        }

        @Test
        @DisplayName("Should not change apiKey when blank in update")
        void shouldNotChangeApiKeyWhenBlank() {
            N8nConfig existing = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(existing));
            when(n8nConfigRepository.save(any(N8nConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            N8nConfigUpdateRequest request = N8nConfigUpdateRequest.builder()
                    .apiKey("   ")
                    .build();

            N8nConfig result = n8nConfigService.update(CONFIG_ID, request);

            assertThat(result.getApiKey()).isEqualTo(ENCRYPTED_API_KEY);
            verify(encryptionService, never()).encrypt(anyString());
        }

        @Test
        @DisplayName("Should throw exception when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(n8nConfigRepository.findById("nonexistent")).thenReturn(Optional.empty());

            N8nConfigUpdateRequest request = N8nConfigUpdateRequest.builder().name("x").build();

            assertThatThrownBy(() -> n8nConfigService.update("nonexistent", request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }
    }

    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete config when exists")
        void shouldDeleteWhenExists() {
            when(n8nConfigRepository.existsById(CONFIG_ID)).thenReturn(true);

            n8nConfigService.delete(CONFIG_ID);

            verify(n8nConfigRepository).deleteById(CONFIG_ID);
        }

        @Test
        @DisplayName("Should throw exception when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(n8nConfigRepository.existsById("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> n8nConfigService.delete("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }
    }

    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should return config with masked apiKey")
        void shouldReturnMaskedApiKey() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);

            N8nConfig result = n8nConfigService.getById(CONFIG_ID);

            // apiKey should be masked: "****" + last 4 chars
            assertThat(result.getApiKey()).startsWith("****");
            assertThat(result.getApiKey()).endsWith(DECRYPTED_API_KEY.substring(DECRYPTED_API_KEY.length() - 4));
            assertThat(result.getApiKey()).isNotEqualTo(DECRYPTED_API_KEY);
            assertThat(result.getName()).isEqualTo(CONFIG_NAME);
        }

        @Test
        @DisplayName("Should mask short apiKey with only ****")
        void shouldMaskShortApiKey() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn("abc");

            N8nConfig result = n8nConfigService.getById(CONFIG_ID);

            // Short key (<=4 chars) should be masked as "****"
            assertThat(result.getApiKey()).isEqualTo("****");
        }

        @Test
        @DisplayName("Should throw exception when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(n8nConfigRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> n8nConfigService.getById("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }
    }

    @Nested
    @DisplayName("list() Tests")
    class ListTests {

        @Test
        @DisplayName("Should return all configs with masked apiKeys")
        void shouldReturnAllWithMaskedApiKeys() {
            N8nConfig config1 = N8nConfig.builder()
                    .id("id-1").name("Config 1").baseUrl("https://n8n1.example.com")
                    .apiKey("ENC:enc1").isActive(true).build();
            N8nConfig config2 = N8nConfig.builder()
                    .id("id-2").name("Config 2").baseUrl("https://n8n2.example.com")
                    .apiKey("ENC:enc2").isActive(false).build();

            when(n8nConfigRepository.findAll()).thenReturn(Arrays.asList(config1, config2));
            when(encryptionService.decrypt("ENC:enc1")).thenReturn("apikey_one_12345");
            when(encryptionService.decrypt("ENC:enc2")).thenReturn("apikey_two_67890");

            List<N8nConfig> results = n8nConfigService.list();

            assertThat(results).hasSize(2);
            for (N8nConfig r : results) {
                assertThat(r.getApiKey()).startsWith("****");
                assertThat(r.getApiKey()).doesNotContain("ENC:");
            }
        }

        @Test
        @DisplayName("Should return empty list when no configs")
        void shouldReturnEmptyList() {
            when(n8nConfigRepository.findAll()).thenReturn(Collections.emptyList());

            List<N8nConfig> results = n8nConfigService.list();

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByIdInternal() Tests")
    class GetByIdInternalTests {

        @Test
        @DisplayName("Should return config with decrypted apiKey")
        void shouldReturnDecryptedApiKey() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);

            N8nConfig result = n8nConfigService.getByIdInternal(CONFIG_ID);

            assertThat(result.getApiKey()).isEqualTo(DECRYPTED_API_KEY);
            assertThat(result.getName()).isEqualTo(CONFIG_NAME);
            verify(encryptionService).decrypt(ENCRYPTED_API_KEY);
        }

        @Test
        @DisplayName("Should throw exception when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(n8nConfigRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> n8nConfigService.getByIdInternal("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }
    }

    @Nested
    @DisplayName("testConnection() Tests")
    class TestConnectionTests {

        @Test
        @DisplayName("Should return success when N8N API returns 200")
        void shouldReturnSuccessWhenApiReturns200() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);

            Map<String, Object> responseBody = new HashMap<>();
            List<Map<String, Object>> workflows = Arrays.asList(
                    Map.of("id", "wf1", "name", "Workflow 1"),
                    Map.of("id", "wf2", "name", "Workflow 2")
            );
            responseBody.put("data", workflows);

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.exchange(
                    eq(BASE_URL + "/api/v1/workflows"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenReturn(responseEntity);

            N8nConnectionTestResult result = n8nConfigService.testConnection(CONFIG_ID);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("连接成功");
            assertThat(result.getWorkflowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should strip trailing slashes from baseUrl")
        void shouldStripTrailingSlashes() {
            N8nConfig config = buildSavedConfig();
            config.setBaseUrl("https://n8n.example.com///");

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("data", List.of()), HttpStatus.OK);
            when(restTemplate.exchange(
                    eq("https://n8n.example.com/api/v1/workflows"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenReturn(responseEntity);

            N8nConnectionTestResult result = n8nConfigService.testConnection(CONFIG_ID);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return failure when RestTemplate throws exception")
        void shouldReturnFailureOnException() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenThrow(new RestClientException("Connection refused"));

            N8nConnectionTestResult result = n8nConfigService.testConnection(CONFIG_ID);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("连接失败");
            assertThat(result.getMessage()).contains("Connection refused");
        }

        @Test
        @DisplayName("Should throw exception when config not found for testConnection")
        void shouldThrowWhenConfigNotFound() {
            when(n8nConfigRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> n8nConfigService.testConnection("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }

        @Test
        @DisplayName("Should return success with null workflowCount when response body has no data")
        void shouldReturnSuccessWithNullCountWhenNoData() {
            N8nConfig config = buildSavedConfig();

            when(n8nConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
            when(encryptionService.decrypt(ENCRYPTED_API_KEY)).thenReturn(DECRYPTED_API_KEY);

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of(), HttpStatus.OK);
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenReturn(responseEntity);

            N8nConnectionTestResult result = n8nConfigService.testConnection(CONFIG_ID);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getWorkflowCount()).isNull();
        }
    }
}

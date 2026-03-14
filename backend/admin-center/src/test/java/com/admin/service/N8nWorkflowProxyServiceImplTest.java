package com.admin.service;

import com.admin.dto.response.N8nWorkflowDTO;
import com.admin.entity.N8nConfig;
import com.admin.service.impl.N8nWorkflowProxyServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8nWorkflowProxyServiceImpl 单元测试
 * 测试工作流列表获取、缓存命中/失效、API 调用失败场景
 * 需求: 2.1, 2.3, 2.4, 2.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nWorkflowProxyServiceImpl Tests")
class N8nWorkflowProxyServiceImplTest {

    @Mock
    private N8nConfigService n8nConfigService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;

    private N8nWorkflowProxyServiceImpl proxyService;

    private static final String CONFIG_ID = "test-config-id";
    private static final String BASE_URL = "https://n8n.example.com";
    private static final String DECRYPTED_API_KEY = "n8n_api_key_12345678";
    private static final String CACHE_KEY = "n8n:workflows:" + CONFIG_ID;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        proxyService = new N8nWorkflowProxyServiceImpl(
                n8nConfigService, restTemplate, stringRedisTemplate, objectMapper);
    }

    private N8nConfig buildConfig() {
        return N8nConfig.builder()
                .id(CONFIG_ID)
                .name("Test Config")
                .baseUrl(BASE_URL)
                .apiKey(DECRYPTED_API_KEY)
                .isActive(true)
                .build();
    }

    private Map<String, Object> buildN8nApiResponse(List<Map<String, Object>> workflows) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", workflows);
        return response;
    }

    private Map<String, Object> buildWorkflow(String id, String name, boolean active) {
        Map<String, Object> wf = new HashMap<>();
        wf.put("id", id);
        wf.put("name", name);
        wf.put("active", active);
        wf.put("tags", List.of(Map.of("name", "tag1")));
        wf.put("createdAt", "2024-01-01T00:00:00.000Z");
        return wf;
    }

    @Nested
    @DisplayName("listWorkflows() - Cache Hit Tests")
    class CacheHitTests {

        @Test
        @DisplayName("Should return cached workflows when cache hit")
        void shouldReturnCachedWorkflows() throws Exception {
            List<N8nWorkflowDTO> cachedWorkflows = List.of(
                    N8nWorkflowDTO.builder().id("wf1").name("Workflow 1").active(true).tags(List.of("tag1")).createdAt("2024-01-01").build()
            );
            String cachedJson = objectMapper.writeValueAsString(cachedWorkflows);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(cachedJson);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("wf1");
            assertThat(result.get(0).getName()).isEqualTo("Workflow 1");
            assertThat(result.get(0).getActive()).isTrue();

            // Should NOT call N8N API or config service
            verify(n8nConfigService, never()).getByIdInternal(anyString());
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("listWorkflows() - Cache Miss Tests")
    class CacheMissTests {

        @Test
        @DisplayName("Should fetch from N8N API and cache when cache miss")
        void shouldFetchFromApiAndCacheOnMiss() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            List<Map<String, Object>> workflows = List.of(
                    buildWorkflow("wf1", "Active WF", true),
                    buildWorkflow("wf2", "Inactive WF", false),
                    buildWorkflow("wf3", "Another Active", true)
            );
            ResponseEntity<Map> responseEntity = new ResponseEntity<>(buildN8nApiResponse(workflows), HttpStatus.OK);
            when(restTemplate.exchange(
                    eq(BASE_URL + "/api/v1/workflows"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            // Should only return active workflows
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(wf -> Boolean.TRUE.equals(wf.getActive()));
            assertThat(result.get(0).getId()).isEqualTo("wf1");
            assertThat(result.get(1).getId()).isEqualTo("wf3");

            // Should cache the result
            verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("Should filter out inactive workflows")
        void shouldFilterOutInactiveWorkflows() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            List<Map<String, Object>> workflows = List.of(
                    buildWorkflow("wf1", "Inactive 1", false),
                    buildWorkflow("wf2", "Inactive 2", false)
            );
            ResponseEntity<Map> responseEntity = new ResponseEntity<>(buildN8nApiResponse(workflows), HttpStatus.OK);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return correct DTO fields")
        void shouldReturnCorrectDtoFields() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            Map<String, Object> wf = new HashMap<>();
            wf.put("id", "wf-123");
            wf.put("name", "My Workflow");
            wf.put("active", true);
            wf.put("tags", List.of(Map.of("name", "automation"), Map.of("name", "email")));
            wf.put("createdAt", "2024-06-15T10:30:00.000Z");

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(buildN8nApiResponse(List.of(wf)), HttpStatus.OK);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).hasSize(1);
            N8nWorkflowDTO dto = result.get(0);
            assertThat(dto.getId()).isEqualTo("wf-123");
            assertThat(dto.getName()).isEqualTo("My Workflow");
            assertThat(dto.getActive()).isTrue();
            assertThat(dto.getTags()).containsExactly("automation", "email");
            assertThat(dto.getCreatedAt()).isEqualTo("2024-06-15T10:30:00.000Z");
        }
    }

    @Nested
    @DisplayName("listWorkflows() - API Failure Tests")
    class ApiFailureTests {

        @Test
        @DisplayName("Should throw exception with error message when API call fails")
        void shouldThrowOnApiFailure() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            assertThatThrownBy(() -> proxyService.listWorkflows(CONFIG_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("获取 N8N 工作流列表失败")
                    .hasMessageContaining("Connection refused");
        }

        @Test
        @DisplayName("Should throw when API returns non-2xx status")
        void shouldThrowOnNon2xxStatus() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            assertThatThrownBy(() -> proxyService.listWorkflows(CONFIG_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("获取 N8N 工作流列表失败");
        }

        @Test
        @DisplayName("Should throw when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID))
                    .thenThrow(new RuntimeException("N8N 配置不存在: " + CONFIG_ID));

            assertThatThrownBy(() -> proxyService.listWorkflows(CONFIG_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("N8N 配置不存在");
        }
    }

    @Nested
    @DisplayName("listWorkflows() - Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty workflow list from API")
        void shouldHandleEmptyWorkflowList() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(
                    buildN8nApiResponse(Collections.emptyList()), HttpStatus.OK);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle response with no data field")
        void shouldHandleNoDataField() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("nextCursor", "abc"), HttpStatus.OK);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should strip trailing slashes from baseUrl")
        void shouldStripTrailingSlashes() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);

            N8nConfig config = buildConfig();
            config.setBaseUrl("https://n8n.example.com///");
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(config);

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(
                    buildN8nApiResponse(Collections.emptyList()), HttpStatus.OK);
            when(restTemplate.exchange(
                    eq("https://n8n.example.com/api/v1/workflows"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)
            )).thenReturn(responseEntity);

            proxyService.listWorkflows(CONFIG_ID);

            verify(restTemplate).exchange(
                    eq("https://n8n.example.com/api/v1/workflows"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class));
        }

        @Test
        @DisplayName("Should proceed with API call when cache read fails")
        void shouldProceedWhenCacheReadFails() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis connection failed"));
            when(n8nConfigService.getByIdInternal(CONFIG_ID)).thenReturn(buildConfig());

            ResponseEntity<Map> responseEntity = new ResponseEntity<>(
                    buildN8nApiResponse(List.of(buildWorkflow("wf1", "WF", true))), HttpStatus.OK);
            when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(responseEntity);

            List<N8nWorkflowDTO> result = proxyService.listWorkflows(CONFIG_ID);

            assertThat(result).hasSize(1);
        }
    }
}

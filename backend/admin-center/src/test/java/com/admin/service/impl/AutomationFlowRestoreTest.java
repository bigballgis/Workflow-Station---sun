package com.admin.service.impl;

import com.admin.exception.ServiceTaskApiException;
import com.admin.service.AutomationFlowService;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FU 导入包携带的 Automation flow 还原：只补缺失、不覆盖既有；
 * 发布失败（本环境缺 connection 凭据）以状态回传而非中断导入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomationFlowRestoreTest {

    private static final String FLOW_KEY = "flow-key-1";

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ServiceTaskApiClient serviceTaskApiClient;
    @Mock private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AutomationFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        ServiceTaskProperties properties = new ServiceTaskProperties();
        properties.setInternalUrl("http://activepieces:80");
        service = new AutomationFlowServiceImpl(
                jdbcTemplate, objectMapper, serviceTaskApiClient, properties, restTemplate);
    }

    private JsonNode flowExport() {
        return objectMapper.createObjectNode()
                .put("hermesFlowExport", 1)
                .put("flowKey", FLOW_KEY)
                .put("displayName", "Notify requester")
                .put("schemaVersion", "1")
                .set("trigger", objectMapper.createObjectNode().put("name", "webhook"));
    }

    @Test
    void keepsExistingFlowUntouched() {
        // 迁移键已能解析到本环境 flow（同环境重导 / 之前迁移过）；发布态与否都不覆盖既有草稿
        when(jdbcTemplate.queryForList(contains("WHERE id = ?"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "local-flow-id", "published", false)));

        List<AutomationFlowService.FlowRestoreResult> results =
                service.restoreFlows(List.of(flowExport()));

        assertEquals(1, results.size());
        assertEquals(AutomationFlowService.FlowRestoreStatus.ALREADY_PRESENT, results.get(0).status());
        assertEquals("local-flow-id", results.get(0).flowId());
        // 不覆盖既有草稿，也不必登录 AP
        verify(serviceTaskApiClient, never()).signInAsCurrentActor();
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void createsAndPublishesMissingFlow() {
        stubMissingFlow();
        stubCreateFlow();
        stubFlowOperations(ResponseEntity.ok("{}"));

        List<AutomationFlowService.FlowRestoreResult> results =
                service.restoreFlows(List.of(flowExport()));

        assertEquals(AutomationFlowService.FlowRestoreStatus.CREATED, results.get(0).status());
        assertEquals("new-flow-id", results.get(0).flowId());
    }

    @Test
    void reportsPublishFailureWithoutLosingTheImportedDraft() {
        stubMissingFlow();
        stubCreateFlow();
        // IMPORT_FLOW 成功、LOCK_AND_PUBLISH 失败（典型：本环境缺 connection 凭据）。
        // RestTemplate 默认对非 2xx **抛异常**而不是返回状态码——按真实行为打桩，
        // 否则测试会绿而线上整包导入被 HttpClientErrorException 打断（dev E2E 实测过）。
        when(restTemplate.exchange(contains("/api/v1/flows/new-flow-id"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, "{\"code\":\"FLOW_INVALID\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        List<AutomationFlowService.FlowRestoreResult> results =
                service.restoreFlows(List.of(flowExport()));

        assertEquals(AutomationFlowService.FlowRestoreStatus.PUBLISH_FAILED, results.get(0).status());
        assertEquals("new-flow-id", results.get(0).flowId(), "草稿已落地，flowId 必须回传");
        assertNotNull(results.get(0).detail());
    }

    @Test
    void rejectsPayloadThatIsNotAFlowExport() {
        JsonNode notAnExport = objectMapper.createObjectNode().put("displayName", "x");

        assertThrows(IllegalArgumentException.class, () -> service.restoreFlows(List.of(notAnExport)));
    }

    @Test
    void draftImportFailureSurfacesAsApErrorWithUpstreamDetail() {
        stubMissingFlow();
        stubCreateFlow();
        // AP 拒绝 IMPORT_FLOW（同样是抛，不是返回非 2xx）→ 整包导入必须中断，且带上 AP 的原文
        when(restTemplate.exchange(contains("/api/v1/flows/new-flow-id"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error", HttpHeaders.EMPTY,
                        "{\"message\":\"Cannot read properties of undefined\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        ServiceTaskApiException e = assertThrows(ServiceTaskApiException.class,
                () -> service.restoreFlows(List.of(flowExport())));
        assertTrue(e.getMessage().contains("Cannot read properties of undefined"),
                "AP 的失败原因必须透出，否则运维只看到一个 500");
    }

    private void stubMissingFlow() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());
        when(serviceTaskApiClient.signInAsCurrentActor())
                .thenReturn(new ServiceTaskApiClient.ApSession("token", "project-1", "platform-1"));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.<Map<String, Object>>of());
    }

    private void stubCreateFlow() {
        when(restTemplate.exchange(contains("/api/v1/flows"), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", "new-flow-id")));
    }

    private void stubFlowOperations(ResponseEntity<String> response) {
        when(restTemplate.exchange(contains("/api/v1/flows/new-flow-id"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }
}

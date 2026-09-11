package com.admin.service.impl;

import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.service.AutomationFlowService;
import com.admin.servicetask.ApWorkspaceResolver;
import com.admin.servicetask.ApWorkspaceSql;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.entity.VirtualGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * flow 跨 workspace 转让。
 *
 * <p>钉住这条路径上唯一会静默出错的地方：<b>顺序</b>。{@code trigger_source} 带 projectId，
 * 若先改归属再停用，旧 project 上会留一条活着的触发器——flow 在新 workspace 显示"运行中"，
 * 实际由旧 workspace 的记录触发，且不会有任何报错。故必须
 * 「在原 project 停用 → 改归属 → 在目标 project 重新启用」。</p>
 *
 * <p>另钉两条：flowId 不变（已部署 BPMN 存的是解析后的 flowId），以及同一 workspace 内转让
 * 显式报错而不是空转成功。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomationFlowTransferTest {

    private static final String FLOW_ID = "flow-1";
    private static final String TEAM_ID = "vg-team-alpha";
    private static final String USER_ID = "44027893";

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ServiceTaskApiClient serviceTaskApiClient;
    @Mock private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VirtualGroupRepository groupRepository = mock(VirtualGroupRepository.class);
    private final VirtualGroupMemberRepository memberRepository = mock(VirtualGroupMemberRepository.class);
    private AutomationFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        ServiceTaskProperties properties = new ServiceTaskProperties();
        properties.setInternalUrl("http://activepieces:80");
        ApWorkspaceResolver resolver =
                new ApWorkspaceResolver(properties, groupRepository, memberRepository);
        service = new AutomationFlowServiceImpl(jdbcTemplate, objectMapper, serviceTaskApiClient,
                properties, restTemplate, new ApWorkspaceSql(properties), resolver);

        authenticateAsSysAdmin();
        VirtualGroup team = new VirtualGroup();
        team.setId(TEAM_ID);
        team.setName("Team Alpha");
        team.setType("DEVELOPER");
        team.setStatus("ACTIVE");
        when(groupRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(groupRepository.findById("vg-dev-public")).thenReturn(Optional.empty());

        when(serviceTaskApiClient.signInManaged(any(UserPrincipal.class), anyString(), anyString(), anyString()))
                .thenAnswer(call -> new ServiceTaskApiClient.ApSession(
                        "token-for-" + call.getArgument(1), "project-" + call.getArgument(1), "platform-1"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        // varargs：用 Object[] 匹配整个可变参，逐个 any() 在 Mockito 5 下匹配不上
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabledFlowIsDisabledInTheSourceProjectBeforeItsOwnerChanges() {
        stubFlowLocation("ENABLED", "hermes-main");
        stubTargetProjectExists();

        AutomationFlowService.FlowTransferResult result = service.transferFlow(FLOW_ID, TEAM_ID);

        InOrder order = inOrder(restTemplate, jdbcTemplate);
        // 1. 原 project 的会话停用（token 来自 hermes-main）
        order.verify(restTemplate).exchange(contains("/flows/" + FLOW_ID), eq(HttpMethod.POST),
                argThatCarriesToken("token-for-hermes-main"), eq(String.class));
        // 2. 才改归属
        ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
        order.verify(jdbcTemplate).update(contains("UPDATE flow SET"), updateArgs.capture());
        assertEquals(FLOW_ID, updateArgs.getValue()[1]);
        // 3. 最后用目标 project 的会话重新启用，让 AP 按新 project 重建触发器
        order.verify(restTemplate).exchange(contains("/flows/" + FLOW_ID), eq(HttpMethod.POST),
                argThatCarriesToken("token-for-hermes-dg-" + TEAM_ID), eq(String.class));

        assertEquals(FLOW_ID, result.flowId(), "flowId 必须保持不变");
        assertTrue(result.wasEnabled());
        assertEquals("Team Alpha", result.toWorkspaceName());
        assertNotNull(result.fromWorkspaceName());
    }

    @Test
    void disabledFlowIsMovedWithoutTouchingTriggers() {
        stubFlowLocation("DISABLED", "hermes-main");
        stubTargetProjectExists();

        AutomationFlowService.FlowTransferResult result = service.transferFlow(FLOW_ID, TEAM_ID);

        assertFalse(result.wasEnabled());
        verify(restTemplate, never()).exchange(contains("/flows/" + FLOW_ID), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
        verify(jdbcTemplate).update(contains("UPDATE flow SET"), any(Object[].class));
    }

    @Test
    void transferringIntoTheWorkspaceItAlreadyLivesInIsRefused() {
        stubFlowLocation("DISABLED", "hermes-dg-" + TEAM_ID);

        assertThrows(IllegalArgumentException.class, () -> service.transferFlow(FLOW_ID, TEAM_ID));
        verify(jdbcTemplate, never()).update(contains("UPDATE flow SET"), any(Object[].class));
    }

    @Test
    void unknownFlowIsRejectedBeforeAnyApCall() {
        when(jdbcTemplate.queryForList(contains("FROM flow f JOIN project p"), eq(FLOW_ID)))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.transferFlow(FLOW_ID, TEAM_ID));
        verify(serviceTaskApiClient, never())
                .signInManaged(any(UserPrincipal.class), anyString(), anyString(), anyString());
    }

    /** 归属已改、重新启用失败：不能吞——否则 flow 停在"已转让但没跑"的状态而无人知晓。 */
    @Test
    void reEnableFailureIsReportedInsteadOfSwallowed() {
        stubFlowLocation("ENABLED", "hermes-main");
        stubTargetProjectExists();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"))
                .thenThrow(new IllegalStateException("no published version"));

        AutomationFlowService.FlowTransferResult result = service.transferFlow(FLOW_ID, TEAM_ID);

        assertNotNull(result.reEnableFailure());
        assertTrue(result.reEnableFailure().contains("no published version"));
    }

    private void stubFlowLocation(String status, String projectExternalId) {
        when(jdbcTemplate.queryForList(contains("FROM flow f JOIN project p"), eq(FLOW_ID)))
                .thenReturn(List.of(Map.of(
                        "projectId", "project-" + projectExternalId,
                        "projectExternalId", projectExternalId,
                        "status", status,
                        "published", Boolean.TRUE,
                        "flowKey", "order-sync")));
    }

    private void stubTargetProjectExists() {
        when(jdbcTemplate.queryForList(contains("FROM project WHERE"), eq(String.class),
                eq("hermes-dg-" + TEAM_ID)))
                .thenReturn(List.of("project-hermes-dg-" + TEAM_ID));
    }

    private static HttpEntity<String> argThatCarriesToken(String token) {
        return org.mockito.ArgumentMatchers.argThat(entity ->
                entity != null && entity.getHeaders().getFirst("Authorization") != null
                        && entity.getHeaders().getFirst("Authorization").contains(token));
    }

    private static void authenticateAsSysAdmin() {
        UserPrincipal principal = new UserPrincipal();
        principal.setUserId(USER_ID);
        principal.setUsername("developer");
        principal.setRoles(List.of("SYS_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}

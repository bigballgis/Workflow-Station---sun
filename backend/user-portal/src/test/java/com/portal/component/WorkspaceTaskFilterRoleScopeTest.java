package com.portal.component;

import com.platform.security.util.SecurityContextUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.repository.BusinessUnitRepository;
import com.portal.service.PortalWorkspaceAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * MI「按角色分派」任务的 workspace 可见性收敛：
 * <ul>
 *   <li>按人分派（无 miAssigneeMode）→ 任何 workspace 都可见（沿用 assignee/candidate 放行）；</li>
 *   <li>按角色分派（miAssigneeMode=role）→ 仅当用户切到该 role 的 workspace（active role code 匹配 miRoleCode）才可见，
 *       即便当前用户是该任务的 assignee（单持有者角色也遵循此规则）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceTaskFilterComponent MI role-scoped visibility")
class WorkspaceTaskFilterRoleScopeTest {

    @Mock
    private WorkflowEngineClient workflowEngineClient;
    @Mock
    private VirtualGroupAccessComponent virtualGroupAccessComponent;
    @Mock
    private PortalWorkspaceAuthService portalWorkspaceAuthService;
    @Mock
    private BusinessUnitRepository businessUnitRepository;

    private WorkspaceTaskFilterComponent component;
    private MockedStatic<SecurityContextUtils> securityContext;

    private static final String USER = "user-001";
    private static final String ACTIVE_BU_ID = "bu-100";
    private static final String ACTIVE_ROLE_ID = "role-finance-id";
    private static final String ACTIVE_ROLE_CODE = "FINANCE";

    @BeforeEach
    void setUp() {
        component = new WorkspaceTaskFilterComponent(
                workflowEngineClient, virtualGroupAccessComponent,
                portalWorkspaceAuthService, businessUnitRepository);

        securityContext = Mockito.mockStatic(SecurityContextUtils.class);
        // 处于 workspace 模式：有 active BU，才会进入过滤主循环。
        securityContext.when(SecurityContextUtils::getCurrentActiveBusinessUnitId)
                .thenReturn(Optional.of(ACTIVE_BU_ID));
        securityContext.when(SecurityContextUtils::getCurrentUsername)
                .thenReturn(Optional.of(USER));
        securityContext.when(SecurityContextUtils::getCurrentActiveRoleId)
                .thenReturn(Optional.of(ACTIVE_ROLE_ID));
        // BU id→code 解析：非 role 场景才需要；用 lenient 避免 role 场景 unnecessary-stubbing 报错。
        lenient().when(businessUnitRepository.findById(Mockito.anyString()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        securityContext.close();
    }

    /** active roleId → roleCode 反查上下文（含匹配的 FINANCE 与另一个 HR 角色）。 */
    private void stubWorkspaceContexts() {
        when(portalWorkspaceAuthService.listWorkspaceContexts(USER)).thenReturn(List.of(
                PortalWorkspaceAuthService.WorkspaceContextRow.builder()
                        .businessUnitId(ACTIVE_BU_ID).roleId(ACTIVE_ROLE_ID)
                        .roleCode(ACTIVE_ROLE_CODE).roleName("Finance").build(),
                PortalWorkspaceAuthService.WorkspaceContextRow.builder()
                        .businessUnitId(ACTIVE_BU_ID).roleId("role-hr-id")
                        .roleCode("HR").roleName("HR").build()));
    }

    private static TaskInfo roleTask(String taskId, String roleCode, String assignee) {
        return TaskInfo.builder()
                .taskId(taskId)
                .assignee(assignee)
                .miAssigneeMode("role")
                .miRoleCode(roleCode)
                .build();
    }

    @Test
    @DisplayName("Role task visible when active role matches (multi-holder pool, no assignee)")
    void roleTaskVisibleWhenActiveRoleMatches() {
        stubWorkspaceContexts();
        TaskInfo t = roleTask("t1", ACTIVE_ROLE_CODE, null);

        List<TaskInfo> out = component.filterFixedBuRoleTasksForActiveWorkspace(List.of(t), USER);

        assertThat(out).extracting(TaskInfo::getTaskId).containsExactly("t1");
    }

    @Test
    @DisplayName("Role task hidden when active role differs, even if user is its assignee (single-holder role)")
    void roleTaskHiddenWhenActiveRoleDiffersEvenIfAssignee() {
        stubWorkspaceContexts();
        // 单持有者角色 → 走 setAssignee，assignee 恰是当前用户；但 active role 是 FINANCE，任务角色是 HR。
        TaskInfo t = roleTask("t2", "HR", USER);

        List<TaskInfo> out = component.filterFixedBuRoleTasksForActiveWorkspace(List.of(t), USER);

        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("Role task hidden when there is no active role at all")
    void roleTaskHiddenWhenNoActiveRole() {
        securityContext.when(SecurityContextUtils::getCurrentActiveRoleId)
                .thenReturn(Optional.empty());
        TaskInfo t = roleTask("t3", ACTIVE_ROLE_CODE, null);

        List<TaskInfo> out = component.filterFixedBuRoleTasksForActiveWorkspace(List.of(t), USER);

        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("Person-assigned task (no MI role mode) stays visible in any workspace")
    void personTaskVisibleInAnyWorkspace() {
        // 按人分派：无 miAssigneeMode，assignee 是当前用户 → assignee 放行，与 active role 无关。
        TaskInfo t = TaskInfo.builder().taskId("t4").assignee(USER).build();

        List<TaskInfo> out = component.filterFixedBuRoleTasksForActiveWorkspace(List.of(t), USER);

        assertThat(out).extracting(TaskInfo::getTaskId).containsExactly("t4");
    }

    @Test
    @DisplayName("Role-code casing is ignored when matching active role to task role")
    void roleMatchIsCaseInsensitive() {
        stubWorkspaceContexts();
        TaskInfo t = roleTask("t5", "finance", null);

        List<TaskInfo> out = component.filterFixedBuRoleTasksForActiveWorkspace(List.of(t), USER);

        assertThat(out).extracting(TaskInfo::getTaskId).containsExactly("t5");
    }
}

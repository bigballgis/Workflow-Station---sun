package com.portal.util;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only BU Role user tasks form the "Tasks to Claim" pool. The accepted values mirror
 * {@code AssigneeType.fromLegacyCode} in workflow-engine-core; this test is the tripwire for the two
 * sides drifting apart. {@link BuRolePoolTasks#staysOnTodoList} is the post-deploy rule for in-flight
 * My To Do rows (no Flowable rewrite).
 */
@DisplayName("BuRolePoolTasks claim pool identification")
class BuRolePoolTasksTest {

    @Test
    void recognisesEveryBpmnCodeTheEngineMapsToBuRole() {
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("BU_ROLE")).isTrue();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("FIXED_BU_ROLE")).isTrue();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("FIXEDDEPT")).isTrue();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("FIXED_DEPT")).isTrue();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType(" bu_role ")).isTrue();
    }

    @Test
    void excludesHierarchyVirtualGroupAndInitiatorPools() {
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("HIERARCHY_ROLE")).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("INITIATOR_BU_ROLE")).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("VIRTUAL_GROUP")).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("INITIATOR")).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("ENTITY_MANAGER")).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType(null)).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolAssigneeType("  ")).isFalse();
    }

    @Test
    void heldMeansFlowableAssigneeIsSet() {
        assertThat(BuRolePoolTasks.isHeld(task("BU_ROLE", "alice"))).isTrue();
        assertThat(BuRolePoolTasks.isHeld(task("BU_ROLE", ""))).isFalse();
        assertThat(BuRolePoolTasks.isHeld(task("BU_ROLE", null))).isFalse();
        assertThat(BuRolePoolTasks.isHeld(null)).isFalse();
    }

    @Test
    void nullTaskIsNeverAClaimPoolTask() {
        assertThat(BuRolePoolTasks.isClaimPoolTask(null)).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolTask(task(null, null))).isFalse();
    }

    @Test
    void afterDeployUnheldBuRoleLeavesMyTodo() {
        assertThat(BuRolePoolTasks.staysOnTodoList(task("BU_ROLE", null))).isFalse();
        assertThat(BuRolePoolTasks.staysOnTodoList(task("FIXED_BU_ROLE", ""))).isFalse();
        assertThat(BuRolePoolTasks.staysOnTodoList(task("BU_ROLE", "alice"))).isTrue();
        assertThat(BuRolePoolTasks.staysOnTodoList(task("USER", null))).isTrue();
        assertThat(BuRolePoolTasks.staysOnTodoList(task("VIRTUAL_GROUP", null))).isTrue();
        assertThat(BuRolePoolTasks.staysOnTodoList(task("HIERARCHY_ROLE", null))).isTrue();
    }

    @Test
    void retainKeepsOnlyBuRoleRows() {
        TaskInfo pool = task("BU_ROLE", null);
        TaskInfo user = task("USER", "alice");
        assertThat(BuRolePoolTasks.retainClaimPoolTasks(List.of(pool, user))).containsExactly(pool);
        assertThat(BuRolePoolTasks.retainClaimPoolTasks(List.of())).isEmpty();
        assertThat(BuRolePoolTasks.retainClaimPoolTasks(null)).isEmpty();
    }

    @Test
    void emptyEngineOptionalIsTransportFailureNotEmptyPool() {
        assertThatThrownBy(() -> BuRolePoolTasks.requireEnginePage(Optional.empty(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page 0");
        assertThatThrownBy(() -> BuRolePoolTasks.requireEnginePage(null, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page 2");
        Map<String, Object> page = Map.of("tasks", List.of());
        assertThat(BuRolePoolTasks.requireEnginePage(Optional.of(page), 1)).isEqualTo(page);
    }

    /**
     * MI 子任务按角色分派：内层 userTask 的 assigneeType 恒是 ELEMENT_VARIABLE，节点级配置
     * 回答不了「这一行是按人还是按角色派」（assigneeMode=both 时同节点两种行并存）。
     * 判据改用引擎逐行落地的 miAssigneeMode。
     *
     * <p>实测 FU 50005（2026-09-02）：同一 sub form1 节点下，按角色派的行 extendedProperties
     * 带 {@code assigneeMode=role} + {@code roleCodes=[HMDC_Index_Role]}，按人派的行为空。
     * 修复前这些行显示成 "Direct Assignment" 且不进认领池。
     */
    @Test
    void miRoleAssignedSubTaskIsAClaimPoolTaskDespiteElementVariableAssigneeType() {
        TaskInfo roleRow = miTask("ELEMENT_VARIABLE", "role");
        assertThat(BuRolePoolTasks.isMiRoleAssignedTask(roleRow)).isTrue();
        assertThat(BuRolePoolTasks.isClaimPoolTask(roleRow)).isTrue();
        // 未认领的角色行要离开 My To Do，走认领池
        assertThat(BuRolePoolTasks.staysOnTodoList(roleRow)).isFalse();
    }

    @Test
    void miDirectlyAssignedSubTaskStaysADirectAssignment() {
        // 同一节点下按人派的行：miAssigneeMode 为空 —— 不能因为它也是 MI 就当成角色池
        TaskInfo userRow = miTask("ELEMENT_VARIABLE", null);
        assertThat(BuRolePoolTasks.isMiRoleAssignedTask(userRow)).isFalse();
        assertThat(BuRolePoolTasks.isClaimPoolTask(userRow)).isFalse();
        assertThat(BuRolePoolTasks.staysOnTodoList(userRow)).isTrue();

        assertThat(BuRolePoolTasks.isMiRoleAssignedTask(miTask("ELEMENT_VARIABLE", "  "))).isFalse();
        assertThat(BuRolePoolTasks.isMiRoleAssignedTask(miTask("ELEMENT_VARIABLE", "user"))).isFalse();
        assertThat(BuRolePoolTasks.isMiRoleAssignedTask(null)).isFalse();
    }

    private static TaskInfo task(String bpmnAssigneeType, String assignee) {
        return TaskInfo.builder()
                .taskId("t-1")
                .bpmnAssigneeType(bpmnAssigneeType)
                .assignee(assignee)
                .build();
    }

    private static TaskInfo miTask(String bpmnAssigneeType, String miAssigneeMode) {
        return TaskInfo.builder()
                .taskId("t-mi")
                .bpmnAssigneeType(bpmnAssigneeType)
                .miAssigneeMode(miAssigneeMode)
                .multiInstanceSubTask(true)
                .build();
    }
}

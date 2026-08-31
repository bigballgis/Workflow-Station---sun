package com.portal.util;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimPoolTaskIdentityTest {

    @Test
    void prefersBpmnBusinessUnitOverProcessVariables() {
        TaskInfo task = TaskInfo.builder()
                .bpmnBusinessUnitId("bu-bpmn")
                .variables(Map.of("businessUnitId", "bu-var"))
                .build();
        assertThat(ClaimPoolTaskIdentity.businessUnit(task)).isEqualTo("bu-bpmn");
    }

    @Test
    void fallsBackToActiveBusinessUnitVariable() {
        TaskInfo task = TaskInfo.builder()
                .variables(Map.of("activeBusinessUnitId", "bu-active"))
                .build();
        assertThat(ClaimPoolTaskIdentity.businessUnit(task)).isEqualTo("bu-active");
    }

    @Test
    void prefersBpmnRoleIdsOverVariables() {
        TaskInfo task = TaskInfo.builder()
                .bpmnRoleIds(List.of("role-a"))
                .variables(Map.of("roleId", "role-var"))
                .build();
        assertThat(ClaimPoolTaskIdentity.roleIds(task)).containsExactly("role-a");
    }

    @Test
    void parsesCommaSeparatedRoleVariable() {
        TaskInfo task = TaskInfo.builder()
                .variables(Map.of("roleIds", "r1, r2"))
                .build();
        assertThat(ClaimPoolTaskIdentity.roleIds(task)).containsExactly("r1", "r2");
    }
}

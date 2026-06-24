package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnInitiatorTaskDetectionTest {

    @Test
    void buRoleFirstTaskShouldNotAutoComplete() {
        Map<String, Object> task = Map.of(
                "bpmnAssigneeType", "BU_ROLE",
                "candidateUserIds", List.of("user-a", "user-b"));
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(task)).isFalse();
    }

    @Test
    void fixedBuRoleFirstTaskShouldNotAutoComplete() {
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(
                Map.of("bpmnAssigneeType", "FIXED_BU_ROLE"))).isFalse();
    }

    @Test
    void initiatorBuRoleFirstTaskShouldNotAutoComplete() {
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(
                Map.of("bpmnAssigneeType", "INITIATOR_BU_ROLE"))).isFalse();
    }

    @Test
    void initiatorTypeShouldAutoComplete() {
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(
                Map.of("bpmnAssigneeType", "INITIATOR"))).isTrue();
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(
                Map.of("bpmnAssigneeType", "Process_Initiator"))).isTrue();
    }

    @Test
    void legacyInitiatorWithoutExtensionShouldAutoCompleteWhenAssignedToInitiator() {
        Map<String, Object> task = Map.of(
                "initiatorId", "starter-1",
                "currentAssignee", "starter-1");
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(task)).isTrue();
    }

    @Test
    void legacyApprovalPoolWithoutExtensionShouldNotAutoComplete() {
        Map<String, Object> task = Map.of(
                "initiatorId", "starter-1",
                "assignmentType", "BU_ROLE",
                "candidateUserIds", List.of("approver-1"));
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(task)).isFalse();
    }

    @Test
    void nullOrEmptyTaskShouldNotAutoComplete() {
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(null)).isFalse();
        assertThat(BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(Map.of())).isFalse();
    }
}

package com.workflow.dto.request;

import com.workflow.enums.DelegatedTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskDelegationRequest USER / BU_ROLE validation")
class TaskDelegationRequestValidationTest {

    @Test
    @DisplayName("USER target is valid when delegatedTo differs from delegatedBy")
    void userTargetValid() {
        TaskDelegationRequest request = TaskDelegationRequest.builder()
                .taskId("task-1")
                .delegatedBy("user-a")
                .delegatedTo("user-b")
                .delegatedTargetType(DelegatedTargetType.USER)
                .build();
        assertThat(request.isValid()).isTrue();
        assertThat(request.getValidationError()).isNull();
    }

    @Test
    @DisplayName("USER target rejects self-delegate")
    void userTargetRejectsSelf() {
        TaskDelegationRequest request = TaskDelegationRequest.builder()
                .taskId("task-1")
                .delegatedBy("user-a")
                .delegatedTo("user-a")
                .delegatedTargetType(DelegatedTargetType.USER)
                .build();
        assertThat(request.isValid()).isFalse();
        assertThat(request.getValidationError()).contains("自己");
    }

    @Test
    @DisplayName("BU_ROLE target requires both codes")
    void buRoleRequiresPair() {
        TaskDelegationRequest buOnly = TaskDelegationRequest.builder()
                .taskId("task-1")
                .delegatedBy("user-a")
                .delegatedTargetType(DelegatedTargetType.BU_ROLE)
                .delegatedBuCode("HK")
                .build();
        assertThat(buOnly.isValid()).isFalse();

        TaskDelegationRequest roleOnly = TaskDelegationRequest.builder()
                .taskId("task-1")
                .delegatedBy("user-a")
                .delegatedTargetType(DelegatedTargetType.BU_ROLE)
                .delegatedRoleCode("APPROVER")
                .build();
        assertThat(roleOnly.isValid()).isFalse();

        TaskDelegationRequest pair = TaskDelegationRequest.builder()
                .taskId("task-1")
                .delegatedBy("user-a")
                .delegatedTargetType(DelegatedTargetType.BU_ROLE)
                .delegatedBuCode("HK")
                .delegatedRoleCode("APPROVER")
                .build();
        assertThat(pair.isValid()).isTrue();
    }
}

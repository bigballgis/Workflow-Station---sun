package com.workflow.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessInstanceResult — start-time nextTaskName")
class ProcessInstanceResultStartCurrentNodeTest {

    @Test
    @DisplayName("status nextTaskName is copied onto the start result")
    void copiesNextTaskName() {
        ProcessInstanceResult result = ProcessInstanceResult.builder().success(true).build();

        result.applyRuntimeStatus(Map.of("nextTaskName", "Case Submission"));

        assertThat(result.getNextTaskName()).isEqualTo("Case Submission");
    }

    @Test
    @DisplayName("blank or missing nextTaskName leaves the field unset")
    void ignoresBlankAndMissing() {
        ProcessInstanceResult result = ProcessInstanceResult.builder().success(true).build();

        result.applyRuntimeStatus(Map.of("nextTaskName", "  "));
        assertThat(result.getNextTaskName()).isNull();

        result.applyRuntimeStatus(Map.of("state", "RUNNING"));
        assertThat(result.getNextTaskName()).isNull();

        result.applyRuntimeStatus(null);
        assertThat(result.getNextTaskName()).isNull();
    }
}

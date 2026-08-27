package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToClaimTaskColumnSpec")
class ToClaimTaskColumnSpecTest {

    @Test
    void showsWhoIsHoldingTheRequest() {
        ListColumnMeta claimedBy = column("assigneeName");
        assertThat(claimedBy.label()).isEqualTo("task.claimedBy");
        assertThat(claimedBy.filterable()).isTrue();
    }

    @Test
    void dropsAssignmentTypeBecauseEveryRowIsTheSameBuRolePool() {
        Set<String> fields = ToClaimTaskColumnSpec.columns().stream()
                .map(ListColumnMeta::field)
                .collect(Collectors.toSet());
        assertThat(fields).doesNotContain("assignmentType");
    }

    @Test
    void keepsTheTodoIdentificationColumns() {
        List<String> fields = ToClaimTaskColumnSpec.columns().stream()
                .map(ListColumnMeta::field)
                .toList();
        assertThat(fields).containsSequence("requestId", "taskName", "currentStepName",
                "processDefinitionName", "initiatorName", "assigneeName");
    }

    private static ListColumnMeta column(String field) {
        return ToClaimTaskColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}

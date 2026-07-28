package com.workflow.entity;

import com.workflow.enums.AssignmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: assignment_target must be TEXT so large CANDIDATE_USERS pools
 * (role/BU resolution) are not truncated at VARCHAR(255).
 */
@DisplayName("ExtendedTaskInfo assignment_target TEXT mapping")
class ExtendedTaskInfoAssignmentTargetTextTest {

    private static final int CANDIDATE_COUNT = 13;
    /** UUID-shaped ids (36 chars) so 13 joined exceed 255. */
    private static final int ID_LENGTH = 36;

    @Test
    @DisplayName("assignment_target Column maps to PostgreSQL TEXT")
    void assignmentTarget_isMappedAsText() throws Exception {
        Field field = ExtendedTaskInfo.class.getDeclaredField("assignmentTarget");
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualToIgnoringCase("TEXT");
        assertThat(column.nullable()).isFalse();
    }

    @Test
    @DisplayName("entity @Table no longer declares full-column idx_assignment_target")
    void assignmentTarget_fullColumnIndexRemovedFromEntity() {
        Table table = ExtendedTaskInfo.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();

        List<String> indexNames = Arrays.stream(table.indexes())
                .map(Index::name)
                .collect(Collectors.toList());

        assertThat(indexNames).doesNotContain("idx_assignment_target");
    }

    @Test
    @DisplayName("13 long candidate user ids join to >255 and persist fully on entity")
    void longCandidateUserPool_preservesFullJoinedTarget() {
        List<String> candidateIds = IntStream.rangeClosed(1, CANDIDATE_COUNT)
                .mapToObj(i -> String.format("aaaaaaaa-bbbb-cccc-dddd-%012d", i))
                .collect(Collectors.toCollection(ArrayList::new));

        assertThat(candidateIds).hasSize(CANDIDATE_COUNT);
        assertThat(candidateIds.get(0)).hasSize(ID_LENGTH);

        String joined = String.join(",", candidateIds);
        assertThat(joined.length())
                .as("joined candidate pool must exceed former VARCHAR(255) limit")
                .isGreaterThan(255);

        ExtendedTaskInfo saved = ExtendedTaskInfo.builder()
                .taskId("task-long-candidates-001")
                .processInstanceId("proc-001")
                .processDefinitionId("proc-def-001")
                .assignmentType(AssignmentType.CANDIDATE_USERS)
                .assignmentTarget(joined)
                .status("CREATED")
                .createdTime(java.time.LocalDateTime.now())
                .isDeleted(false)
                .build();

        assertThat(saved.getAssignmentTarget()).isEqualTo(joined);
        assertThat(saved.getAssignmentTarget().split(",")).containsExactlyElementsOf(candidateIds);
        assertThat(saved.getAssignmentTarget().length()).isGreaterThan(255);
    }
}

package com.portal.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessAssigneeSnapshotTest {

    @Test
    void fromEngineTask_usesCandidateUserIdsForPool() {
        Map<String, Object> task = Map.of(
                "currentAssignee", "",
                "candidateUserIds", List.of("uuid-1", "uuid-2", "uuid-3"),
                "assignmentType", "CANDIDATE_USERS");

        ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(task);

        assertThat(snapshot.getAssigneeUserId()).isNull();
        assertThat(snapshot.getCandidateUserIds()).isEqualTo("uuid-1,uuid-2,uuid-3");
    }

    @Test
    void fromEngineTask_usesAssignmentTargetForBuRolePool() {
        Map<String, Object> task = Map.of(
                "assignmentTarget", "uuid-a,uuid-b",
                "assignmentType", "CANDIDATE_USERS");

        ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(task);

        assertThat(snapshot.getAssigneeUserId()).isNull();
        assertThat(snapshot.getCandidateUserIds()).isEqualTo("uuid-a,uuid-b");
    }

    @Test
    void fromEngineTask_singleAssignee() {
        Map<String, Object> task = Map.of("currentAssignee", "uuid-solo");

        ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(task);

        assertThat(snapshot.getAssigneeUserId()).isEqualTo("uuid-solo");
        assertThat(snapshot.getCandidateUserIds()).isNull();
    }

    @Test
    void collectUserKeys_mergesAssigneeAndCandidates() {
        Set<String> keys = ProcessAssigneeSnapshot.collectUserKeys("uuid-1", "uuid-2,uuid-3");

        assertThat(keys).containsExactly("uuid-1", "uuid-2", "uuid-3");
    }
}

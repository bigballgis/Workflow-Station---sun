package com.portal.property;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 18: Snapshot diff detection
 *
 * For any completed Task_Instance, and for each field in the Task Form, if the snapshot
 * value differs from the current live process variable value, the diff should be flagged.
 * The number of flagged diffs should equal the number of fields where
 * snapshot.fieldValues[key] != liveValues[key].
 *
 * Validates: Requirements 10.2, 10.3
 */
public class SnapshotDiffPropertyTest {

    /**
     * Property 18: Diff count equals number of fields where snapshot != live.
     *
     * Validates: Requirements 10.2, 10.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 18: Snapshot diff detection")
    void snapshotDiffCountMatchesActualDifferences(
            @ForAll("diffConfigs") DiffConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());

        // Calculate expected diff count manually
        int expectedDiffs = 0;
        for (Map.Entry<String, Object> entry : config.snapshotValues.entrySet()) {
            Object snapshotVal = entry.getValue();
            Object liveVal = config.liveValues.get(entry.getKey());
            if (!Objects.equals(snapshotVal, liveVal)) {
                expectedDiffs++;
            }
        }

        // Call countSnapshotDiffs
        int actualDiffs = component.countSnapshotDiffs(config.snapshotValues, config.liveValues);

        // Core property: diff count matches
        assertThat(actualDiffs)
                .as("Diff count should equal number of fields where snapshot != live")
                .isEqualTo(expectedDiffs);
    }

    /**
     * Property 18: Identical snapshot and live values produce zero diffs.
     *
     * Validates: Requirements 10.2, 10.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 18: Identical values produce zero diffs")
    void identicalValuesProduceZeroDiffs(
            @ForAll("fieldValueMaps") Map<String, Object> values) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());

        // Same values for snapshot and live
        int diffs = component.countSnapshotDiffs(values, new HashMap<>(values));

        assertThat(diffs)
                .as("Identical snapshot and live values should produce zero diffs")
                .isZero();
    }

    /**
     * Property 18: Completely different values produce max diffs.
     *
     * Validates: Requirements 10.2, 10.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 18: Completely different values produce max diffs")
    void completelyDifferentValuesProduceMaxDiffs(
            @ForAll("disjointValueConfigs") DisjointValueConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());

        int diffs = component.countSnapshotDiffs(config.snapshotValues, config.liveValues);

        // All fields differ
        assertThat(diffs)
                .as("All fields should differ")
                .isEqualTo(config.snapshotValues.size());
    }

    // ========== Data classes ==========

    static class DiffConfig {
        Map<String, Object> snapshotValues;
        Map<String, Object> liveValues;
    }

    static class DisjointValueConfig {
        Map<String, Object> snapshotValues;
        Map<String, Object> liveValues;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<DiffConfig> diffConfigs() {
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 8);

        return fieldCounts.map(count -> {
            DiffConfig config = new DiffConfig();
            config.snapshotValues = new HashMap<>();
            config.liveValues = new HashMap<>();

            Random random = new Random();
            for (int i = 0; i < count; i++) {
                String fieldName = "field_" + i;
                String snapshotVal = "snapshot_" + i;
                // Some fields change, some stay the same
                String liveVal = random.nextBoolean() ? snapshotVal : "live_" + i;
                config.snapshotValues.put(fieldName, snapshotVal);
                config.liveValues.put(fieldName, liveVal);
            }
            return config;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> fieldValueMaps() {
        return Arbitraries.integers().between(0, 6)
                .map(count -> {
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        map.put("field_" + i, "value_" + i);
                    }
                    return map;
                });
    }

    @Provide
    Arbitrary<DisjointValueConfig> disjointValueConfigs() {
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 6);

        return fieldCounts.map(count -> {
            DisjointValueConfig config = new DisjointValueConfig();
            config.snapshotValues = new HashMap<>();
            config.liveValues = new HashMap<>();

            for (int i = 0; i < count; i++) {
                String fieldName = "field_" + i;
                config.snapshotValues.put(fieldName, "snapshot_" + i);
                config.liveValues.put(fieldName, "live_" + i);
            }
            return config;
        });
    }
}

package com.portal.property;

import com.portal.component.TaskFormComponent;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property 27: Concurrent modification flagged in Change_History
 *
 * For any two concurrent Task Form submissions that modify overlapping fields,
 * the Change_History records for the overlapping fields should have isConcurrent = true.
 *
 * This test validates the detectConcurrentModifications logic directly:
 * when baseline values differ from current values for submitted fields,
 * those fields are flagged as concurrent.
 *
 * Validates: Requirements 15.6
 */
public class ConcurrentModificationFlagPropertyTest {

    /**
     * Property 27: Fields where baseline != current are flagged as concurrent.
     *
     * Validates: Requirements 15.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 27: Concurrent modification flagged in Change_History")
    void concurrentModificationDetectedWhenBaselineDiffersFromCurrent(
            @ForAll("concurrentDetectionConfigs") ConcurrentDetectionConfig config) {

        TaskFormComponent component = createMinimalComponent();

        Set<String> detected = component.detectConcurrentModifications(
                config.baselineValues, config.currentVariables, config.submittedFieldNames);

        // Core property: a field is flagged as concurrent iff
        // baseline contains the field AND baseline[field] != current[field]
        for (String fieldName : config.submittedFieldNames) {
            boolean shouldBeConcurrent = config.baselineValues.containsKey(fieldName)
                    && !Objects.equals(
                            config.baselineValues.get(fieldName),
                            config.currentVariables.get(fieldName));

            if (shouldBeConcurrent) {
                assertThat(detected)
                        .as("Field '%s' should be flagged as concurrent (baseline=%s, current=%s)",
                                fieldName, config.baselineValues.get(fieldName),
                                config.currentVariables.get(fieldName))
                        .contains(fieldName);
            } else {
                assertThat(detected)
                        .as("Field '%s' should NOT be flagged as concurrent", fieldName)
                        .doesNotContain(fieldName);
            }
        }
    }

    /**
     * Property 27 (null baseline): When baseline is null, no concurrent modifications detected.
     *
     * Validates: Requirements 15.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 27: Null baseline means no concurrent detection")
    void nullBaselineMeansNoConcurrentDetection(
            @ForAll("fieldNameSets") Set<String> submittedFields) {

        TaskFormComponent component = createMinimalComponent();

        Map<String, Object> currentVars = new HashMap<>();
        for (String f : submittedFields) {
            currentVars.put(f, "some_value");
        }

        Set<String> detected = component.detectConcurrentModifications(
                null, currentVars, submittedFields);

        assertThat(detected).isEmpty();
    }

    /**
     * Property 27 (identical baseline): When baseline == current, no concurrent modifications.
     *
     * Validates: Requirements 15.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 27: Identical baseline and current means no concurrent modification")
    void identicalBaselineAndCurrentMeansNoConcurrent(
            @ForAll("fieldValueMaps") Map<String, Object> fieldValues) {

        TaskFormComponent component = createMinimalComponent();

        // Baseline and current are identical
        Map<String, Object> baseline = new HashMap<>(fieldValues);
        Map<String, Object> current = new HashMap<>(fieldValues);

        Set<String> detected = component.detectConcurrentModifications(
                baseline, current, fieldValues.keySet());

        assertThat(detected).isEmpty();
    }

    // ========== Helper ==========

    private TaskFormComponent createMinimalComponent() {
        // TaskFormComponent constructor requires dependencies, but detectConcurrentModifications
        // is a pure function that doesn't use them
        return new TaskFormComponent(null, null, null);
    }

    // ========== Data classes ==========

    static class ConcurrentDetectionConfig {
        Map<String, Object> baselineValues;
        Map<String, Object> currentVariables;
        Set<String> submittedFieldNames;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ConcurrentDetectionConfig> concurrentDetectionConfigs() {
        return Arbitraries.integers().between(1, 5).flatMap(fieldCount -> {
            return Arbitraries.integers().between(0, fieldCount).flatMap(modifiedCount -> {
                ConcurrentDetectionConfig config = new ConcurrentDetectionConfig();
                config.baselineValues = new HashMap<>();
                config.currentVariables = new HashMap<>();
                config.submittedFieldNames = new HashSet<>();

                for (int i = 0; i < fieldCount; i++) {
                    String fieldName = "field_" + i;
                    config.submittedFieldNames.add(fieldName);
                    config.baselineValues.put(fieldName, "baseline_" + i);

                    if (i < modifiedCount) {
                        // This field was modified by another user (concurrent)
                        config.currentVariables.put(fieldName, "modified_by_other_" + i);
                    } else {
                        // This field was NOT modified (same as baseline)
                        config.currentVariables.put(fieldName, "baseline_" + i);
                    }
                }

                return Arbitraries.just(config);
            });
        });
    }

    @Provide
    Arbitrary<Set<String>> fieldNameSets() {
        return Arbitraries.integers().between(1, 5).map(count -> {
            Set<String> fields = new HashSet<>();
            for (int i = 0; i < count; i++) {
                fields.add("field_" + i);
            }
            return fields;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> fieldValueMaps() {
        return Arbitraries.integers().between(1, 5).map(count -> {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < count; i++) {
                map.put("field_" + i, "value_" + i);
            }
            return map;
        });
    }
}

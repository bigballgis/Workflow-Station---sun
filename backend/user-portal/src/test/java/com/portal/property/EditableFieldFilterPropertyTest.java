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
 * Property 16: Only editable fields are updated on Task Form submission
 *
 * For any Task Form submission payload, the system should only update process variable
 * fields that are configured as EDITABLE in the Task Form's fieldPermissions.
 * Fields configured as READONLY should retain their previous values even if the
 * submission payload includes new values for them.
 *
 * Validates: Requirements 9.7, 12.4
 */
public class EditableFieldFilterPropertyTest {

    /**
     * Property 16: READONLY fields are filtered out, only EDITABLE fields pass through.
     *
     * Validates: Requirements 9.7, 12.4
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 16: Only editable fields are updated on Task Form submission")
    void onlyEditableFieldsAreUpdated(
            @ForAll("fieldPermissionConfigs") FieldPermissionConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class));

        // Call filterEditableFields
        Map<String, Object> result = component.filterEditableFields(
                config.submittedData, config.fieldPermissions);

        // Core property: result should only contain EDITABLE fields
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String permission = config.fieldPermissions.get(entry.getKey());
            assertThat(permission)
                    .as("Field '%s' in result should have EDITABLE permission", entry.getKey())
                    .isEqualTo("EDITABLE");
        }

        // Core property: all EDITABLE fields from submitted data should be in result
        for (Map.Entry<String, Object> entry : config.submittedData.entrySet()) {
            String permission = config.fieldPermissions.get(entry.getKey());
            if ("EDITABLE".equals(permission)) {
                assertThat(result)
                        .as("EDITABLE field '%s' should be in result", entry.getKey())
                        .containsKey(entry.getKey());
                assertThat(result.get(entry.getKey()))
                        .as("EDITABLE field '%s' value should match submitted value", entry.getKey())
                        .isEqualTo(entry.getValue());
            }
        }

        // Core property: READONLY fields should NOT be in result
        for (Map.Entry<String, Object> entry : config.submittedData.entrySet()) {
            String permission = config.fieldPermissions.get(entry.getKey());
            if ("READONLY".equals(permission)) {
                assertThat(result)
                        .as("READONLY field '%s' should NOT be in result", entry.getKey())
                        .doesNotContainKey(entry.getKey());
            }
        }

        // Count property: result size == number of submitted fields with EDITABLE permission
        long expectedEditableCount = config.submittedData.keySet().stream()
                .filter(key -> "EDITABLE".equals(config.fieldPermissions.get(key)))
                .count();
        assertThat(result).hasSize((int) expectedEditableCount);
    }

    /**
     * Property 16 (empty permissions): When fieldPermissions is empty, all fields pass through.
     *
     * Validates: Requirements 9.7, 12.4
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 16: Empty permissions allows all fields")
    void emptyPermissionsAllowsAllFields(
            @ForAll("formDataMaps") Map<String, Object> formData) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class));

        // Empty permissions = accept all
        Map<String, Object> result = component.filterEditableFields(formData, Collections.emptyMap());
        assertThat(result).containsAllEntriesOf(formData);

        // Null permissions = accept all
        Map<String, Object> resultNull = component.filterEditableFields(formData, null);
        assertThat(resultNull).containsAllEntriesOf(formData);
    }

    // ========== Data class ==========

    static class FieldPermissionConfig {
        Map<String, String> fieldPermissions;
        Map<String, Object> submittedData;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FieldPermissionConfig> fieldPermissionConfigs() {
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 8);

        return fieldCounts.map(count -> {
            FieldPermissionConfig config = new FieldPermissionConfig();
            config.fieldPermissions = new HashMap<>();
            config.submittedData = new HashMap<>();

            Random random = new Random();
            for (int i = 0; i < count; i++) {
                String fieldName = "field_" + i;
                String permission = random.nextBoolean() ? "EDITABLE" : "READONLY";
                config.fieldPermissions.put(fieldName, permission);
                config.submittedData.put(fieldName, "submitted_value_" + i);
            }
            return config;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> formDataMaps() {
        return Arbitraries.integers().between(0, 5)
                .map(count -> {
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        map.put("field_" + i, "value_" + i);
                    }
                    return map;
                });
    }
}

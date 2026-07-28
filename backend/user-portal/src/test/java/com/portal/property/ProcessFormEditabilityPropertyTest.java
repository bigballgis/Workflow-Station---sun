package com.portal.property;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ChangeHistorySubmissionFilter;
import com.portal.component.ProcessFormComponent;
import com.portal.dto.ProcessFormData;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 13: Process Form editability matches process state
 *
 * For any Process_Instance, the editable flag returned by the Process Form API
 * should be true if and only if the process is in Return_To_Requester state.
 * In all other running states, editable should be false.
 *
 * Validates: Requirements 8.1, 8.3
 */
public class ProcessFormEditabilityPropertyTest {

        private static final String RETURN_TO_REQUESTER = "RETURN_TO_REQUESTER";

        /**
         * Property 13: editable=true iff process state is Return_To_Requester.
         *
         * Validates: Requirements 8.1, 8.3
         */
        @Property(tries = 100)
        @Label("Feature: process-task-form-separation, Property 13: Process Form editability matches process state")
        void processFormEditabilityMatchesState(
                        @ForAll("processStateConfigs") ProcessStateConfig config) {

                ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
                ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

                ProcessFormComponent component = new ProcessFormComponent(
                                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                                new ObjectMapper(), mock(JdbcTemplate.class),
                                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
                ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

                // Build a ProcessInstance with the given state
                ProcessInstance processInstance = ProcessInstance.builder()
                                .id(config.processInstanceId)
                                .processDefinitionKey(config.processDefinitionKey)
                                .processDefinitionName("Test Process")
                                .startUserId("user-001")
                                .status(config.processState)
                                .variables(config.variables)
                                .build();

                when(processInstanceRepository.findById(config.processInstanceId))
                                .thenReturn(Optional.of(processInstance));

                // Call getProcessFormData
                ProcessFormData formData = component.getProcessFormData(config.processInstanceId);

                // Core property: editable == true iff state is RETURN_TO_REQUESTER
                boolean expectedEditable = RETURN_TO_REQUESTER.equals(config.processState);
                assertThat(formData.isEditable())
                                .as("editable should be %s for state '%s'", expectedEditable, config.processState)
                                .isEqualTo(expectedEditable);

                // Additional invariants
                assertThat(formData.getProcessInstanceId()).isEqualTo(config.processInstanceId);
                assertThat(formData.getProcessState()).isEqualTo(config.processState);
                assertThat(formData.getFormType()).isEqualTo("PROCESS");
                assertThat(formData.getFieldValues()).isNotNull();
        }

        /**
         * Property 13 (submit guard): submitProcessFormUpdate throws 403 when NOT in
         * Return_To_Requester.
         *
         * Validates: Requirements 8.1, 8.3
         */
        @Property(tries = 100)
        @Label("Feature: process-task-form-separation, Property 13: Submit rejected when not in Return_To_Requester state")
        void submitRejectedWhenNotInReturnState(
                        @ForAll("nonReturnStates") String processState,
                        @ForAll("processInstanceIds") String processInstanceId) {

                ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
                ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

                ProcessFormComponent component = new ProcessFormComponent(
                                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                                new ObjectMapper(), mock(JdbcTemplate.class),
                                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
                ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

                ProcessInstance processInstance = ProcessInstance.builder()
                                .id(processInstanceId)
                                .processDefinitionKey("test-process")
                                .startUserId("user-001")
                                .status(processState)
                                .variables(Map.of("field1", "value1"))
                                .build();

                when(processInstanceRepository.findById(processInstanceId))
                                .thenReturn(Optional.of(processInstance));

                Map<String, Object> formData = Map.of("field1", "updated");

                assertThatThrownBy(() -> component.submitProcessFormUpdate(processInstanceId, "user-001", formData))
                                .isInstanceOf(PortalException.class)
                                .satisfies(ex -> {
                                        PortalException pe = (PortalException) ex;
                                        assertThat(pe.getCode()).isEqualTo("403");
                                });
        }

        /**
         * Property 13 (positive submit): submitProcessFormUpdate succeeds in
         * Return_To_Requester state.
         *
         * Validates: Requirements 8.1, 8.3
         */
        @Property(tries = 100)
        @Label("Feature: process-task-form-separation, Property 13: Submit succeeds in Return_To_Requester state")
        void submitSucceedsInReturnState(
                        @ForAll("processInstanceIds") String processInstanceId,
                        @ForAll("userIds") String userId,
                        @ForAll("formDataMaps") Map<String, Object> formData) {

                ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
                ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

                ProcessFormComponent component = new ProcessFormComponent(
                                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                                new ObjectMapper(), mock(JdbcTemplate.class),
                                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
                ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");
                ChangeHistorySubmissionFilter submissionFilter = mock(ChangeHistorySubmissionFilter.class);
                when(submissionFilter.filterProcessSubmission(eq("test-process"), anyMap(), anyMap()))
                                .thenReturn(formData);
                ReflectionTestUtils.setField(component, "changeHistorySubmissionFilter", submissionFilter);
                
                ProcessInstance processInstance = ProcessInstance.builder()
                                .id(processInstanceId)
                                .processDefinitionKey("test-process")
                                .functionUnitCode("test-process")
                                .startUserId(userId)
                                .status(RETURN_TO_REQUESTER)
                                .variables(new HashMap<>(Map.of("existing_field", "existing_value")))
                                .build();

                when(processInstanceRepository.findById(processInstanceId))
                                .thenReturn(Optional.of(processInstance));
                when(processInstanceRepository.save(any(ProcessInstance.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Should NOT throw
                assertThatCode(() -> component.submitProcessFormUpdate(processInstanceId, userId, formData))
                                .doesNotThrowAnyException();

                // Verify process instance was saved
                verify(processInstanceRepository).save(any(ProcessInstance.class));

                // Verify change history was recorded
                verify(changeHistoryComponent).recordFieldChanges(
                                argThat(ctx -> ctx.getProcessInstanceId().equals(processInstanceId)
                                                && ctx.getUserId().equals(userId)
                                                && RETURN_TO_REQUESTER.equals(ctx.getStageId())),
                                anyMap(),
                                eq(formData));
        }

        // ========== Data classes ==========

        static class ProcessStateConfig {
                String processInstanceId;
                String processDefinitionKey;
                String processState;
                Map<String, Object> variables;
        }

        // ========== Arbitraries ==========

        @Provide
        Arbitrary<ProcessStateConfig> processStateConfigs() {
                Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                                .map(s -> "proc_" + s);
                Arbitrary<String> processKeys = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                                .map(s -> "key_" + s);
                Arbitrary<String> states = Arbitraries.of(
                                "RUNNING", "COMPLETED", "CANCELLED", "SUSPENDED",
                                "RETURN_TO_REQUESTER", "PENDING", "REJECTED");
                Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(0, 5);

                return Combinators.combine(processIds, processKeys, states, fieldCounts)
                                .as((procId, procKey, state, count) -> {
                                        ProcessStateConfig config = new ProcessStateConfig();
                                        config.processInstanceId = procId;
                                        config.processDefinitionKey = procKey;
                                        config.processState = state;
                                        config.variables = new HashMap<>();
                                        for (int i = 0; i < count; i++) {
                                                config.variables.put("field_" + i, "value_" + i);
                                        }
                                        return config;
                                });
        }

        @Provide
        Arbitrary<String> nonReturnStates() {
                return Arbitraries.of(
                                "RUNNING", "COMPLETED", "CANCELLED", "SUSPENDED",
                                "PENDING", "REJECTED", "TERMINATED");
        }

        @Provide
        Arbitrary<String> processInstanceIds() {
                return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                                .map(s -> "proc_" + s);
        }

        @Provide
        Arbitrary<String> userIds() {
                return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                                .map(s -> "user_" + s);
        }

        @Provide
        Arbitrary<Map<String, Object>> formDataMaps() {
                return Arbitraries.integers().between(1, 5)
                                .flatMap(count -> {
                                        Map<String, Object> map = new HashMap<>();
                                        for (int i = 0; i < count; i++) {
                                                map.put("field_" + i, "new_value_" + i);
                                        }
                                        return Arbitraries.just(map);
                                });
        }
}

package com.portal.property;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 24: Process Form update rejected when not in return state
 *
 * For any Process_Instance that is not in Return_To_Requester state,
 * calling the Process Form update API should return HTTP 403.
 *
 * Validates: Requirements 14.8
 */
public class ProcessFormUpdateRejectionPropertyTest {

    private static final String RETURN_TO_REQUESTER = "RETURN_TO_REQUESTER";

    /**
     * Property 24: PUT Process Form update throws 403 when process is NOT in Return_To_Requester state.
     *
     * Validates: Requirements 14.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 24: Process Form update rejected when not in return state")
    void processFormUpdateRejectedWhenNotInReturnState(
            @ForAll("nonReturnStates") String processState,
            @ForAll("processInstanceIds") String processInstanceId,
            @ForAll("userIds") String userId,
            @ForAll("formDataMaps") Map<String, Object> formData) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent);
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status(processState)
                .variables(new HashMap<>(Map.of("field1", "value1")))
                .build();

        when(processInstanceRepository.findById(processInstanceId))
                .thenReturn(Optional.of(processInstance));

        // Core property: must throw PortalException with code "403"
        assertThatThrownBy(() -> component.submitProcessFormUpdate(processInstanceId, userId, formData))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> {
                    PortalException pe = (PortalException) ex;
                    assertThat(pe.getCode()).isEqualTo("403");
                    assertThat(pe.getMessage()).contains("Return_To_Requester");
                });

        // Verify process instance was NOT saved
        verify(processInstanceRepository, never()).save(any());
        // Verify change history was NOT recorded
        verify(changeHistoryComponent, never()).recordFieldChanges(any(), anyMap(), anyMap());
    }

    /**
     * Property 24 (positive): Update succeeds when process IS in Return_To_Requester state.
     *
     * Validates: Requirements 14.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 24: Process Form update succeeds in Return_To_Requester state")
    void processFormUpdateSucceedsInReturnState(
            @ForAll("processInstanceIds") String processInstanceId,
            @ForAll("userIds") String userId,
            @ForAll("formDataMaps") Map<String, Object> formData) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent);
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId(userId)
                .status(RETURN_TO_REQUESTER)
                .variables(new HashMap<>(Map.of("existing", "value")))
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
    }

    // ========== Arbitraries ==========

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

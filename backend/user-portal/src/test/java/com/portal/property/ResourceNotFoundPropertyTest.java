package com.portal.property;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.exception.PortalException;
import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ProcessInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 23: Non-existent resource returns 404
 *
 * For any API request referencing a Process_Instance ID or Task_Instance ID
 * that does not exist, the system should return HTTP 404 with a descriptive error message.
 *
 * Validates: Requirements 14.6
 */
public class ResourceNotFoundPropertyTest {

    /**
     * Property 23a: Non-existent processInstanceId → getProcessFormData throws 404.
     *
     * Validates: Requirements 14.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 23: Non-existent processInstanceId returns 404 from ProcessFormComponent")
    void nonExistentProcessInstanceReturns404(
            @ForAll("randomProcessInstanceIds") String processInstanceId) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                new ObjectMapper(), mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

        // Process instance does not exist
        when(processInstanceRepository.findById(processInstanceId))
                .thenReturn(Optional.empty());

        // Core property: must throw PortalException with code "404"
        assertThatThrownBy(() -> component.getProcessFormData(processInstanceId))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> {
                    PortalException pe = (PortalException) ex;
                    assertThat(pe.getCode()).isEqualTo("404");
                    assertThat(pe.getMessage()).contains(processInstanceId);
                });
    }

    /**
     * Property 23b: Non-existent processInstanceId → submitProcessFormUpdate throws 404.
     *
     * Validates: Requirements 14.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 23: Non-existent processInstanceId returns 404 from submitProcessFormUpdate")
    void nonExistentProcessInstanceReturns404OnSubmit(
            @ForAll("randomProcessInstanceIds") String processInstanceId,
            @ForAll("randomUserIds") String userId) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                new ObjectMapper(), mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

        when(processInstanceRepository.findById(processInstanceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.submitProcessFormUpdate(
                processInstanceId, userId, java.util.Map.of("field", "value")))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> {
                    PortalException pe = (PortalException) ex;
                    assertThat(pe.getCode()).isEqualTo("404");
                    assertThat(pe.getMessage()).contains(processInstanceId);
                });
    }

    /**
     * Property 23c: Non-existent taskId → getTaskFormData throws 404.
     *
     * TaskFormComponent.getTaskInfo throws PortalException("404") for unknown tasks.
     *
     * Validates: Requirements 14.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 23: Non-existent taskId returns 404 from TaskFormComponent")
    void nonExistentTaskIdReturns404(
            @ForAll("randomTaskIds") String taskId) {

        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class));
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // TaskFormComponent.getTaskInfo throws 404 for unknown tasks (Flowable integration pending)
        assertThatThrownBy(() -> component.getTaskFormData(taskId))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> {
                    PortalException pe = (PortalException) ex;
                    assertThat(pe.getCode()).isEqualTo("404");
                    assertThat(pe.getMessage()).contains(taskId);
                });
    }

    /**
     * Property 23d: Non-existent taskId → getCompletedTaskFormData throws 404.
     *
     * Validates: Requirements 14.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 23: Non-existent taskId returns 404 from getCompletedTaskFormData")
    void nonExistentTaskIdReturns404OnCompletedForm(
            @ForAll("randomTaskIds") String taskId) {

        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class));
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        assertThatThrownBy(() -> component.getCompletedTaskFormData(taskId))
                .isInstanceOf(PortalException.class)
                .satisfies(ex -> {
                    PortalException pe = (PortalException) ex;
                    assertThat(pe.getCode()).isEqualTo("404");
                    assertThat(pe.getMessage()).contains(taskId);
                });
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> randomProcessInstanceIds() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                .map(s -> "proc_" + s);
    }

    @Provide
    Arbitrary<String> randomUserIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
    }

    @Provide
    Arbitrary<String> randomTaskIds() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                .map(s -> "task_" + s);
    }
}

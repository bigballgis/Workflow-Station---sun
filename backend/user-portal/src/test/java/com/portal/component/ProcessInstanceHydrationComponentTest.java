package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceHydrationComponentTest {

    private static final String PI_ID = "19deadae-73d8-11f1-ba72-e6a5490c4ac4";

    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ProcessInstanceHydrationComponent hydrationComponent;

    @Test
    void returnsLocalRowWhenPresent() {
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("Process_MCY")
                .functionUnitCatalogId("fu-1").functionUnitCode("MCY").functionUnitVersionLabel("1.0.0")
                .variables(new java.util.HashMap<>(Map.of("functionUnitId", "fu-1", "functionUnitCode", "MCY",
                        "functionUnitVersionLabel", "1.0.0", "amount", 5)))
                .build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID);

        assertThat(result).isSameAs(local);
        verify(workflowEngineClient, never()).getProcessInstance(any());
        verify(processInstanceRepository, never()).save(any());
    }

    @Test
    void pinnedRequestWhoseVariablesLackTheReleaseRecordsItThere() {
        // Pinned before its variables carried the release: pinned-or-live decisions read the variables.
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("fu-call-demo-vendor")
                .functionUnitCatalogId("fu-1").functionUnitCode("fu-call-demo-vendor").functionUnitVersionLabel("1.0.2")
                .variables(new java.util.HashMap<>(Map.of("vendor_name", "Acme")))
                .build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID);

        assertThat(result.getVariables()).containsEntry("functionUnitId", "fu-1")
                .containsEntry("functionUnitCode", "fu-call-demo-vendor")
                .containsEntry("functionUnitVersionLabel", "1.0.2")
                .containsEntry("vendor_name", "Acme");
        // A JSON merge that leaves lock_version alone: concurrent first reads (task page loads two
        // forms in parallel) and an in-flight save cannot collide on optimistic locking.
        org.mockito.ArgumentCaptor<Object> args = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.argThat((String sql) -> sql.startsWith(
                "UPDATE up_process_instance SET variables = CAST(? AS jsonb) || ") && !sql.contains("lock_version")),
                args.capture(), args.capture());
        assertThat(args.getAllValues().get(0).toString()).contains("\"functionUnitId\":\"fu-1\"")
                .contains("\"functionUnitVersionLabel\":\"1.0.2\"");
        assertThat(args.getAllValues().get(1)).isEqualTo(PI_ID);
        verify(processInstanceRepository, never()).save(any());
        verifyNoInteractions(restTemplate, workflowEngineClient);
    }

    @Test
    void healInsideAWriteTransactionFlushesTheCallersPendingChangesBeforeRefreshing() {
        // claim/complete hydrate engine sub-table rows into the managed instance before reading actions:
        // those unflushed writes must reach the row before the refresh reloads it.
        jakarta.persistence.EntityManager entityManager = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(hydrationComponent, "entityManager", entityManager);
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("fu-call-demo-vendor")
                .functionUnitCatalogId("fu-1").functionUnitCode("fu-call-demo-vendor").functionUnitVersionLabel("1.0.2")
                .variables(new java.util.HashMap<>(Map.of("vendor_name", "Acme")))
                .build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));
        when(entityManager.contains(local)).thenReturn(true);

        hydrationComponent.requireProcessInstance(PI_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(entityManager, jdbcTemplate);
        order.verify(entityManager).flush();
        order.verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE up_process_instance SET variables"),
                any(Object.class), eq(PI_ID));
        order.verify(entityManager).refresh(local);
        verify(processInstanceRepository, never()).save(any());
    }

    private ProcessInstance pinnedMcy() {
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("Process_MCY")
                .functionUnitCatalogId("fu-1").functionUnitCode("MCY").functionUnitVersionLabel("1.0.0")
                .variables(new java.util.HashMap<>(Map.of("functionUnitId", "fu-1", "functionUnitCode", "MCY",
                        "functionUnitVersionLabel", "1.0.0")))
                .build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));
        return local;
    }

    /**
     * A request's content is its own release whichever way the page names the unit — by that
     * release, by code or by process key — never the unit's latest or currently active one.
     */
    @Test
    void contentOfARequestIsTheReleaseItWasStartedOn() {
        pinnedMcy();

        assertThat(hydrationComponent.pinnedContentRef("fu-1", null, PI_ID)).isEqualTo("fu-1");
        assertThat(hydrationComponent.pinnedContentRef("MCY", null, PI_ID)).isEqualTo("fu-1");
        assertThat(hydrationComponent.pinnedContentRef("Process_MCY", null, PI_ID)).isEqualTo("fu-1");
    }

    @Test
    void contentOfATaskIsTheReleaseOfItsRequest() {
        pinnedMcy();
        when(workflowEngineClient.getTaskById("task-1"))
                .thenReturn(Optional.of(Map.of("taskId", "task-1", "processInstanceId", PI_ID)));

        assertThat(hydrationComponent.pinnedContentRef("Process_MCY", "task-1", null)).isEqualTo("fu-1");
    }

    /** Naming a different release of the request's unit is a version error, not a silent swap. */
    @Test
    void anotherReleaseThanTheRequestsIsRefused() {
        pinnedMcy();

        assertThatThrownBy(() -> hydrationComponent.pinnedContentRef("fu-2", null, PI_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class)
                .satisfies(error -> assertThat(((com.platform.common.functionunit.FunctionUnitVersionException) error)
                        .getDetails()).containsEntry("reason", "VERSION_CONTEXT_MISMATCH"));
    }

    @Test
    void taskOfAnotherRequestThanTheOneNamedIsRefused() {
        when(workflowEngineClient.getTaskById("task-1"))
                .thenReturn(Optional.of(Map.of("taskId", "task-1", "processInstanceId", "other-request")));

        assertThatThrownBy(() -> hydrationComponent.pinnedContentRef("fu-1", "task-1", PI_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        verify(processInstanceRepository, never()).findById(any());
    }

    /** A task the engine does not know ties the content to no request: nothing is served. */
    @Test
    void contentForAnUnknownTaskIsNotServed() {
        when(workflowEngineClient.getTaskById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hydrationComponent.pinnedContentRef("MCY", "gone", null))
                .isInstanceOf(PortalException.class);
        verify(processInstanceRepository, never()).findById(any());
    }

    /** Without a task or request (starting a new one), the named unit is served as named. */
    @Test
    void contentWithoutARequestIsServedAsNamed() {
        assertThat(hydrationComponent.pinnedContentRef("MCY", null, null)).isEqualTo("MCY");
        verifyNoInteractions(processInstanceRepository, workflowEngineClient);
    }

    @Test
    void variablesNamingAnotherReleaseAreRefusedAndNotOverwritten() {
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("fu-a")
                .functionUnitCatalogId("fu-1").functionUnitCode("fu-a").functionUnitVersionLabel("1.0.2")
                .variables(new java.util.HashMap<>(Map.of("functionUnitId", "fu-other")))
                .build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> hydrationComponent.requireProcessInstance(PI_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        verifyNoInteractions(jdbcTemplate);
        verify(processInstanceRepository, never()).save(any());
    }

    @Test
    void missingVersionLabelIsRecoveredFromThePersistedCatalog() throws Exception {
        var local = ProcessInstance.builder().id(PI_ID).functionUnitCatalogId("fu-old")
                .functionUnitCode("MCY").processDefinitionId("old-definition").build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));
        var row = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(row.getString("id")).thenReturn("fu-old");
        when(row.getString("code")).thenReturn("MCY");
        when(row.getString("version")).thenReturn("1.0.0");
        when(jdbcTemplate.query(eq("SELECT id, code, version FROM sys_function_units WHERE id = ?"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(), eq("fu-old")))
                .thenAnswer(inv -> List.of(((org.springframework.jdbc.core.RowMapper<?>) inv.getArgument(1)).mapRow(row, 0)));
        when(processInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var restored = hydrationComponent.requireProcessInstance(PI_ID);
        assertThat(restored.getFunctionUnitCatalogId()).isEqualTo("fu-old");
        assertThat(restored.getFunctionUnitVersionLabel()).isEqualTo("1.0.0");
        assertThat(restored.getVariables()).containsEntry("functionUnitId", "fu-old");
        verifyNoInteractions(restTemplate, workflowEngineClient);
    }

    @Test
    void hydratesFromEngineWhenLocalMissing() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(workflowEngineClient.getProcessInstance(PI_ID)).thenReturn(Optional.of(Map.of(
                "processInstanceId", PI_ID,
                "processDefinitionKey", "Process_MCY",
                "processDefinitionId", "Process_MCY:6:abc",
                "startUserId", "user-dev",
                "status", "active",
                "variables", Map.of("case_number", "123", "functionUnitId", "fu-1", "functionUnitCode", "MCY", "functionUnitVersionLabel", "1.0.0")
        )));
        when(workflowEngineClient.getProcessInstanceStatus(PI_ID)).thenReturn(Optional.of(Map.of(
                "nextTaskName", "Case Submission",
                "nextAssignee", "user-dev"
        )));
        when(userDisplayNameResolver.resolve("user-dev")).thenReturn("Developer Tester");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID);

        assertThat(result.getId()).isEqualTo(PI_ID);
        assertThat(result.getProcessDefinitionKey()).isEqualTo("Process_MCY");
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getCurrentNode()).isEqualTo("Case Submission");
        assertThat(result.getVariables()).containsEntry("case_number", "123");
        verify(processInstanceRepository).save(any(ProcessInstance.class));
    }

    @Test
    void unresolvedLegacyVersionCannotBeReplacedWithTheCurrentRelease() {
        var legacy = ProcessInstance.builder().id(PI_ID).processDefinitionKey("Process_MCY").build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(legacy));
        assertThatThrownBy(() -> hydrationComponent.requireProcessInstance(PI_ID))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        verify(processInstanceRepository, never()).save(any());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void throwsWhenEngineHasNoInstance() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(workflowEngineClient.getProcessInstance(PI_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hydrationComponent.requireProcessInstance(PI_ID))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining(PI_ID);
    }

    @Test
    void hydratesFromSnapshotWithoutCallingEngine() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(userDisplayNameResolver.resolve("system")).thenReturn("system");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snapshot = Map.of(
                "processDefinitionKey", "test-20260803-xc0jmo",
                "businessKey", "email:msg-1",
                "startUserId", "system",
                "status", "RUNNING",
                "variables", Map.of("subject", "hello", "functionUnitId", "50030", "functionUnitCode", "MCY", "functionUnitVersionLabel", "1.0.0"));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID, snapshot);

        assertThat(result.getId()).isEqualTo(PI_ID);
        assertThat(result.getProcessDefinitionKey()).isEqualTo("test-20260803-xc0jmo");
        assertThat(result.getVariables()).containsEntry("subject", "hello");
        verify(workflowEngineClient, never()).getProcessInstance(any());
        verify(processInstanceRepository).save(any(ProcessInstance.class));
    }

    @Test
    void doesNotHydrateBatchRuntimeMetadataFromEngineVariables() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(userDisplayNameResolver.resolve("system")).thenReturn("system");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID, Map.of(
                "processDefinitionKey", "Process_MCY",
                "variables", Map.of(
                        "functionUnitId", "cat-1",
                        "functionUnitCode", "FU-MCY",
                        "functionUnitVersionLabel", "1.0.0",
                        "businessValue", "keep",
                        SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("forged", true))));

        assertThat(result.getVariables()).containsEntry("businessValue", "keep")
                .doesNotContainKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
    }

    /** The record carries the release it runs; nothing is read from the designer, which runtime does not deploy. */
    @Test
    void recordsTheReleaseWithoutReadingTheDesigner() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(userDisplayNameResolver.resolve("system")).thenReturn("system");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snapshot = Map.of(
                "processDefinitionKey", "Process_MCY",
                "startUserId", "system",
                "status", "RUNNING",
                "variables", Map.of(
                        "functionUnitId", "cat-uuid",
                        "functionUnitCode", "FU-MCY", "functionUnitVersionLabel", "1.0.0"));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID, snapshot);

        assertThat(result.getFunctionUnitCatalogId()).isEqualTo("cat-uuid");
        assertThat(result.getFunctionUnitCode()).isEqualTo("FU-MCY");
        assertThat(result.getFunctionUnitVersionId()).isNull();
        verify(jdbcTemplate, never()).queryForList(org.mockito.ArgumentMatchers.contains("dw_"),
                org.mockito.ArgumentMatchers.<Class<Object>>any(), any(Object[].class));
    }
}

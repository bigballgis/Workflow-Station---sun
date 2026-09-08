package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.entity.ChangeHistory;
import com.portal.entity.ProcessInstance;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.testsupport.PortalTransactionTestSupport;
import com.platform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ATM later stages hydrate lookup/status/stage columns as JSON objects while the
 * submitted form still sends the display scalar. Objects.equals treated that as a
 * FIELD_UPDATE even when the business value was unchanged (Open → Open).
 */
class ChangeHistoryLookupFieldUpdateTest {

    private static final String PROCESS_ID = "proc-lookup-history";
    private static final String FU_CODE = "atm-lookup-fu";
    private static final String PROCESS_FORM_JSON = """
            {"rule":[
              {"field":"urge_status","title":"Urge Status","type":"select"},
              {"field":"case_status","title":"Case Status","type":"select"},
              {"field":"case_stage","title":"Case Stage","type":"select"},
              {"field":"card_number","title":"Card Number","type":"input"}
            ]}
            """;

    private ChangeHistoryRepository changeHistoryRepository;
    private ChangeHistoryComponent component;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        changeHistoryRepository = mock(ChangeHistoryRepository.class);
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkflowEngineClient workflowEngineClient = mock(WorkflowEngineClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        UserPortalAuditEnricher auditEnricher = mock(UserPortalAuditEnricher.class);

        when(changeHistoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(workflowEngineClient.getTaskHistory(anyString())).thenReturn(Optional.empty());
        when(auditEnricher.resolveStageNamesFromDb(any())).thenReturn(Map.of());
        when(processInstanceRepository.findById(PROCESS_ID)).thenReturn(Optional.of(
                ProcessInstance.builder().id(PROCESS_ID).processDefinitionKey(FU_CODE).build()));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(FU_CODE)))
                .thenReturn(List.of(PROCESS_FORM_JSON));

        component = new ChangeHistoryComponent(
                changeHistoryRepository,
                processInstanceRepository,
                userRepository,
                workflowEngineClient,
                jdbcTemplate,
                new ObjectMapper(),
                auditEnricher,
                mock(UserPortalAuditProcessInstanceMatcher.class),
                PortalTransactionTestSupport.noopPlatformTransactionManager());
    }

    @Test
    void recordFieldChangesSkipsLookupObjectVersusUnchangedDisplayScalar() {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("urge_status", urgeNormal());
        oldValues.put("case_status", caseStatusOpen());
        oldValues.put("case_stage", caseStageSubmission());
        oldValues.put("card_number", 1);

        Map<String, Object> newValues = Map.of(
                "urge_status", "Normal",
                "case_status", "Open",
                "case_stage", "Case Submission",
                "card_number", "1");

        component.recordFieldChanges(context(), oldValues, newValues);

        verify(changeHistoryRepository, never()).saveAll(anyList());
    }

    @Test
    void recordFieldChangesKeepsEmptyToDisplayAndRealLookupChange() {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("case_status", "");
        oldValues.put("case_stage", caseStageSubmission());

        Map<String, Object> newValues = Map.of(
                "case_status", "Open",
                "case_stage", "Investigation");

        component.recordFieldChanges(context(), oldValues, newValues);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChangeHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(changeHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ChangeHistory::getFieldName)
                .containsExactlyInAnyOrder("case_status", "case_stage");
    }

    @Test
    void getChangeHistoryHidesStoredLookupJsonVersusSameDisplayScalar() {
        Instant now = Instant.parse("2026-09-08T06:00:00Z");
        String statusJson = """
                {"id":"hmdc-st-cs-open","status_name":"Open","enabled":true}
                """.trim();
        when(changeHistoryRepository.findByProcessInstanceIdOrderByTimestampAsc(PROCESS_ID))
                .thenReturn(List.of(
                        history(1L, "case_status", null, "Open", now),
                        history(2L, "case_status", statusJson, "Open", now.plusSeconds(1)),
                        history(3L, "case_status", statusJson, "Closed", now.plusSeconds(2))));

        List<ChangeHistoryRecord> records = component.getChangeHistory(PROCESS_ID);

        assertThat(records).extracting(ChangeHistoryRecord::getNewValue)
                .containsExactly("Open", "Closed");
        assertThat(records.get(0).getOldValue()).isNull();
        assertThat(records.get(1).getNewValue()).isEqualTo("Closed");
    }

    private static ChangeHistoryContext context() {
        return ChangeHistoryContext.builder()
                .processInstanceId(PROCESS_ID)
                .taskInstanceId("task-1")
                .stageId("Activity_assignment")
                .userId("user-1")
                .build();
    }

    private static ChangeHistory history(long id, String fieldName, String oldValue, String newValue,
            Instant timestamp) {
        return ChangeHistory.builder()
                .id(id)
                .processInstanceId(PROCESS_ID)
                .userId("user-1")
                .timestamp(timestamp)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changeType(ChangeType.FIELD_UPDATE)
                .build();
    }

    private static Map<String, Object> urgeNormal() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", "hmdc-dd-urge-normal");
        value.put("enabled", true);
        value.put("dropdown_name", "Normal");
        value.put("dropdown_category", "Urge Type");
        return value;
    }

    private static Map<String, Object> caseStatusOpen() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", "hmdc-st-cs-open");
        value.put("enabled", true);
        value.put("status_name", "Open");
        return value;
    }

    private static Map<String, Object> caseStageSubmission() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", "Case Submission");
        value.put("stage_name", "Case Submission");
        value.put("stage_code", "CS");
        return value;
    }
}

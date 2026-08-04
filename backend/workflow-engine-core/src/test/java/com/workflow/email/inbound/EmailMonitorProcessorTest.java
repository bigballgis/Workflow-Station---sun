package com.workflow.email.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.inbound.entity.ProcessedEmailMessage;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.ProcessedEmailMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailMonitorProcessor}: review gate on missing required fields,
 * happy-path START_PROCESS with mapped variables, and idempotency on duplicate messageId.
 */
class EmailMonitorProcessorTest {

    private ProcessEngineComponent processEngineComponent;
    private ProcessedEmailMessageRepository processedRepository;
    private EmailMonitorPortalSyncComponent portalSyncComponent;
    private EmailMonitorProcessor processor;

    @BeforeEach
    void setUp() {
        processEngineComponent = mock(ProcessEngineComponent.class);
        processedRepository = mock(ProcessedEmailMessageRepository.class);
        portalSyncComponent = mock(EmailMonitorPortalSyncComponent.class);
        processor = new EmailMonitorProcessor(
                processEngineComponent, processedRepository, portalSyncComponent, new ObjectMapper());
    }

    private SysEmailMonitorRule rule(Map<String, Object> extractionRules) {
        SysEmailMonitorRule rule = new SysEmailMonitorRule();
        rule.setId("rule-1");
        rule.setActionType("START_PROCESS");
        rule.setProcessDefinitionKey("case_process");
        rule.setSystemInitiatorUserId("system");
        rule.setFunctionUnitId("fu-1");
        rule.setReviewOnMissing(true);
        rule.setExtractionRules(extractionRules);
        return rule;
    }

    private Map<String, Object> labelRule(String target, String label, boolean required) {
        return Map.of("fields", List.of(Map.of(
                "target", target, "source", "TEXT", "type", "LABEL",
                "label", label, "required", required)));
    }

    @Test
    void missingRequiredRoutesToReviewAndDoesNotStartProcess() {
        SysEmailMonitorRule rule = rule(labelRule("case_number", "Case No: ", true));
        EmailMessage email = new EmailMessage("m1", "s", "a@b.com", "nothing here", null, Map.of());

        String status = processor.process(rule, email);

        assertThat(status).isEqualTo(ProcessedEmailMessage.STATUS_REVIEW);
        verify(processEngineComponent, never()).startProcess(any());
        verify(processedRepository).save(any());
    }

    @Test
    void startsProcessWithExtractedVariables() {
        SysEmailMonitorRule rule = rule(labelRule("case_number", "Case No: ", true));
        EmailMessage email = new EmailMessage("m2", "s", "a@b.com", "Case No: ABC-7", null, Map.of());

        when(processEngineComponent.startProcess(any()))
                .thenReturn(ProcessInstanceResult.builder()
                        .processInstanceId("pi-9").success(true).build());

        String status = processor.process(rule, email);

        assertThat(status).isEqualTo(ProcessedEmailMessage.STATUS_STARTED);

        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processEngineComponent).startProcess(captor.capture());
        StartProcessRequest sent = captor.getValue();
        assertThat(sent.getProcessDefinitionKey()).isEqualTo("case_process");
        assertThat(sent.getBusinessKey()).isEqualTo("email:m2");
        assertThat(sent.getVariables()).containsEntry("case_number", "ABC-7");
        assertThat(sent.getVariables()).containsEntry("initiator", "system");
        assertThat(sent.getVariables()).containsKey("__inboundEmail__");
        verify(portalSyncComponent).hydratePortalProcessInstanceAsync(org.mockito.ArgumentMatchers.eq("pi-9"), any());
    }

    @Test
    void alreadyProcessedMessageIsSkipped() {
        SysEmailMonitorRule rule = rule(labelRule("case_number", "Case No: ", true));
        EmailMessage email = new EmailMessage("m3", "s", "a@b.com", "Case No: X", null, Map.of());
        when(processedRepository.existsByRuleUidAndMessageId("rule-1", "m3")).thenReturn(true);

        String status = processor.process(rule, email);

        assertThat(status).isNull();
        verify(processEngineComponent, never()).startProcess(any());
        verify(processedRepository, never()).save(any());
    }
}

package com.workflow.email.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.email.extract.EmailExtractionSpec;
import com.workflow.email.extract.EmailFieldExtractor;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.extract.ExtractionResult;
import com.workflow.email.inbound.entity.ProcessedEmailMessage;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.ProcessedEmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes a single inbound email against a monitor rule: runs the no-code extraction,
 * applies the missing-required review gate, and either starts a process (writing main fields +
 * sub-table rows as variables) or records the email for manual review. Idempotent per
 * {@code (ruleUid, messageId)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailMonitorProcessor {

    private static final String ACTION_START_PROCESS = "START_PROCESS";

    private final ProcessEngineComponent processEngineComponent;
    private final ProcessedEmailMessageRepository processedRepository;
    private final EmailMonitorPortalSyncComponent portalSyncComponent;
    private final ObjectMapper objectMapper;

    /** Returns the recorded status, or {@code null} when the email was skipped as already processed. */
    @Transactional
    public String process(SysEmailMonitorRule rule, EmailMessage email) {
        if (!StringUtils.hasText(email.messageId())) {
            log.warn("Inbound email without messageId for rule {}; skipping", rule.getId());
            return null;
        }
        if (processedRepository.existsByRuleUidAndMessageId(rule.getId(), email.messageId())) {
            return null;
        }
        try {
            return processNew(rule, email);
        } catch (DataIntegrityViolationException race) {
            log.info("Email {} already claimed by another instance for rule {}", email.messageId(), rule.getId());
            return null;
        }
    }

    private String processNew(SysEmailMonitorRule rule, EmailMessage email) {
        EmailExtractionSpec spec = parseSpec(rule);
        if (spec == null) {
            return record(rule, email, ProcessedEmailMessage.STATUS_FAILED, null, "No extraction rules configured");
        }

        ExtractionResult extraction = EmailFieldExtractor.extract(email, spec);

        boolean review = extraction.hasMissingRequired()
                && (rule.getReviewOnMissing() == null || Boolean.TRUE.equals(rule.getReviewOnMissing()));
        if (review) {
            return record(rule, email, ProcessedEmailMessage.STATUS_REVIEW, null,
                    "Missing required fields: " + extraction.getMissingRequired());
        }

        if (!ACTION_START_PROCESS.equalsIgnoreCase(rule.getActionType())
                || !StringUtils.hasText(rule.getProcessDefinitionKey())) {
            return record(rule, email, ProcessedEmailMessage.STATUS_FAILED, null,
                    "Unsupported action or missing process key");
        }

        ProcessInstanceResult result = startProcess(rule, email, extraction);
        if (result == null || !result.isSuccess()) {
            String msg = result != null ? result.getMessage() : "startProcess returned null";
            return record(rule, email, ProcessedEmailMessage.STATUS_FAILED, null, msg);
        }
        portalSyncComponent.hydratePortalProcessInstanceAsync(result.getProcessInstanceId());
        return record(rule, email, ProcessedEmailMessage.STATUS_STARTED, result.getProcessInstanceId(), null);
    }

    private ProcessInstanceResult startProcess(SysEmailMonitorRule rule, EmailMessage email, ExtractionResult extraction) {
        Map<String, Object> variables = new HashMap<>(extraction.getFields());
        if (StringUtils.hasText(rule.getSystemInitiatorUserId())) {
            variables.put("initiator", rule.getSystemInitiatorUserId());
        }
        if (StringUtils.hasText(rule.getFunctionUnitId())) {
            variables.put("functionUnitId", rule.getFunctionUnitId());
        }
        if (StringUtils.hasText(rule.getProcessDefinitionKey())) {
            variables.put("processDefinitionKey", rule.getProcessDefinitionKey());
        }
        variables.put("__inboundEmail__", inboundEmailSnapshot(email));
        if (!extraction.getSubTables().isEmpty()) {
            variables.put("__subTables__", extraction.getSubTables());
        }

        StartProcessRequest request = new StartProcessRequest();
        request.setProcessDefinitionKey(rule.getProcessDefinitionKey());
        request.setBusinessKey("email:" + email.messageId());
        request.setStartUserId(rule.getSystemInitiatorUserId());
        request.setVariables(variables);
        return processEngineComponent.startProcess(request);
    }

    private Map<String, Object> inboundEmailSnapshot(EmailMessage email) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("messageId", email.messageId());
        snapshot.put("subject", email.subject());
        snapshot.put("from", email.from());
        snapshot.put("text", email.text());
        if (StringUtils.hasText(email.html())) {
            snapshot.put("html", email.html());
        }
        return snapshot;
    }

    private EmailExtractionSpec parseSpec(SysEmailMonitorRule rule) {
        Map<String, Object> rules = rule.getExtractionRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(rules, EmailExtractionSpec.class);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid extractionRules for rule {}: {}", rule.getId(), e.getMessage());
            return null;
        }
    }

    private String record(SysEmailMonitorRule rule, EmailMessage email,
                          String status, String processInstanceId, String error) {
        ProcessedEmailMessage row = new ProcessedEmailMessage();
        row.setRuleUid(rule.getId());
        row.setMessageId(email.messageId());
        row.setProcessInstanceId(processInstanceId);
        row.setStatus(status);
        row.setErrorMessage(truncate(error));
        row.setProcessedAt(Instant.now());
        processedRepository.save(row);
        return status;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}

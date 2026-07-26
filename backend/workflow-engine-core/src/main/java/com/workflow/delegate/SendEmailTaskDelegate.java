package com.workflow.delegate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.service.EmailSendOptions;
import com.workflow.service.EmailSenderService;
import com.platform.common.mail.MailDiagnostics;
import com.workflow.util.BpmnExtensionUtils;
import com.workflow.util.EmailTemplateResolver;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("sendEmailTaskDelegate")
@RequiredArgsConstructor
public class SendEmailTaskDelegate implements JavaDelegate {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RepositoryService repositoryService;
    private final AdminCenterClient adminCenterClient;
    private final EmailSenderService emailSenderService;
    private final I18nService i18nService;

    @Override
    public void execute(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        log.info("SendEmailTaskDelegate executing for activity {} in process {}",
                activityId, execution.getProcessInstanceId());

        FlowElement flowElement = getFlowElement(execution);
        if (flowElement == null) {
            throw new BpmnError("EMAIL_SEND_FAILED",
                    i18nService.getMessage("email.send_task.flow_element_unresolved", activityId));
        }

        String connectionId = BpmnExtensionUtils.getExtensionProperty(flowElement, "connectionId");
        String emailTo = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailTo");
        String emailCc = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailCc");
        String emailBcc = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailBcc");
        String emailReplyTo = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailReplyTo");
        String emailImportance = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailImportance");
        String emailSensitivity = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailSensitivity");
        String emailFrom = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailFrom");
        String emailFromName = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailFromName");
        String emailAttachmentsJson = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailAttachments");
        String emailSubject = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailSubject");
        String emailBody = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailBody");

        if (!StringUtils.hasText(connectionId)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_connection"));
        }
        if (!StringUtils.hasText(emailTo)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_recipient"));
        }
        if (!StringUtils.hasText(emailFrom)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_from"));
        }
        if (!StringUtils.hasText(emailSubject)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_subject"));
        }

        Map<String, Object> variables = new HashMap<>(execution.getVariables());
        String resolvedTo = resolveRecipients(BpmnExtensionUtils.resolveExpression(emailTo, variables));
        String resolvedCc = emailCc != null ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailCc, variables)) : null;
        String resolvedBcc = emailBcc != null ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailBcc, variables)) : null;
        String resolvedReplyTo = emailReplyTo != null
                ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailReplyTo, variables)) : null;
        String resolvedFrom = BpmnExtensionUtils.resolveExpression(emailFrom, variables);
        String resolvedFromName = emailFromName != null
                ? BpmnExtensionUtils.resolveExpression(emailFromName, variables) : null;
        String resolvedSubject = EmailTemplateResolver.resolve(emailSubject, variables);
        String resolvedBody = EmailTemplateResolver.resolve(
                emailBody != null ? emailBody : "", variables);
        List<EmailSendOptions.EmailAttachmentPart> attachments =
                parseAttachments(emailAttachmentsJson, variables);

        String functionUnitId = resolveFunctionUnitId(variables);
        if (!StringUtils.hasText(functionUnitId)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_function_unit_id"));
        }

        Optional<Map<String, Object>> credentialsOpt =
                adminCenterClient.getEmailConnectionCredentials(functionUnitId, connectionId);
        if (credentialsOpt.isEmpty()) {
            throw new BpmnError("EMAIL_CONNECTION_NOT_FOUND",
                    i18nService.getMessage("email.send_task.connection_not_found",
                            functionUnitId, connectionId));
        }

        try {
            EmailSendOptions options = new EmailSendOptions(
                    resolvedTo,
                    resolvedCc,
                    resolvedBcc,
                    resolvedSubject,
                    resolvedBody,
                    resolvedReplyTo,
                    emailImportance != null ? emailImportance : "normal",
                    emailSensitivity != null ? emailSensitivity : "normal",
                    attachments,
                    resolvedFrom,
                    resolvedFromName
            );
            log.info("[SEND-EMAIL-TASK] activity={} connectionId={} functionUnitId={} to={} cc={} bcc={} from={}",
                    activityId, connectionId, functionUnitId,
                    com.platform.common.util.StringUtils.maskEmail(resolvedTo),
                    com.platform.common.util.StringUtils.maskEmail(resolvedCc),
                    com.platform.common.util.StringUtils.maskEmail(resolvedBcc),
                    com.platform.common.util.StringUtils.maskEmail(resolvedFrom));
            emailSenderService.send(credentialsOpt.get(), options);
            execution.setVariable("emailSendResult", Map.of(
                    "success", true,
                    "activityId", activityId,
                    "to", resolvedTo
            ));
        } catch (Exception e) {
            String causeChain = MailDiagnostics.causeChain(e);
            String rootCause = MailDiagnostics.rootCause(e);
            log.error("[SEND-EMAIL-TASK] FAILED activity={} connectionId={} functionUnitId={} to={} | causeChain={} | rootCause={}",
                    activityId, connectionId, functionUnitId, resolvedTo, causeChain, rootCause, e);
            execution.setVariable("emailSendResult", Map.of(
                    "success", false,
                    "activityId", activityId,
                    "error", rootCause,
                    "causeChain", causeChain
            ));
            throw new BpmnError("EMAIL_SEND_FAILED",
                    i18nService.getMessage("email.send_task.send_failed", rootCause));
        }
    }

    private FlowElement getFlowElement(DelegateExecution execution) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }
        return bpmnModel.getMainProcess().getFlowElement(execution.getCurrentActivityId());
    }

    private String resolveFunctionUnitId(Map<String, Object> variables) {
        Object functionUnitId = variables.get("functionUnitId");
        if (functionUnitId != null && StringUtils.hasText(functionUnitId.toString())) {
            return functionUnitId.toString();
        }
        Object functionUnitCode = variables.get("functionUnitCode");
        if (functionUnitCode != null && StringUtils.hasText(functionUnitCode.toString())) {
            return adminCenterClient.resolveFunctionUnitIdByCode(functionUnitCode.toString()).orElse(null);
        }
        return null;
    }

    private String resolveRecipients(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String[] parts = raw.split("[;,]");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String resolved = resolveRecipient(trimmed);
            if (!StringUtils.hasText(resolved)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(resolved);
        }
        return out.toString();
    }

    private String resolveRecipient(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.contains("@")) {
            return value;
        }
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(value);
            if (userInfo != null && userInfo.get("email") != null) {
                return userInfo.get("email").toString();
            }
        } catch (com.workflow.exception.AdminCenterUnavailableException e) {
            // FALLBACK(external): 收件人解析故障时按原值继续（后续发送可能失败并走邮件重试），
            // 不能让 admin-center 抖动打断整个流程节点执行。
            log.warn("admin-center unavailable resolving email recipient {}: {}", value, e.getMessage());
        }
        return value;
    }

    private List<EmailSendOptions.EmailAttachmentPart> parseAttachments(
            String rawJson, Map<String, Object> variables) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            String resolved = BpmnExtensionUtils.resolveExpression(rawJson, variables);
            List<Map<String, Object>> items = OBJECT_MAPPER.readValue(resolved, new TypeReference<>() {});
            return items.stream()
                    .map(item -> new EmailSendOptions.EmailAttachmentPart(
                            item.get("name") != null ? item.get("name").toString() : "",
                            item.get("content") != null
                                    ? BpmnExtensionUtils.resolveExpression(item.get("content").toString(), variables)
                                    : ""))
                    .filter(part -> StringUtils.hasText(part.name()) && StringUtils.hasText(part.content()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse email attachments: {}", e.getMessage());
            return List.of();
        }
    }
}

package com.workflow.delegate;

import com.workflow.client.AdminCenterClient;
import com.workflow.client.DeveloperWorkstationEmailTemplateClient;
import com.workflow.service.EmailAttachmentResolver;
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

    private final RepositoryService repositoryService;
    private final AdminCenterClient adminCenterClient;
    private final DeveloperWorkstationEmailTemplateClient emailTemplateClient;
    private final EmailAttachmentResolver emailAttachmentResolver;
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
        String emailTemplateId = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailTemplateId");

        if (!StringUtils.hasText(connectionId)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_connection"));
        }
        if (!StringUtils.hasText(emailTo)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_recipient"));
        }

        Map<String, Object> variables = new HashMap<>(execution.getVariables());
        // Admin-center catalog id (UUID) for connection credentials; DW uses Long id / code for templates.
        String functionUnitId = resolveFunctionUnitId(variables);
        if (!StringUtils.hasText(functionUnitId)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_function_unit_id"));
        }
        String dwFunctionUnitRef = resolveDwFunctionUnitRef(variables);
        if (!StringUtils.hasText(dwFunctionUnitRef)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_function_unit_id"));
        }

        ResolvedContent content = resolveEmailContent(flowElement, dwFunctionUnitRef, emailTemplateId, variables);
        String resolvedTo = resolveRecipients(BpmnExtensionUtils.resolveExpression(emailTo, variables));
        String resolvedCc = emailCc != null ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailCc, variables)) : null;
        String resolvedBcc = emailBcc != null ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailBcc, variables)) : null;
        String resolvedReplyTo = emailReplyTo != null
                ? resolveRecipients(BpmnExtensionUtils.resolveExpression(emailReplyTo, variables)) : null;
        // Optional override; EmailSenderService falls back to connection fromEmail when blank.
        String resolvedFrom = StringUtils.hasText(emailFrom)
                ? BpmnExtensionUtils.resolveExpression(emailFrom, variables) : null;
        String resolvedFromName = emailFromName != null
                ? BpmnExtensionUtils.resolveExpression(emailFromName, variables) : null;
        List<EmailSendOptions.EmailAttachmentPart> attachments =
                emailAttachmentResolver.resolve(emailAttachmentsJson, variables);

        Optional<Map<String, Object>> credentialsOpt;
        try {
            credentialsOpt = adminCenterClient.getEmailConnectionCredentials(functionUnitId, connectionId);
        } catch (IllegalStateException ex) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.system_smtp_required",
                            ex.getMessage() != null ? ex.getMessage() : ""));
        }
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
                    content.subject(),
                    content.body(),
                    resolvedReplyTo,
                    emailImportance != null ? emailImportance : "normal",
                    emailSensitivity != null ? emailSensitivity : "normal",
                    attachments,
                    resolvedFrom,
                    resolvedFromName
            );
            log.info("[SEND-EMAIL-TASK] activity={} connectionId={} functionUnitId={} templateId={} to={} cc={} bcc={} from={}",
                    activityId, connectionId, functionUnitId, emailTemplateId,
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
        } catch (BpmnError bpmnError) {
            throw bpmnError;
        } catch (Exception e) {
            String causeChain = MailDiagnostics.causeChain(e);
            String rootCause = MailDiagnostics.rootCause(e);
            log.error("[SEND-EMAIL-TASK] FAILED activity={} connectionId={} functionUnitId={} to={} | causeChain={} | rootCause={}",
                    activityId, connectionId, functionUnitId,
                    com.platform.common.util.StringUtils.maskEmail(resolvedTo),
                    causeChain, rootCause, e);
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

    /**
     * Prefer live Email Template content; legacy BPMN emailSubject/emailBody kept for already-deployed flows.
     */
    private ResolvedContent resolveEmailContent(
            FlowElement flowElement,
            String functionUnitId,
            String emailTemplateId,
            Map<String, Object> variables) {
        if (StringUtils.hasText(emailTemplateId)) {
            Optional<DeveloperWorkstationEmailTemplateClient.EmailTemplateContent> templateOpt =
                    emailTemplateClient.getTemplate(functionUnitId, emailTemplateId);
            if (templateOpt.isEmpty()) {
                throw new BpmnError("EMAIL_CONFIG_INVALID",
                        i18nService.getMessage("email.send_task.template_not_found",
                                functionUnitId, emailTemplateId));
            }
            DeveloperWorkstationEmailTemplateClient.EmailTemplateContent tpl = templateOpt.get();
            String subject = EmailTemplateResolver.resolve(
                    tpl.subject() != null ? tpl.subject() : "", variables);
            String body = EmailTemplateResolver.resolve(
                    tpl.bodyHtml() != null ? tpl.bodyHtml() : "", variables);
            if (!StringUtils.hasText(subject)) {
                throw new BpmnError("EMAIL_CONFIG_INVALID",
                        i18nService.getMessage("email.send_task.missing_subject"));
            }
            return new ResolvedContent(subject, body);
        }

        // FALLBACK(migration): pre-template-required deployments may still carry inline subject/body.
        String emailSubject = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailSubject");
        String emailBody = BpmnExtensionUtils.getExtensionProperty(flowElement, "emailBody");
        if (!StringUtils.hasText(emailSubject)) {
            throw new BpmnError("EMAIL_CONFIG_INVALID",
                    i18nService.getMessage("email.send_task.missing_template"));
        }
        return new ResolvedContent(
                EmailTemplateResolver.resolve(emailSubject, variables),
                EmailTemplateResolver.resolve(emailBody != null ? emailBody : "", variables));
    }

    private record ResolvedContent(String subject, String body) {}

    private FlowElement getFlowElement(DelegateExecution execution) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }
        // Nested Send Tasks (e.g. inside Multi-Instance SubProcess) are not on the main process.
        return bpmnModel.getFlowElement(execution.getCurrentActivityId());
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

    /**
     * DW email-template API expects a Long id or function-unit {@code code}.
     * Process variables usually carry Admin-center UUID in {@code functionUnitId}, so prefer code.
     */
    private static String resolveDwFunctionUnitRef(Map<String, Object> variables) {
        Object functionUnitCode = variables.get("functionUnitCode");
        if (functionUnitCode != null && StringUtils.hasText(functionUnitCode.toString())) {
            return functionUnitCode.toString().trim();
        }
        Object functionUnitId = variables.get("functionUnitId");
        if (functionUnitId != null) {
            String raw = functionUnitId.toString().trim();
            if (!raw.isEmpty() && raw.chars().allMatch(Character::isDigit)) {
                return raw;
            }
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
}

package com.workflow.service;

import com.platform.common.i18n.I18nService;
import com.platform.common.mail.MailDiagnostics;
import com.workflow.client.AdminCenterClient;
import com.workflow.client.DeveloperWorkstationEmailTemplateClient;
import com.workflow.util.BpmnExtensionUtils;
import com.workflow.util.EmailTemplateResolver;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.platform.common.util.StringUtils.maskEmail;

/**
 * Sends one email described by Send-Task style properties ({@code connectionId}, {@code emailTo},
 * {@code emailTemplateId}, ...) against a process-variable map.
 *
 * <p>Shared by the BPMN Send Task ({@code SendEmailTaskDelegate}) and post-action emails configured
 * on Developer Workstation Actions, so recipient / template / connection resolution has a single
 * implementation. The variable map must carry {@code functionUnitId} and/or {@code functionUnitCode}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfiguredEmailSender {

    public static final String CONNECTION_ID = "connectionId";
    public static final String EMAIL_TO = "emailTo";
    public static final String EMAIL_CC = "emailCc";
    public static final String EMAIL_BCC = "emailBcc";
    public static final String EMAIL_REPLY_TO = "emailReplyTo";
    public static final String EMAIL_IMPORTANCE = "emailImportance";
    public static final String EMAIL_SENSITIVITY = "emailSensitivity";
    public static final String EMAIL_FROM = "emailFrom";
    public static final String EMAIL_FROM_NAME = "emailFromName";
    public static final String EMAIL_ATTACHMENTS = "emailAttachments";
    public static final String EMAIL_TEMPLATE_ID = "emailTemplateId";
    public static final String EMAIL_SUBJECT = "emailSubject";
    public static final String EMAIL_BODY = "emailBody";

    /** Every property key this sender reads, in BPMN extension-property naming. */
    public static final List<String> CONFIG_KEYS = List.of(
            CONNECTION_ID, EMAIL_TO, EMAIL_CC, EMAIL_BCC, EMAIL_REPLY_TO, EMAIL_IMPORTANCE,
            EMAIL_SENSITIVITY, EMAIL_FROM, EMAIL_FROM_NAME, EMAIL_ATTACHMENTS, EMAIL_TEMPLATE_ID,
            EMAIL_SUBJECT, EMAIL_BODY);

    private final AdminCenterClient adminCenterClient;
    private final DeveloperWorkstationEmailTemplateClient emailTemplateClient;
    private final EmailAttachmentResolver emailAttachmentResolver;
    private final EmailSenderService emailSenderService;
    private final TextEnvironmentInterpolator textEnvironmentInterpolator;
    private final I18nService i18nService;

    /**
     * Resolve and send.
     *
     * @param logContext short label for logs, e.g. {@code activity=Activity_1}
     * @param config     property values keyed by {@link #CONFIG_KEYS} (absent = not configured)
     * @param variables  process variables used for expressions and template placeholders
     * @return the resolved To address list
     * @throws EmailDeliveryException configuration or delivery failure
     */
    public String send(String logContext, Map<String, String> config, Map<String, Object> variables) {
        String connectionId = config.get(CONNECTION_ID);
        String emailTo = config.get(EMAIL_TO);
        if (!StringUtils.hasText(connectionId)) {
            throw configInvalid("email.send_task.missing_connection");
        }
        if (!StringUtils.hasText(emailTo)) {
            throw configInvalid("email.send_task.missing_recipient");
        }
        // Admin-center catalog id (UUID) for connection credentials; DW uses Long id / code for templates.
        String functionUnitId = resolveFunctionUnitId(variables);
        if (!StringUtils.hasText(functionUnitId)) {
            throw configInvalid("email.send_task.missing_function_unit_id");
        }
        String dwFunctionUnitRef = resolveDwFunctionUnitRef(variables);
        if (!StringUtils.hasText(dwFunctionUnitRef)) {
            throw configInvalid("email.send_task.missing_function_unit_id");
        }
        ResolvedContent content = resolveEmailContent(config, dwFunctionUnitRef, variables);
        EmailSendOptions options = buildOptions(config, content, variables);
        Map<String, Object> credentials = loadCredentials(functionUnitId, connectionId);
        deliver(logContext, connectionId, functionUnitId, config.get(EMAIL_TEMPLATE_ID), credentials, options);
        return options.to();
    }

    private EmailSendOptions buildOptions(Map<String, String> config, ResolvedContent content,
                                          Map<String, Object> variables) {
        String emailFrom = config.get(EMAIL_FROM);
        String emailFromName = config.get(EMAIL_FROM_NAME);
        String importance = config.get(EMAIL_IMPORTANCE);
        String sensitivity = config.get(EMAIL_SENSITIVITY);
        return new EmailSendOptions(
                resolveRecipients(applyTextEnv(BpmnExtensionUtils.resolveExpression(config.get(EMAIL_TO), variables), false)),
                resolveOptionalRecipients(config.get(EMAIL_CC), variables),
                resolveOptionalRecipients(config.get(EMAIL_BCC), variables),
                content.subject(),
                content.body(),
                resolveOptionalRecipients(config.get(EMAIL_REPLY_TO), variables),
                importance != null ? importance : "normal",
                sensitivity != null ? sensitivity : "normal",
                emailAttachmentResolver.resolve(config.get(EMAIL_ATTACHMENTS), variables),
                // Optional override; EmailSenderService falls back to connection fromEmail when blank.
                StringUtils.hasText(emailFrom)
                        ? applyTextEnv(BpmnExtensionUtils.resolveExpression(emailFrom, variables), false)
                        : null,
                emailFromName != null
                        ? applyTextEnv(BpmnExtensionUtils.resolveExpression(emailFromName, variables), false)
                        : null
        );
    }

    private String resolveOptionalRecipients(String raw, Map<String, Object> variables) {
        return raw != null
                ? resolveRecipients(applyTextEnv(BpmnExtensionUtils.resolveExpression(raw, variables), false))
                : null;
    }

    private String applyTextEnv(String raw, boolean htmlEscape) {
        if (raw == null) {
            return null;
        }
        try {
            return textEnvironmentInterpolator.apply(raw, htmlEscape);
        } catch (com.workflow.exception.AdminCenterUnavailableException ex) {
            throw EmailDeliveryException.sendFailed(
                    i18nService.getMessage("email.send_task.env_var_unavailable"),
                    true, ex.getMessage(), null, ex);
        } catch (IllegalStateException ex) {
            throw configInvalid("email.send_task.env_var_unresolved",
                    ex.getMessage() != null ? ex.getMessage() : "");
        }
    }

    private Map<String, Object> loadCredentials(String functionUnitId, String connectionId) {
        Optional<Map<String, Object>> credentialsOpt;
        try {
            credentialsOpt = adminCenterClient.getEmailConnectionCredentials(functionUnitId, connectionId);
        } catch (IllegalStateException ex) {
            throw EmailDeliveryException.permanent(EmailDeliveryException.CONFIG_INVALID,
                    i18nService.getMessage("email.send_task.system_smtp_required",
                            ex.getMessage() != null ? ex.getMessage() : ""));
        }
        if (credentialsOpt.isEmpty()) {
            throw EmailDeliveryException.permanent(EmailDeliveryException.CONNECTION_NOT_FOUND,
                    i18nService.getMessage("email.send_task.connection_not_found", functionUnitId, connectionId));
        }
        return credentialsOpt.get();
    }

    private void deliver(String logContext, String connectionId, String functionUnitId, String templateId,
                         Map<String, Object> credentials, EmailSendOptions options) {
        try {
            log.info("[SEND-EMAIL] {} connectionId={} functionUnitId={} templateId={} to={} cc={} bcc={} from={}",
                    logContext, connectionId, functionUnitId, templateId,
                    maskEmail(options.to()), maskEmail(options.cc()), maskEmail(options.bcc()),
                    maskEmail(options.fromEmail()));
            emailSenderService.send(credentials, options);
        } catch (Exception e) {
            String causeChain = MailDiagnostics.causeChain(e);
            String rootCause = MailDiagnostics.rootCause(e);
            log.error("[SEND-EMAIL] FAILED {} connectionId={} functionUnitId={} to={} | causeChain={} | rootCause={}",
                    logContext, connectionId, functionUnitId, maskEmail(options.to()), causeChain, rootCause, e);
            throw EmailDeliveryException.sendFailed(
                    i18nService.getMessage("email.send_task.send_failed", rootCause),
                    isTransientSendFailure(e), rootCause, causeChain, e);
        }
    }

    /** Bad addresses, rejected recipients and incomplete SMTP settings will fail again unchanged. */
    static boolean isTransientSendFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AddressException || t instanceof SendFailedException
                    || t instanceof IllegalArgumentException) {
                return false;
            }
        }
        return true;
    }

    /**
     * Prefer live Email Template content; legacy BPMN emailSubject/emailBody kept for already-deployed flows.
     */
    private ResolvedContent resolveEmailContent(Map<String, String> config, String functionUnitId,
                                                Map<String, Object> variables) {
        String emailTemplateId = config.get(EMAIL_TEMPLATE_ID);
        if (StringUtils.hasText(emailTemplateId)) {
            Optional<DeveloperWorkstationEmailTemplateClient.EmailTemplateContent> templateOpt =
                    emailTemplateClient.getTemplate(functionUnitId, emailTemplateId);
            if (templateOpt.isEmpty()) {
                throw configInvalid("email.send_task.template_not_found", functionUnitId, emailTemplateId);
            }
            DeveloperWorkstationEmailTemplateClient.EmailTemplateContent tpl = templateOpt.get();
            String subject = applyTextEnv(
                    EmailTemplateResolver.resolve(tpl.subject() != null ? tpl.subject() : "", variables), false);
            String body = applyTextEnv(
                    EmailTemplateResolver.resolveHtml(tpl.bodyHtml() != null ? tpl.bodyHtml() : "", variables), true);
            if (!StringUtils.hasText(subject)) {
                throw configInvalid("email.send_task.missing_subject");
            }
            return new ResolvedContent(subject, body);
        }

        // FALLBACK(migration): pre-template-required deployments may still carry inline subject/body.
        // Remove once no deployed BPMN Send Task lacks emailTemplateId.
        String emailSubject = config.get(EMAIL_SUBJECT);
        String emailBody = config.get(EMAIL_BODY);
        if (!StringUtils.hasText(emailSubject)) {
            throw configInvalid("email.send_task.missing_template");
        }
        return new ResolvedContent(
                applyTextEnv(EmailTemplateResolver.resolve(emailSubject, variables), false),
                applyTextEnv(EmailTemplateResolver.resolveHtml(emailBody != null ? emailBody : "", variables), true));
    }

    private record ResolvedContent(String subject, String body) {}

    private EmailDeliveryException configInvalid(String messageKey, Object... args) {
        return EmailDeliveryException.permanent(EmailDeliveryException.CONFIG_INVALID,
                i18nService.getMessage(messageKey, args));
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
        StringBuilder out = new StringBuilder();
        for (String part : raw.split("[;,]")) {
            String resolved = resolveRecipient(part.trim());
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
        if (!StringUtils.hasText(value) || value.contains("@")) {
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

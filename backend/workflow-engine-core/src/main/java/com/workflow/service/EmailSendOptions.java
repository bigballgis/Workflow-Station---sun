package com.workflow.service;

import java.util.List;

public record EmailSendOptions(
        String to,
        String cc,
        String bcc,
        String subject,
        String body,
        String replyTo,
        String importance,
        String sensitivity,
        List<EmailAttachmentPart> attachments,
        String fromEmail,
        String fromName
) {
    public record EmailAttachmentPart(String name, String content) {}
}

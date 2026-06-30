package com.workflow.email.extract;

import java.util.Map;

/**
 * Normalized inbound email passed to the extraction interpreter.
 *
 * <p>All fields are read-only inputs; {@code text} is the plain-text body and {@code html} the
 * raw HTML body (either may be {@code null} when the source email lacks that part).
 *
 * @param messageId provider message id (Gmail id / Graph id / Message-ID header) for idempotency
 * @param subject   email subject line
 * @param from      sender address (raw header value)
 * @param text      plain-text body, may be {@code null}
 * @param html      HTML body, may be {@code null}
 * @param headers   lower-cased header name -> value (e.g. {@code from}, {@code date})
 */
public record EmailMessage(
        String messageId,
        String subject,
        String from,
        String text,
        String html,
        Map<String, String> headers
) {
}

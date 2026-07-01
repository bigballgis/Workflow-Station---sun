package com.platform.common.mail;

/**
 * Helpers to extract the real reason behind a mail send failure. JavaMail wraps the
 * meaningful error (TLS handshake, PKIX trust, protocol) several levels deep, so the
 * top-level {@code getMessage()} alone is rarely enough to diagnose the problem.
 */
public final class MailDiagnostics {

    private static final int MAX_DEPTH = 20;

    private MailDiagnostics() {
    }

    /** Full cause chain, e.g. {@code MessagingException: ... -> SSLHandshakeException: PKIX ...}. */
    public static String causeChain(Throwable t) {
        if (t == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < MAX_DEPTH) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(describe(cur));
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    /** Deepest (root) cause description — usually the actionable one. */
    public static String rootCause(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur && depth < MAX_DEPTH) {
            cur = cur.getCause();
            depth++;
        }
        return cur == null ? "" : describe(cur);
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        return msg != null ? t.getClass().getSimpleName() + ": " + msg : t.getClass().getSimpleName();
    }
}

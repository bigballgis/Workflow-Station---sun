package com.workflow.email.inbound;

/**
 * IMAP host/port presets per connection provider type (mirrors the SMTP outbound presets).
 * The mailbox provider is connection-determined, so the same {@code connectionType} that
 * picks an SMTP host also picks the IMAP host for inbound polling.
 */
public record ImapProviderPreset(String host, int port, boolean ssl) {

    /** Resolves the IMAP preset for a connection type string; {@code null} when unsupported. */
    public static ImapProviderPreset forType(String connectionType) {
        if (connectionType == null) {
            return null;
        }
        return switch (connectionType.trim().toUpperCase()) {
            case "GMAIL" -> new ImapProviderPreset("imap.gmail.com", 993, true);
            case "OUTLOOK" -> new ImapProviderPreset("outlook.office365.com", 993, true);
            case "YAHOO" -> new ImapProviderPreset("imap.mail.yahoo.com", 993, true);
            case "QQ" -> new ImapProviderPreset("imap.qq.com", 993, true);
            case "NETEASE_163" -> new ImapProviderPreset("imap.163.com", 993, true);
            default -> null;
        };
    }
}

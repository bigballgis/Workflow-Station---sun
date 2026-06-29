package com.developer.util;

import com.developer.enums.ConnectionType;

/**
 * SMTP host/port/TLS presets per email provider type.
 */
public record EmailProviderPreset(String host, int port, boolean useTls) {

    public static EmailProviderPreset forType(ConnectionType type) {
        if (type == null) {
            type = ConnectionType.GMAIL;
        }
        return switch (type) {
            case GMAIL -> new EmailProviderPreset("smtp.gmail.com", 587, true);
            case OUTLOOK -> new EmailProviderPreset("smtp.office365.com", 587, true);
            case YAHOO -> new EmailProviderPreset("smtp.mail.yahoo.com", 587, true);
            case QQ -> new EmailProviderPreset("smtp.qq.com", 587, true);
            case NETEASE_163 -> new EmailProviderPreset("smtp.163.com", 465, true);
            case SMTP -> new EmailProviderPreset("smtp.gmail.com", 587, true);
        };
    }
}

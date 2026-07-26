package com.admin.component;

import com.platform.common.security.SsrfProtection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves global outbound SMTP endpoint from {@code admin_system_configs}.
 */
@Component
@RequiredArgsConstructor
public class SystemSmtpConfigResolver {

    public static final String KEY_HOST = "smtp.host";
    public static final String KEY_PORT = "smtp.port";
    public static final String KEY_USE_TLS = "smtp.useTls";

    private final ConfigManagerComponent configManager;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    public record SystemSmtpEndpoint(String host, int port, boolean useTls) {
    }

    public SystemSmtpEndpoint requireSystemSmtpEndpoint() {
        String host = trimToNull(configManager.getConfigValue(KEY_HOST));
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("System SMTP host is not configured (smtp.host)");
        }
        try {
            SsrfProtection.validateHostname(host, allowedHosts());
        } catch (SsrfProtection.SsrfException ex) {
            throw new IllegalStateException("System SMTP host blocked by SSRF protection: " + ex.getMessage(), ex);
        }

        String portRaw = trimToNull(configManager.getConfigValue(KEY_PORT));
        int port;
        try {
            port = Integer.parseInt(portRaw != null ? portRaw : "");
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("System SMTP port is invalid (smtp.port)", ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("System SMTP port out of range (smtp.port)");
        }

        String tlsRaw = trimToNull(configManager.getConfigValue(KEY_USE_TLS));
        if (tlsRaw == null) {
            throw new IllegalStateException("System SMTP TLS flag is not configured (smtp.useTls)");
        }
        boolean useTls = Boolean.parseBoolean(tlsRaw);
        return new SystemSmtpEndpoint(host, port, useTls);
    }

    public static boolean isOutboundCapable(String direction) {
        if (direction == null || direction.isBlank()) {
            return true;
        }
        String d = direction.trim().toUpperCase();
        return "OUTBOUND".equals(d) || "BOTH".equals(d);
    }

    private Set<String> allowedHosts() {
        if (ssrfAllowedHosts == null || ssrfAllowedHosts.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String h : ssrfAllowedHosts) {
            if (h != null && !h.isBlank()) {
                out.add(h.trim());
            }
        }
        return out;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}

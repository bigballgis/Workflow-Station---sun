package com.admin.component;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves global inbound IMAP endpoint from {@code admin_system_configs}.
 */
@Component
@RequiredArgsConstructor
public class SystemImapConfigResolver {

    public static final String KEY_HOST = "imap.host";
    public static final String KEY_PORT = "imap.port";
    public static final String KEY_USE_SSL = "imap.useSsl";

    private final ConfigManagerComponent configManager;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    public record SystemImapEndpoint(String host, int port, boolean useSsl) {
    }

    public SystemImapEndpoint requireSystemImapEndpoint() {
        String host = trimToNull(configManager.getConfigValue(KEY_HOST));
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("System IMAP host is not configured (imap.host)");
        }
        validateConfiguredMailHost(host);

        String portRaw = trimToNull(configManager.getConfigValue(KEY_PORT));
        int port;
        try {
            port = Integer.parseInt(portRaw != null ? portRaw : "");
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("System IMAP port is invalid (imap.port)", ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("System IMAP port out of range (imap.port)");
        }

        String sslRaw = trimToNull(configManager.getConfigValue(KEY_USE_SSL));
        if (sslRaw == null) {
            throw new IllegalStateException("System IMAP SSL flag is not configured (imap.useSsl)");
        }
        boolean useSsl = Boolean.parseBoolean(sslRaw);
        return new SystemImapEndpoint(host, port, useSsl);
    }

    /**
     * BOTH remains inbound-capable for packages already deployed before the designer
     * dropped that direction. New DW connections cannot be saved as BOTH.
     */
    public static boolean isInboundCapable(String direction) {
        if (direction == null || direction.isBlank()) {
            return false;
        }
        String d = direction.trim().toUpperCase();
        return "INBOUND".equals(d) || "BOTH".equals(d);
    }

    private void validateConfiguredMailHost(String host) {
        SystemMailHostValidator.validate(host, allowedHosts(), "System IMAP");
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

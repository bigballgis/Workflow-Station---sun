package com.admin.component;

import com.platform.common.security.SsrfProtection;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Admin-chosen SMTP/IMAP hosts are operator-configured mail servers (often intranet FQDNs).
 * RFC1918 site-local addresses are allowed after DNS; loopback, link-local and cloud
 * metadata stay blocked even when the hostname is not a blocked literal.
 *
 * <p>{@code extraAllowed} ({@code ssrf.allowed-hosts}) is an operator allowlist: a matching
 * hostname skips DNS in {@link SsrfProtection}. Denied literals (localhost, loopback,
 * metadata) are still rejected first. Do not put untrusted names on that list.
 */
final class SystemMailHostValidator {

    private SystemMailHostValidator() {
    }

    static void validate(String host, Set<String> extraAllowed, String label) {
        if (isDeniedLiteral(host)) {
            throw new IllegalStateException(label + " host is not allowed: " + host);
        }
        try {
            SsrfProtection.validateHostname(host, extraAllowed);
        } catch (SsrfProtection.SsrfException ex) {
            if (!isRfc1918SiteLocal(host)) {
                throw new IllegalStateException(
                        label + " host blocked by SSRF protection: " + ex.getMessage(), ex);
            }
        }
    }

    private static boolean isDeniedLiteral(String host) {
        String normalized = host.trim().toLowerCase();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || "169.254.169.254".equals(normalized);
    }

    private static boolean isRfc1918SiteLocal(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host.trim());
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                return false;
            }
            return addr.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}

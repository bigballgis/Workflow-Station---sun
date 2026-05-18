package com.platform.common.security;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SSRF (Server-Side Request Forgery) protection utility.
 * Validates that URLs point to external/public hosts and block internal/private addresses.
 */
@Slf4j
public final class SsrfProtection {

    /** Blocked hostname patterns (case-insensitive) */
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "[::1]",
            "169.254.169.254"  // cloud metadata endpoints (AWS, GCP, Azure)
    ));

    /** Private IPv4 ranges */
    private static final long[] PRIVATE_RANGES = {
            ipToLong("10.0.0.0"),          ipToLong("10.255.255.255"),      // 10.0.0.0/8
            ipToLong("172.16.0.0"),         ipToLong("172.31.255.255"),      // 172.16.0.0/12
            ipToLong("192.168.0.0"),        ipToLong("192.168.255.255"),     // 192.168.0.0/16
            ipToLong("169.254.0.0"),        ipToLong("169.254.255.255"),     // 169.254.0.0/16 (link-local)
            ipToLong("127.0.0.0"),          ipToLong("127.255.255.255"),     // 127.0.0.0/8 (loopback)
            ipToLong("100.64.0.0"),         ipToLong("100.127.255.255"),     // 100.64.0.0/10 (CGN)
    };

    /** Allowed schemes */
    private static final Set<String> ALLOWED_SCHEMES = new HashSet<>(Arrays.asList("http", "https"));

    /** Optional allowed domains (e.g., N8N SaaS domains). Empty = none allowed beyond basic checks. */
    private static final Set<String> allowedDomains = new HashSet<>();

    private SsrfProtection() {}

    /**
     * Validate a URL for SSRF safety.
     *
     * @param urlString the URL to validate
     * @throws SsrfException if the URL is blocked
     */
    public static void validate(String urlString) {
        validate(urlString, Set.of());
    }

    /**
     * Validate a URL for SSRF safety, allowing configured internal service hostnames
     * (e.g. Docker Compose service names like {@code n8n}) without DNS private-IP checks.
     *
     * @param urlString    the URL to validate
     * @param allowedHosts hostnames permitted to resolve to private addresses (case-insensitive)
     * @throws SsrfException if the URL is blocked
     */
    public static void validate(String urlString, Set<String> allowedHosts) {
        if (urlString == null || urlString.isBlank()) {
            throw new SsrfException("URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(urlString.trim());
        } catch (URISyntaxException e) {
            throw new SsrfException("Invalid URL syntax: " + truncate(urlString));
        }

        // 1. Check scheme
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new SsrfException("URL scheme not allowed: " + (scheme != null ? scheme : "null"));
        }

        // 2. Check host
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfException("URL must have a host");
        }

        String hostLower = host.toLowerCase();

        // 3. Allowlisted internal service hostnames (Docker/K8s DNS names)
        if (allowedHosts != null && allowedHosts.contains(hostLower)) {
            log.debug("SSRF validation passed (allowed host): {}", truncate(host));
            return;
        }

        // 4. Block known-bad hostnames
        if (BLOCKED_HOSTS.contains(hostLower)) {
            log.warn("SSRF blocked — hostname: {}", truncate(host));
            throw new SsrfException("Requests to " + host + " are not allowed");
        }

        // 5. Resolve and check IP
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                log.warn("SSRF blocked — private/reserved address: {} -> {}", truncate(host), addr.getHostAddress());
                throw new SsrfException("Requests to internal addresses are not allowed");
            }

            // Check against private ranges (catches edge cases isSiteLocalAddress misses)
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) {
                long ip = ipToLong(bytes);
                for (int i = 0; i < PRIVATE_RANGES.length; i += 2) {
                    if (ip >= PRIVATE_RANGES[i] && ip <= PRIVATE_RANGES[i + 1]) {
                        log.warn("SSRF blocked — private range: {} -> {}", truncate(host), addr.getHostAddress());
                        throw new SsrfException("Requests to internal addresses are not allowed");
                    }
                }
            }

            // IPv6 loopback
            if (bytes.length == 16 && addr.isLoopbackAddress()) {
                log.warn("SSRF blocked — IPv6 loopback: {}", truncate(host));
                throw new SsrfException("Requests to internal addresses are not allowed");
            }
        } catch (UnknownHostException e) {
            // If DNS resolution fails, the URL can't be reached anyway — allow it
            // (if attacker controls DNS, they can't point to internal IPs without DNS working)
            log.debug("SSRF check: host {} could not be resolved, allowing (DNS failure)", truncate(host));
        }

        log.debug("SSRF validation passed: {}", truncate(urlString));
    }

    private static long ipToLong(String ip) {
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            return ipToLong(bytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP: " + ip, e);
        }
    }

    private static long ipToLong(byte[] bytes) {
        return ((long) (bytes[0] & 0xFF) << 24)
                | ((long) (bytes[1] & 0xFF) << 16)
                | ((long) (bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static String truncate(String s) {
        return s != null && s.length() > 100 ? s.substring(0, 97) + "..." : s;
    }

    /**
     * Exception thrown when SSRF validation fails.
     */
    public static class SsrfException extends RuntimeException {
        public SsrfException(String message) {
            super(message);
        }
    }
}

package com.admin.util;

import jakarta.servlet.http.HttpServletRequest;
/**
 * Resolves the originating client IP from common reverse-proxy headers.
 */
public final class ClientIpResolver {
    private ClientIpResolver() {
    }
    public static String resolve(HttpServletRequest request) {
        String ip = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (isValid(ip)) return ip;
        ip = firstForwardedIp(request.getHeader("X-Original-Forwarded-For"));
        if (isValid(ip)) return ip;
        ip = firstForwardedIp(request.getHeader("Forwarded"));
        if (isValid(ip)) return ip;
        ip = clean(request.getHeader("X-Envoy-External-Address"));
        if (isValid(ip)) return ip;
        ip = clean(request.getHeader("X-Real-IP"));
        if (isValid(ip)) return ip;
        ip = clean(request.getHeader("CF-Connecting-IP"));
        if (isValid(ip)) return ip;
        return request.getRemoteAddr();
    }
    private static String firstForwardedIp(String value) {
        if (!isValid(value)) return null;
        String first = value.split(",")[0].trim();
        // RFC 7239 Forwarded: for=1.2.3.4;proto=https
        if (first.startsWith("for=")) {
            first = first.substring(4).trim();
            int semicolonIdx = first.indexOf(';');
            if (semicolonIdx > -1) {
                first = first.substring(0, semicolonIdx).trim();
            }
        }
        return clean(first);
    }
    private static String clean(String ip) {
        if (ip == null) return null;
        String v = ip.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() > 1) {
            v = v.substring(1, v.length() - 1);
        }
        // RFC 7239 may wrap IPv6 as [::1]
        if (v.startsWith("[") && v.endsWith("]") && v.length() > 1) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
    private static boolean isValid(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }
}
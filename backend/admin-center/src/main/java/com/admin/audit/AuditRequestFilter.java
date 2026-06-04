package com.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * Extracts the current user's identity from each HTTP request
 * and stores it in AuditContextHolder for use by AdminAuditAspect.
 *
 * Sources (in order of priority):
 *   1. X-User-Id header (set by every frontend request)
 *   2. JWT Bearer token (username + subject claim)
 *   3. X-Forwarded-For / RemoteAddr for IP
 */
@Slf4j
@RequiredArgsConstructor
public class AuditRequestFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            AuditContextHolder.AuditContext ctx = new AuditContextHolder.AuditContext();

            // User identity from headers sent by frontend (JWT is often in httpOnly cookie, not Authorization)
            ctx.setUserId(req.getHeader("X-User-Id"));
            ctx.setUserName(req.getHeader("X-Username"));

            // Extract claims from JWT Bearer token
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    String[] parts = auth.substring(7).split("\\.");
                    if (parts.length >= 2) {
                        // Pad to multiple of 4 for Base64 decoding
                        String b64 = parts[1];
                        int mod = b64.length() % 4;
                        if (mod != 0) b64 += "=".repeat(4 - mod);

                        byte[] payload = Base64.getUrlDecoder().decode(b64);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

                        if (ctx.getUserId() == null) {
                            ctx.setUserId((String) claims.get("sub"));
                        }
                        if (ctx.getUserName() == null || ctx.getUserName().isBlank()) {
                            ctx.setUserName((String) claims.get("username"));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse JWT claims for audit context: {}", e.getMessage());
                }
            }

            // Client IP
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                ip = ip.split(",")[0].trim();
            } else {
                ip = req.getRemoteAddr();
            }
            ctx.setIpAddress(ip);
            ctx.setUserAgent(req.getHeader("User-Agent"));
            ctx.setRequestMethod(req.getMethod());
            ctx.setRequestPath(req.getRequestURI());

            AuditContextHolder.set(ctx);
            chain.doFilter(req, res);
        } finally {
            AuditContextHolder.clear();
        }
    }
}

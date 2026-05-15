package com.platform.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Injects X-Trace-Id from Kong Gateway into SLF4J MDC for log correlation.
 * If no trace ID is present in the request, generates a new UUID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = sanitizeTraceIdForResponse(request.getHeader(TRACE_ID_HEADER));
            MDC.put(MDC_TRACE_ID, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /**
     * Only allow safe characters in the reflected response header. Newlines or colons from
     * untrusted clients can corrupt HTTP framing; reverse proxies (e.g. Kong/nginx) may then
     * report "upstream sent invalid header" and return 502 — especially alongside SSE streams.
     */
    static String sanitizeTraceIdForResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = raw.replaceAll("[^a-zA-Z0-9._\\-]", "");
        if (cleaned.isBlank() || cleaned.length() > 128) {
            return UUID.randomUUID().toString();
        }
        return cleaned;
    }
}

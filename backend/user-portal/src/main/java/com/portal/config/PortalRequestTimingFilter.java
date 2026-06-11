package com.portal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TEMP perf instrumentation: logs wall-clock time for portal task/process requests so the
 * task-detail load waterfall can be measured from container logs. Tagged with {@code [PERF]}
 * for easy grep. Remove once detail-page latency is diagnosed.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PortalRequestTimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !(uri.contains("/tasks") || uri.contains("/processes"))) {
            filterChain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("[PERF] {} {} -> {} took {} ms",
                    request.getMethod(), uri, response.getStatus(), elapsedMs);
        }
    }
}

package com.developer.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that silently swallows IllegalStateException caused by SSE response
 * commit on async dispatch. This happens when Spring tries to finalize the
 * HTTP response after the SSE OutputStream was already committed, causing
 * "getOutputStream() has already been called for this response".
 *
 * Must run BEFORE Spring Security's ExceptionTranslationFilter so the error
 * doesn't get translated into a 401/403.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SseErrorSuppressionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("getOutputStream() has already been called")) {
                // Response already committed by SSE streaming — silently ignore
                log.debug("SSE response already committed on async dispatch, ignoring: {}", e.getMessage());
                // Ensure the response is marked as already handled
                if (response instanceof HttpServletResponse httpResp) {
                    if (!httpResp.isCommitted()) {
                        httpResp.setStatus(200);
                    }
                }
                return;
            }
            throw e;
        }
    }
}

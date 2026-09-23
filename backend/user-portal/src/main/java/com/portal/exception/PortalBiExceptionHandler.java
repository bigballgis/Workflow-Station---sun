package com.portal.exception;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PortalBiExceptionHandler {

    @ExceptionHandler(PortalBiUpstreamException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiUpstream(
            PortalBiUpstreamException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }
}

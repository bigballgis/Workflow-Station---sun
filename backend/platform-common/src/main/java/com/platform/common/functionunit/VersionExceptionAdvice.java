package com.platform.common.functionunit;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class VersionExceptionAdvice {
    private final VersionExceptionAudit audit;
    private final MessageSource messages;

    @ExceptionHandler(FunctionUnitVersionException.class)
    public ResponseEntity<ApiResponse<?>> handle(FunctionUnitVersionException error, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        String actor = request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
        audit.record(error, actor, request.getRequestURI(), traceId);
        log.warn("Version routing rejected: reason={} reference={} traceId={}",
                error.getDetails().get("reason"), error.getDetails().get("reference"), traceId);
        var response = ErrorResponse.builder().code(error.getErrorCode().getCode())
                .message(messages.getMessage("function_unit.version.routing_rejected", null, LocaleContextHolder.getLocale()))
                .details(error.getDetails()).traceId(traceId).timestamp(Instant.now()).path(request.getRequestURI()).build();
        return ResponseEntity.status(error.getErrorCode().getHttpStatus()).body(ApiResponse.error(response));
    }
}

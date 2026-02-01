package com.developer.exception;

import com.developer.dto.ErrorResponse;
import com.developer.resilience.CircuitBreaker;
import com.developer.resilience.GracefulDegradationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler that provides specific exception handling
 * instead of generic catching, with proper error context and logging.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler implements ErrorHandler {
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException ex, WebRequest request) {
        logError(ex, "Security exception occurred", Map.of(
            "threatType", ex.getThreatType(),
            "securityContext", ex.getSecurityContext()
        ));
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(getSafeMessage(ex))
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(getHttpStatus(ex)).body(error);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex, WebRequest request) {
        logError(ex, "Validation exception occurred", Map.of(
            "validationErrors", ex.getValidationErrors().size()
        ));
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .details(ex.getErrors())
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogic(BusinessException ex, WebRequest request) {
        logError(ex, "Business logic exception occurred", Map.of(
            "businessRule", ex.getBusinessRule()
        ));
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .suggestion(ex.getSuggestion())
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
    
    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogicException(BusinessLogicException ex, WebRequest request) {
        logError(ex, "Business logic exception occurred", Map.of(
            "businessRule", ex.getBusinessRule()
        ));
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .suggestion(ex.getSuggestion())
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
    
    @ExceptionHandler(com.developer.exception.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(com.developer.exception.DataAccessException ex, WebRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", ex.getOperation());
        if (ex.getEntityType() != null) {
            metadata.put("entityType", ex.getEntityType());
        }
        
        logError(ex, "Data access exception occurred", metadata);
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message("A data access error occurred")
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        logError(ex, "Resource not found", Map.of(
            "resourceType", ex.getResourceType(),
            "resourceId", ex.getResourceId()
        ));
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
                ))
                .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("VAL_INVALID_INPUT")
                .message("Validation failed")
                .details(details)
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("AUTH_INVALID_CREDENTIALS")
                .message("Invalid username or password")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("PERM_ACCESS_DENIED")
                .message("Access denied")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleSpringDataAccess(DataAccessException ex, WebRequest request) {
        log.error("Spring data access exception occurred", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("DATA_ACCESS_ERROR")
                .message("A database error occurred")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(CircuitBreaker.CircuitBreakerOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CircuitBreaker.CircuitBreakerOpenException ex, WebRequest request) {
        log.warn("Circuit breaker is open: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("SYS_SERVICE_UNAVAILABLE")
                .message("Service is temporarily unavailable")
                .suggestion("Please try again in a few moments")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @ExceptionHandler(GracefulDegradationManager.FeatureDisabledException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDisabled(GracefulDegradationManager.FeatureDisabledException ex, WebRequest request) {
        log.warn("Feature is disabled: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("SYS_FEATURE_DISABLED")
                .message("This feature is temporarily disabled")
                .suggestion("Please try again later or use alternative functionality")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("SYS_INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .suggestion("Please try again later or contact support")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @Override
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        if (ex instanceof ApplicationException appEx) {
            return handleApplicationException(appEx, request);
        }
        return handleGenericException(ex, request);
    }
    
    @Override
    public void logError(Exception ex, String context, Map<String, Object> metadata) {
        if (ex instanceof ApplicationException appEx) {
            if (appEx.shouldAlert()) {
                log.error("CRITICAL ERROR - {}: {} | Context: {} | Metadata: {}", 
                    context, ex.getMessage(), appEx.getContext(), metadata, ex);
            } else if (appEx.shouldLog()) {
                log.warn("ERROR - {}: {} | Context: {} | Metadata: {}", 
                    context, ex.getMessage(), appEx.getContext(), metadata);
            } else {
                log.debug("INFO - {}: {} | Context: {} | Metadata: {}", 
                    context, ex.getMessage(), appEx.getContext(), metadata);
            }
        } else {
            log.error("UNEXPECTED ERROR - {}: {} | Metadata: {}", context, ex.getMessage(), metadata, ex);
        }
    }
    
    private ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex, WebRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(getSafeMessage(ex))
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .traceId(UUID.randomUUID().toString())
                .build();
        
        return ResponseEntity.status(getHttpStatus(ex)).body(error);
    }
    
    private String getSafeMessage(ApplicationException ex) {
        // For security exceptions, return generic messages to avoid information disclosure
        if (ex instanceof SecurityException) {
            return "A security error occurred";
        }
        return ex.getMessage();
    }
    
    private HttpStatus getHttpStatus(ApplicationException ex) {
        switch (ex.getCategory()) {
            case VALIDATION:
                return HttpStatus.BAD_REQUEST;
            case SECURITY:
                return ex.getSeverity() == ErrorSeverity.CRITICAL ? 
                    HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
            case BUSINESS_LOGIC:
                return HttpStatus.UNPROCESSABLE_ENTITY;
            case DATA_ACCESS:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case INTEGRATION:
                return HttpStatus.BAD_GATEWAY;
            case CONFIGURATION:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case SYSTEM:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
    
    private String getRequestPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}

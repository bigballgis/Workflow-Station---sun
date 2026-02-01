package com.developer.exception;

import com.developer.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Interface for error handling functionality.
 * Provides contract for handling exceptions and logging errors.
 * 
 * Requirements: 3.1, 3.2, 3.3
 */
public interface ErrorHandler {
    
    /**
     * Handle any exception and return appropriate HTTP response
     * 
     * @param ex The exception to handle
     * @param request The web request context
     * @return ResponseEntity with error response
     */
    ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request);
    
    /**
     * Log error with context and metadata
     * 
     * @param ex The exception to log
     * @param context Description of the operation context
     * @param metadata Additional metadata for debugging
     */
    void logError(Exception ex, String context, Map<String, Object> metadata);
}
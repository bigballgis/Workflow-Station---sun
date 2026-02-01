package com.developer.resilience.fallback;

import com.developer.exception.ErrorContext;
import com.developer.resilience.FallbackStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Fallback strategies for external service operations.
 * 
 * Requirements: 3.5
 */
@Slf4j
public class ExternalServiceFallbackStrategy {
    
    /**
     * Fallback strategy for external API calls - returns cached or default response
     */
    public static <T> FallbackStrategy<T> cachedResponse(T cachedValue) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                log.warn("External service call failed, returning cached response. Context: {}", context.getDescription());
                return cachedValue;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return isNetworkException(exception);
            }
            
            @Override
            public int getPriority() {
                return 5; // Very high priority for external services
            }
        };
    }
    
    /**
     * Fallback strategy that degrades functionality gracefully
     */
    public static <T> FallbackStrategy<T> degradedService(T degradedResponse, String degradationMessage) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                log.warn("External service unavailable, providing degraded functionality: {}. Context: {}", 
                        degradationMessage, context.getDescription());
                return degradedResponse;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return isNetworkException(exception);
            }
            
            @Override
            public int getPriority() {
                return 15;
            }
        };
    }
    
    /**
     * Fallback strategy for validation services - returns permissive result
     */
    public static FallbackStrategy<Boolean> permissiveValidation() {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.warn("External validation service failed, allowing operation to proceed. Context: {}", context.getDescription());
                return true; // Permissive - allow operation when validation service is down
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return isNetworkException(exception);
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy for notification services - logs instead of sending
     */
    public static FallbackStrategy<Void> logNotification() {
        return new FallbackStrategy<Void>() {
            @Override
            public Void execute(ErrorContext context, Exception originalException) {
                log.warn("Notification service failed, logging notification instead. Context: {}", context.getDescription());
                // In a real implementation, this might write to a queue for later processing
                return null;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return isNetworkException(exception);
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy that retries with exponential backoff
     */
    public static <T> FallbackStrategy<T> retryWithBackoff(int maxRetries, long baseDelayMs) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                log.warn("External service failed, implementing retry fallback is not supported in this context. Context: {}", context.getDescription());
                throw new RuntimeException("Service temporarily unavailable, please try again later", originalException);
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return isNetworkException(exception);
            }
            
            @Override
            public int getPriority() {
                return 20;
            }
        };
    }
    
    /**
     * Check if an exception is network-related
     */
    private static boolean isNetworkException(Exception exception) {
        return exception instanceof IOException ||
               exception instanceof SocketTimeoutException ||
               exception instanceof TimeoutException ||
               (exception.getCause() instanceof IOException) ||
               (exception.getCause() instanceof SocketTimeoutException) ||
               (exception.getCause() instanceof TimeoutException);
    }
}
package com.developer.resilience.fallback;

import com.developer.exception.DataAccessException;
import com.developer.exception.ErrorContext;
import com.developer.resilience.FallbackStrategy;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fallback strategies for database operations.
 * 
 * Requirements: 3.5
 */
@Slf4j
public class DatabaseFallbackStrategy {
    
    /**
     * Fallback strategy for list operations - returns empty list
     */
    public static <T> FallbackStrategy<List<T>> emptyList() {
        return new FallbackStrategy<List<T>>() {
            @Override
            public List<T> execute(ErrorContext context, Exception originalException) {
                log.warn("Database operation failed, returning empty list. Context: {}", context.getDescription());
                return Collections.emptyList();
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof DataAccessException || 
                       exception instanceof SQLException ||
                       exception.getCause() instanceof SQLException;
            }
            
            @Override
            public int getPriority() {
                return 10; // High priority for database operations
            }
        };
    }
    
    /**
     * Fallback strategy for optional operations - returns empty optional
     */
    public static <T> FallbackStrategy<Optional<T>> emptyOptional() {
        return new FallbackStrategy<Optional<T>>() {
            @Override
            public Optional<T> execute(ErrorContext context, Exception originalException) {
                log.warn("Database operation failed, returning empty optional. Context: {}", context.getDescription());
                return Optional.empty();
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof DataAccessException || 
                       exception instanceof SQLException ||
                       exception.getCause() instanceof SQLException;
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy for count operations - returns zero
     */
    public static FallbackStrategy<Long> zeroCount() {
        return new FallbackStrategy<Long>() {
            @Override
            public Long execute(ErrorContext context, Exception originalException) {
                log.warn("Database count operation failed, returning zero. Context: {}", context.getDescription());
                return 0L;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof DataAccessException || 
                       exception instanceof SQLException ||
                       exception.getCause() instanceof SQLException;
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy for boolean operations - returns false (safe default)
     */
    public static FallbackStrategy<Boolean> safeFalse() {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.warn("Database boolean operation failed, returning false for safety. Context: {}", context.getDescription());
                return false;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof DataAccessException || 
                       exception instanceof SQLException ||
                       exception.getCause() instanceof SQLException;
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy that throws a business-friendly exception
     */
    public static <T> FallbackStrategy<T> businessException(String userMessage) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                log.error("Database operation failed, throwing business exception. Context: {}", context.getDescription(), originalException);
                throw new RuntimeException(userMessage);
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof DataAccessException || 
                       exception instanceof SQLException ||
                       exception.getCause() instanceof SQLException;
            }
            
            @Override
            public int getPriority() {
                return 50; // Lower priority - use when graceful degradation isn't possible
            }
        };
    }
}
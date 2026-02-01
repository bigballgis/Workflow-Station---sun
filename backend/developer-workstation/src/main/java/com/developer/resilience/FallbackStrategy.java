package com.developer.resilience;

import com.developer.exception.ErrorContext;

import java.util.function.Supplier;

/**
 * Interface for fallback strategies when primary operations fail.
 * 
 * Requirements: 3.5
 */
public interface FallbackStrategy<T> {
    
    /**
     * Execute the fallback operation
     * 
     * @param context Error context from the failed operation
     * @param originalException The exception that triggered the fallback
     * @return Fallback result
     */
    T execute(ErrorContext context, Exception originalException);
    
    /**
     * Check if this fallback strategy can handle the given exception
     * 
     * @param exception The exception to check
     * @return true if this strategy can handle the exception
     */
    boolean canHandle(Exception exception);
    
    /**
     * Get the priority of this fallback strategy (lower number = higher priority)
     * 
     * @return Priority value
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * Create a simple fallback strategy that returns a default value
     */
    static <T> FallbackStrategy<T> defaultValue(T defaultValue) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                return defaultValue;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return true;
            }
            
            @Override
            public int getPriority() {
                return 1000; // Low priority - use as last resort
            }
        };
    }
    
    /**
     * Create a fallback strategy that executes a supplier function
     */
    static <T> FallbackStrategy<T> supplier(Supplier<T> supplier) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                return supplier.get();
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return true;
            }
        };
    }
    
    /**
     * Create a fallback strategy for specific exception types
     */
    static <T> FallbackStrategy<T> forException(Class<? extends Exception> exceptionType, Supplier<T> supplier) {
        return new FallbackStrategy<T>() {
            @Override
            public T execute(ErrorContext context, Exception originalException) {
                return supplier.get();
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exceptionType.isAssignableFrom(exception.getClass());
            }
        };
    }
}
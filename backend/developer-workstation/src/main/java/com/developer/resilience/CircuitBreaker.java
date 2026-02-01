package com.developer.resilience;

import com.developer.exception.ErrorContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker implementation for protecting against cascading failures.
 * 
 * Requirements: 3.5
 */
@Slf4j
public class CircuitBreaker {
    
    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<CircuitBreakerState> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong stateTransitionTime;
    
    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config;
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.stateTransitionTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * Execute a callable with circuit breaker protection
     */
    public <T> T execute(Callable<T> operation, FallbackStrategy<T> fallbackStrategy, ErrorContext context) throws Exception {
        if (!canExecute()) {
            log.debug("Circuit breaker {} is OPEN, executing fallback", name);
            return executeFallback(fallbackStrategy, context, new CircuitBreakerOpenException("Circuit breaker is open"));
        }
        
        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return executeFallback(fallbackStrategy, context, e);
        }
    }
    
    /**
     * Execute a callable with circuit breaker protection (no fallback)
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        if (!canExecute()) {
            throw new CircuitBreakerOpenException("Circuit breaker " + name + " is open");
        }
        
        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            throw e;
        }
    }
    
    /**
     * Check if the circuit breaker allows execution
     */
    public boolean canExecute() {
        CircuitBreakerState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                return shouldAttemptRecovery();
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get current state of the circuit breaker
     */
    public CircuitBreakerState getState() {
        return state.get();
    }
    
    /**
     * Get current failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Get current success count (for HALF_OPEN state)
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Manually reset the circuit breaker to CLOSED state
     */
    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        stateTransitionTime.set(System.currentTimeMillis());
        log.info("Circuit breaker {} manually reset to CLOSED", name);
    }
    
    /**
     * Force the circuit breaker to OPEN state
     */
    public void forceOpen() {
        state.set(CircuitBreakerState.OPEN);
        stateTransitionTime.set(System.currentTimeMillis());
        log.warn("Circuit breaker {} forced to OPEN state", name);
    }
    
    private void onSuccess() {
        CircuitBreakerState currentState = state.get();
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            int currentSuccessCount = successCount.incrementAndGet();
            log.debug("Circuit breaker {} success in HALF_OPEN: {}/{}", name, currentSuccessCount, config.getSuccessThreshold());
            
            if (currentSuccessCount >= config.getSuccessThreshold()) {
                transitionToClosed();
            }
        } else if (currentState == CircuitBreakerState.CLOSED) {
            // Reset failure count on success in CLOSED state
            failureCount.set(0);
        }
    }
    
    private void onFailure(Exception exception) {
        lastFailureTime.set(System.currentTimeMillis());
        CircuitBreakerState currentState = state.get();
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            transitionToOpen();
        } else if (currentState == CircuitBreakerState.CLOSED) {
            int currentFailureCount = failureCount.incrementAndGet();
            log.debug("Circuit breaker {} failure count: {}/{}", name, currentFailureCount, config.getFailureThreshold());
            
            if (currentFailureCount >= config.getFailureThreshold()) {
                if (isWithinFailureWindow()) {
                    transitionToOpen();
                }
            }
        }
        
        log.debug("Circuit breaker {} recorded failure: {}", name, exception.getMessage());
    }
    
    private boolean shouldAttemptRecovery() {
        if (!config.isAutoRecovery()) {
            return false;
        }
        
        long timeSinceTransition = System.currentTimeMillis() - stateTransitionTime.get();
        boolean shouldAttempt = timeSinceTransition >= config.getRecoveryTimeout().toMillis();
        
        if (shouldAttempt) {
            transitionToHalfOpen();
            return true;
        }
        
        return false;
    }
    
    private boolean isWithinFailureWindow() {
        long timeSinceFirstFailure = System.currentTimeMillis() - (lastFailureTime.get() - config.getFailureWindow().toMillis());
        return timeSinceFirstFailure <= config.getFailureWindow().toMillis();
    }
    
    private void transitionToClosed() {
        if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
            failureCount.set(0);
            successCount.set(0);
            stateTransitionTime.set(System.currentTimeMillis());
            log.info("Circuit breaker {} transitioned to CLOSED", name);
        }
    }
    
    private void transitionToOpen() {
        CircuitBreakerState currentState = state.get();
        if (state.compareAndSet(currentState, CircuitBreakerState.OPEN)) {
            successCount.set(0);
            stateTransitionTime.set(System.currentTimeMillis());
            log.warn("Circuit breaker {} transitioned to OPEN", name);
        }
    }
    
    private void transitionToHalfOpen() {
        if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
            successCount.set(0);
            stateTransitionTime.set(System.currentTimeMillis());
            log.info("Circuit breaker {} transitioned to HALF_OPEN", name);
        }
    }
    
    private <T> T executeFallback(FallbackStrategy<T> fallbackStrategy, ErrorContext context, Exception originalException) {
        if (fallbackStrategy != null && fallbackStrategy.canHandle(originalException)) {
            try {
                T result = fallbackStrategy.execute(context, originalException);
                log.debug("Circuit breaker {} executed fallback successfully", name);
                return result;
            } catch (Exception fallbackException) {
                log.error("Circuit breaker {} fallback failed", name, fallbackException);
                throw new RuntimeException("Both primary operation and fallback failed", fallbackException);
            }
        } else {
            log.error("Circuit breaker {} has no suitable fallback strategy", name);
            throw new RuntimeException("No fallback available for failed operation", originalException);
        }
    }
    
    /**
     * Exception thrown when circuit breaker is in OPEN state
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
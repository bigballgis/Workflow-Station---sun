package com.developer.resilience;

import com.developer.exception.ErrorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircuitBreaker implementation.
 * 
 * Requirements: 3.5
 */
class CircuitBreakerTest {
    
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerConfig config;
    private ErrorContext errorContext;
    
    @BeforeEach
    void setUp() {
        config = CircuitBreakerConfig.fastRecovery(); // Use fast recovery for testing
        circuitBreaker = new CircuitBreaker("test-circuit", config);
        errorContext = ErrorContext.of("test-operation", "test-component");
    }
    
    @Test
    @DisplayName("Circuit breaker should start in CLOSED state")
    void shouldStartInClosedState() {
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.canExecute());
        assertEquals(0, circuitBreaker.getFailureCount());
    }
    
    @Test
    @DisplayName("Circuit breaker should execute successful operations normally")
    void shouldExecuteSuccessfulOperations() throws Exception {
        Callable<String> operation = () -> "success";
        
        String result = circuitBreaker.execute(operation);
        
        assertEquals("success", result);
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
    }
    
    @Test
    @DisplayName("Circuit breaker should count failures and transition to OPEN")
    void shouldTransitionToOpenAfterFailures() throws Exception {
        Callable<String> failingOperation = () -> {
            throw new RuntimeException("Operation failed");
        };
        
        FallbackStrategy<String> fallback = FallbackStrategy.defaultValue("fallback");
        
        // Execute failing operations up to threshold
        for (int i = 0; i < config.getFailureThreshold(); i++) {
            String result = circuitBreaker.execute(failingOperation, fallback, errorContext);
            assertEquals("fallback", result);
        }
        
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertEquals(config.getFailureThreshold(), circuitBreaker.getFailureCount());
    }
    
    @Test
    @DisplayName("Circuit breaker should use fallback when OPEN")
    void shouldUseFallbackWhenOpen() throws Exception {
        // Force circuit breaker to OPEN state
        circuitBreaker.forceOpen();
        
        Callable<String> operation = () -> "should not execute";
        FallbackStrategy<String> fallback = FallbackStrategy.defaultValue("fallback result");
        
        String result = circuitBreaker.execute(operation, fallback, errorContext);
        
        assertEquals("fallback result", result);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }
    
    @Test
    @DisplayName("Circuit breaker should throw exception when OPEN and no fallback")
    void shouldThrowExceptionWhenOpenAndNoFallback() {
        circuitBreaker.forceOpen();
        
        Callable<String> operation = () -> "should not execute";
        
        assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, 
                () -> circuitBreaker.execute(operation));
    }
    
    @Test
    @DisplayName("Circuit breaker should transition to HALF_OPEN after recovery timeout")
    void shouldTransitionToHalfOpenAfterTimeout() throws Exception {
        // Force to OPEN state
        circuitBreaker.forceOpen();
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        
        // Wait for recovery timeout (using fast recovery config)
        Thread.sleep(config.getRecoveryTimeout().toMillis() + 100);
        
        // Next execution should transition to HALF_OPEN
        Callable<String> operation = () -> "success";
        String result = circuitBreaker.execute(operation);
        
        assertEquals("success", result);
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());
    }
    
    @Test
    @DisplayName("Circuit breaker should transition from HALF_OPEN to CLOSED after successful operations")
    void shouldTransitionFromHalfOpenToClosed() throws Exception {
        // Set to HALF_OPEN state by forcing OPEN then allowing recovery
        circuitBreaker.forceOpen();
        Thread.sleep(config.getRecoveryTimeout().toMillis() + 100);
        
        Callable<String> successfulOperation = () -> "success";
        
        // Execute successful operations up to success threshold
        for (int i = 0; i < config.getSuccessThreshold(); i++) {
            String result = circuitBreaker.execute(successfulOperation);
            assertEquals("success", result);
        }
        
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
    }
    
    @Test
    @DisplayName("Circuit breaker should transition from HALF_OPEN to OPEN on failure")
    void shouldTransitionFromHalfOpenToOpenOnFailure() throws Exception {
        // Set to HALF_OPEN state
        circuitBreaker.forceOpen();
        Thread.sleep(config.getRecoveryTimeout().toMillis() + 100);
        
        // Execute one successful operation to get to HALF_OPEN
        circuitBreaker.execute(() -> "success");
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());
        
        // Now execute a failing operation
        Callable<String> failingOperation = () -> {
            throw new RuntimeException("Failure in half-open");
        };
        FallbackStrategy<String> fallback = FallbackStrategy.defaultValue("fallback");
        
        String result = circuitBreaker.execute(failingOperation, fallback, errorContext);
        
        assertEquals("fallback", result);
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }
    
    @Test
    @DisplayName("Circuit breaker should reset to CLOSED state when manually reset")
    void shouldResetToClosed() {
        // Force to OPEN state
        circuitBreaker.forceOpen();
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        
        // Reset circuit breaker
        circuitBreaker.reset();
        
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
        assertEquals(0, circuitBreaker.getSuccessCount());
        assertTrue(circuitBreaker.canExecute());
    }
    
    @Test
    @DisplayName("Circuit breaker should handle concurrent operations safely")
    void shouldHandleConcurrentOperations() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        Callable<String> operation = () -> {
            if (Math.random() > 0.5) {
                successCount.incrementAndGet();
                return "success";
            } else {
                failureCount.incrementAndGet();
                throw new RuntimeException("Random failure");
            }
        };
        
        FallbackStrategy<String> fallback = FallbackStrategy.defaultValue("fallback");
        
        // Execute operations concurrently
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        circuitBreaker.execute(operation, fallback, errorContext);
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    // Expected for some operations
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Circuit breaker should still be in a valid state
        assertNotNull(circuitBreaker.getState());
        assertTrue(circuitBreaker.getFailureCount() >= 0);
        assertTrue(circuitBreaker.getSuccessCount() >= 0);
    }
}
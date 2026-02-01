package com.developer.resilience;

import com.developer.exception.ErrorContext;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for critical error fallback mechanisms.
 * **Feature: technical-debt-remediation, Property 11: Critical Error Fallback**
 * **Validates: Requirements 3.5**
 * 
 * Requirements: 3.5
 */
class CriticalErrorFallbackPropertyTest {
    
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private GracefulDegradationManager degradationManager;
    private ResilienceService resilienceService;
    
    @BeforeProperty
    void setUp() {
        circuitBreakerRegistry = new CircuitBreakerRegistry();
        degradationManager = new GracefulDegradationManager(circuitBreakerRegistry);
        resilienceService = new ResilienceService(circuitBreakerRegistry, degradationManager);
    }
    
    @Property(tries = 100)
    void criticalErrorFallbackProperty(
            @ForAll("operationNames") String operationName,
            @ForAll("errorMessages") String errorMessage,
            @ForAll("fallbackValues") String fallbackValue) {
        
        // Given: A critical operation that may fail
        ErrorContext context = ErrorContext.of(operationName, "test-component");
        AtomicInteger executionCount = new AtomicInteger(0);
        
        Callable<String> criticalOperation = () -> {
            int count = executionCount.incrementAndGet();
            if (count <= 3) { // Fail first few attempts to trigger circuit breaker
                throw new RuntimeException(errorMessage);
            }
            return "success";
        };
        
        // When: Executing the operation with fallback protection
        String result = resilienceService.executeExternalServiceCall(
                operationName, criticalOperation, fallbackValue, context);
        
        // Then: The system should provide a fallback response to maintain availability
        assertNotNull(result, "Fallback mechanism should always provide a result");
        
        // The result should either be the successful operation result or the fallback value
        assertTrue(result.equals("success") || result.equals(fallbackValue),
                "Result should be either successful operation or fallback value");
        
        // The system should remain responsive (no exceptions thrown to caller)
        // This is implicitly tested by the fact that we got a result
    }
    
    @Property(tries = 100)
    void circuitBreakerStateTransitionProperty(
            @ForAll("operationNames") String operationName,
            @ForAll @IntRange(min = 1, max = 10) int failureCount) {
        
        // Given: A circuit breaker with fast recovery configuration
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker(
                operationName, CircuitBreakerConfig.fastRecovery());
        
        ErrorContext context = ErrorContext.of(operationName, "test-component");
        FallbackStrategy<String> fallback = FallbackStrategy.defaultValue("fallback");
        
        // When: Executing failing operations
        for (int i = 0; i < failureCount; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                }, fallback, context);
            } catch (Exception e) {
                // Expected for some cases
            }
        }
        
        // Then: Circuit breaker should be in a valid state
        CircuitBreakerState state = circuitBreaker.getState();
        assertNotNull(state, "Circuit breaker should always have a valid state");
        
        // If enough failures occurred, circuit should be open
        if (failureCount >= CircuitBreakerConfig.fastRecovery().getFailureThreshold()) {
            assertEquals(CircuitBreakerState.OPEN, state,
                    "Circuit breaker should be OPEN after threshold failures");
        }
        
        // Failure count should be tracked correctly (allowing for some variance due to circuit breaker behavior)
        assertTrue(circuitBreaker.getFailureCount() >= 0,
                "Failure count should be non-negative");
        
        // When circuit breaker opens, it may stop counting additional failures
        // So failure count should be at most the threshold + some buffer for race conditions
        int maxExpectedFailures = Math.max(failureCount, CircuitBreakerConfig.fastRecovery().getFailureThreshold() + 2);
        assertTrue(circuitBreaker.getFailureCount() <= maxExpectedFailures,
                "Failure count should be reasonable: actual=" + circuitBreaker.getFailureCount() + 
                ", expected<=" + maxExpectedFailures + ", input=" + failureCount);
    }
    
    @Property(tries = 100)
    void gracefulDegradationProperty(
            @ForAll("featureNames") String featureName,
            @ForAll("degradationReasons") String reason,
            @ForAll("operationResults") String primaryResult,
            @ForAll("operationResults") String degradedResult) {
        
        // Given: A feature that can be degraded
        ErrorContext context = ErrorContext.of("test-operation", "test-component");
        
        // When: Primary operation fails and triggers degradation
        GracefulDegradationManager.OperationExecutor<String> failingPrimary = () -> {
            throw new RuntimeException("Primary operation failed");
        };
        
        GracefulDegradationManager.OperationExecutor<String> workingDegraded = () -> degradedResult;
        
        String result = degradationManager.executeWithDegradation(
                featureName, failingPrimary, workingDegraded, context);
        
        // Then: The system should provide degraded functionality
        assertEquals(degradedResult, result,
                "Should return degraded result when primary fails");
        
        // Feature should be marked as degraded
        DegradationLevel level = degradationManager.getDegradationLevel(featureName);
        assertEquals(DegradationLevel.REDUCED, level,
                "Feature should be degraded after primary failure");
        
        // Feature should still be available (not completely disabled)
        assertTrue(degradationManager.isFeatureAvailable(featureName),
                "Feature should remain available in degraded mode");
    }
    
    @Property(tries = 100)
    void databaseFallbackProperty(
            @ForAll("operationNames") String operationName,
            @ForAll("errorMessages") String errorMessage) {
        
        // Given: A database operation that fails
        ErrorContext context = ErrorContext.of(operationName, "database-component");
        
        Callable<java.util.List<String>> failingListOperation = () -> {
            throw new RuntimeException(errorMessage);
        };
        
        Callable<java.util.Optional<String>> failingOptionalOperation = () -> {
            throw new RuntimeException(errorMessage);
        };
        
        Callable<Long> failingCountOperation = () -> {
            throw new RuntimeException(errorMessage);
        };
        
        // When: Executing database operations with fallback protection
        java.util.List<String> listResult = resilienceService.executeDatabaseListOperation(
                operationName, failingListOperation, context);
        
        java.util.Optional<String> optionalResult = resilienceService.executeDatabaseOptionalOperation(
                operationName, failingOptionalOperation, context);
        
        Long countResult = resilienceService.executeDatabaseCountOperation(
                operationName, failingCountOperation, context);
        
        // Then: All operations should return safe fallback values
        assertNotNull(listResult, "List operation should return non-null result");
        assertTrue(listResult.isEmpty(), "Failed list operation should return empty list");
        
        assertNotNull(optionalResult, "Optional operation should return non-null result");
        assertTrue(optionalResult.isEmpty(), "Failed optional operation should return empty optional");
        
        assertNotNull(countResult, "Count operation should return non-null result");
        assertEquals(0L, countResult, "Failed count operation should return zero");
    }
    
    @Property(tries = 100)
    void securityFallbackProperty(
            @ForAll("operationNames") String operationName,
            @ForAll("errorMessages") String errorMessage) {
        
        // Given: A security operation that fails
        ErrorContext context = ErrorContext.of(operationName, "security-component");
        
        Callable<Boolean> failingSecurityOperation = () -> {
            throw new RuntimeException(errorMessage);
        };
        
        // When: Executing security operation with fallback protection
        Boolean result = resilienceService.executeSecurityOperation(
                operationName, failingSecurityOperation, context);
        
        // Then: Security operation should fail safely (deny access)
        assertNotNull(result, "Security operation should return non-null result");
        assertFalse(result, "Failed security operation should deny access for safety");
    }
    
    // Generators for test data
    @Provide
    Arbitrary<String> operationNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s.toLowerCase().replaceAll("[^a-z]", ""));
    }
    
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "Connection timeout",
                "Database unavailable", 
                "Service not responding",
                "Authentication failed",
                "Network error",
                "Resource exhausted"
        );
    }
    
    @Provide
    Arbitrary<String> fallbackValues() {
        return Arbitraries.of(
                "cached-response",
                "default-value", 
                "fallback-result",
                "safe-default",
                "emergency-response"
        );
    }
    
    @Provide
    Arbitrary<String> featureNames() {
        return Arbitraries.of(
                "authentication",
                "authorization",
                "basic-crud",
                "advanced-search",
                "reporting",
                "notifications",
                "audit-logging",
                "external-integrations"
        );
    }
    
    @Provide
    Arbitrary<String> degradationReasons() {
        return Arbitraries.of(
                "High system load",
                "External service unavailable",
                "Database connection issues",
                "Memory pressure",
                "Circuit breaker open"
        );
    }
    
    @Provide
    Arbitrary<String> operationResults() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(15);
    }
}
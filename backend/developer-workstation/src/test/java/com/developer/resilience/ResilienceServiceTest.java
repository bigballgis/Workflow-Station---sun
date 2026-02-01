package com.developer.resilience;

import com.developer.exception.ErrorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResilienceService.
 * 
 * Requirements: 3.5
 */
class ResilienceServiceTest {
    
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Mock
    private GracefulDegradationManager degradationManager;
    
    @Mock
    private CircuitBreaker circuitBreaker;
    
    private ResilienceService resilienceService;
    private ErrorContext errorContext;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resilienceService = new ResilienceService(circuitBreakerRegistry, degradationManager);
        errorContext = ErrorContext.of("test-operation", "test-component");
        
        // Default mock behavior
        when(circuitBreakerRegistry.getCircuitBreaker(anyString())).thenReturn(circuitBreaker);
        when(circuitBreakerRegistry.getCircuitBreaker(anyString(), any(CircuitBreakerConfig.class))).thenReturn(circuitBreaker);
    }
    
    @Test
    @DisplayName("Should execute database list operation with circuit breaker protection")
    void shouldExecuteDatabaseListOperation() throws Exception {
        Callable<List<String>> operation = () -> List.of("item1", "item2");
        List<String> expectedResult = List.of("item1", "item2");
        
        when(circuitBreaker.execute(eq(operation), any(FallbackStrategy.class), eq(errorContext)))
                .thenReturn(expectedResult);
        
        List<String> result = resilienceService.executeDatabaseListOperation("test-read", operation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker("database-test-read");
        verify(circuitBreaker).execute(eq(operation), any(FallbackStrategy.class), eq(errorContext));
    }
    
    @Test
    @DisplayName("Should return empty list when database list operation fails")
    void shouldReturnEmptyListWhenDatabaseListOperationFails() throws Exception {
        Callable<List<String>> failingOperation = () -> {
            throw new RuntimeException("Database connection failed");
        };
        
        when(circuitBreaker.execute(eq(failingOperation), any(FallbackStrategy.class), eq(errorContext)))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        List<String> result = resilienceService.executeDatabaseListOperation("test-read", failingOperation, errorContext);
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("Should execute database optional operation with circuit breaker protection")
    void shouldExecuteDatabaseOptionalOperation() throws Exception {
        Callable<Optional<String>> operation = () -> Optional.of("found");
        Optional<String> expectedResult = Optional.of("found");
        
        when(circuitBreaker.execute(eq(operation), any(FallbackStrategy.class), eq(errorContext)))
                .thenReturn(expectedResult);
        
        Optional<String> result = resilienceService.executeDatabaseOptionalOperation("test-find", operation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker("database-test-find");
    }
    
    @Test
    @DisplayName("Should return empty optional when database optional operation fails")
    void shouldReturnEmptyOptionalWhenDatabaseOptionalOperationFails() throws Exception {
        Callable<Optional<String>> failingOperation = () -> {
            throw new RuntimeException("Database query failed");
        };
        
        when(circuitBreaker.execute(eq(failingOperation), any(FallbackStrategy.class), eq(errorContext)))
                .thenThrow(new RuntimeException("Database query failed"));
        
        Optional<String> result = resilienceService.executeDatabaseOptionalOperation("test-find", failingOperation, errorContext);
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("Should execute database count operation with circuit breaker protection")
    void shouldExecuteDatabaseCountOperation() throws Exception {
        Callable<Long> operation = () -> 42L;
        Long expectedResult = 42L;
        
        when(circuitBreaker.execute(eq(operation), any(FallbackStrategy.class), eq(errorContext)))
                .thenReturn(expectedResult);
        
        Long result = resilienceService.executeDatabaseCountOperation("test-count", operation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker("database-test-count");
    }
    
    @Test
    @DisplayName("Should return zero when database count operation fails")
    void shouldReturnZeroWhenDatabaseCountOperationFails() throws Exception {
        Callable<Long> failingOperation = () -> {
            throw new RuntimeException("Database count failed");
        };
        
        when(circuitBreaker.execute(eq(failingOperation), any(FallbackStrategy.class), eq(errorContext)))
                .thenThrow(new RuntimeException("Database count failed"));
        
        Long result = resilienceService.executeDatabaseCountOperation("test-count", failingOperation, errorContext);
        
        assertEquals(0L, result);
    }
    
    @Test
    @DisplayName("Should execute external service call with circuit breaker protection")
    void shouldExecuteExternalServiceCall() throws Exception {
        Callable<String> operation = () -> "external-result";
        String cachedResponse = "cached-result";
        String expectedResult = "external-result";
        
        when(circuitBreaker.execute(eq(operation), any(FallbackStrategy.class), eq(errorContext)))
                .thenReturn(expectedResult);
        
        String result = resilienceService.executeExternalServiceCall("payment-service", operation, cachedResponse, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker(eq("external-payment-service"), any(CircuitBreakerConfig.class));
    }
    
    @Test
    @DisplayName("Should return cached response when external service call fails")
    void shouldReturnCachedResponseWhenExternalServiceCallFails() throws Exception {
        Callable<String> failingOperation = () -> {
            throw new RuntimeException("External service unavailable");
        };
        String cachedResponse = "cached-result";
        
        when(circuitBreaker.execute(eq(failingOperation), any(FallbackStrategy.class), eq(errorContext)))
                .thenThrow(new RuntimeException("External service unavailable"));
        
        String result = resilienceService.executeExternalServiceCall("payment-service", failingOperation, cachedResponse, errorContext);
        
        assertEquals(cachedResponse, result);
    }
    
    @Test
    @DisplayName("Should execute security operation with strict fallback")
    void shouldExecuteSecurityOperation() throws Exception {
        Callable<Boolean> operation = () -> true;
        Boolean expectedResult = true;
        
        when(circuitBreaker.execute(eq(operation), any(FallbackStrategy.class), eq(errorContext)))
                .thenReturn(expectedResult);
        
        Boolean result = resilienceService.executeSecurityOperation("auth-check", operation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker(eq("security-auth-check"), any(CircuitBreakerConfig.class));
    }
    
    @Test
    @DisplayName("Should deny access when security operation fails")
    void shouldDenyAccessWhenSecurityOperationFails() throws Exception {
        Callable<Boolean> failingOperation = () -> {
            throw new RuntimeException("Auth service down");
        };
        
        when(circuitBreaker.execute(eq(failingOperation), any(FallbackStrategy.class), eq(errorContext)))
                .thenThrow(new RuntimeException("Auth service down"));
        
        Boolean result = resilienceService.executeSecurityOperation("auth-check", failingOperation, errorContext);
        
        assertFalse(result); // Should deny access for security
    }
    
    @Test
    @DisplayName("Should execute operation with graceful degradation")
    void shouldExecuteWithGracefulDegradation() {
        Callable<String> primaryOperation = () -> "primary-result";
        Callable<String> degradedOperation = () -> "degraded-result";
        String expectedResult = "primary-result";
        
        when(degradationManager.executeWithDegradation(
                eq("test-feature"), 
                any(GracefulDegradationManager.OperationExecutor.class),
                any(GracefulDegradationManager.OperationExecutor.class),
                eq(errorContext)
        )).thenReturn(expectedResult);
        
        String result = resilienceService.executeWithGracefulDegradation(
                "test-feature", primaryOperation, degradedOperation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(degradationManager).executeWithDegradation(
                eq("test-feature"), 
                any(GracefulDegradationManager.OperationExecutor.class),
                any(GracefulDegradationManager.OperationExecutor.class),
                eq(errorContext)
        );
    }
    
    @Test
    @DisplayName("Should execute critical operation without fallback")
    void shouldExecuteCriticalOperation() throws Exception {
        Callable<String> operation = () -> "critical-result";
        String expectedResult = "critical-result";
        
        when(circuitBreaker.execute(operation)).thenReturn(expectedResult);
        
        String result = resilienceService.executeCriticalOperation("critical-op", operation, errorContext);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerRegistry).getCircuitBreaker(eq("critical-critical-op"), any(CircuitBreakerConfig.class));
        verify(circuitBreaker).execute(operation);
    }
    
    @Test
    @DisplayName("Should get health status from circuit breakers and degradation manager")
    void shouldGetHealthStatus() {
        // Mock circuit breaker status
        CircuitBreakerStatus cbStatus = CircuitBreakerStatus.builder()
                .name("test-cb")
                .state(CircuitBreakerState.CLOSED)
                .failureCount(0)
                .successCount(10)
                .build();
        
        // Mock degradation status
        DegradationStatus degradationStatus = DegradationStatus.builder()
                .featureName("test-feature")
                .available(true)
                .level(DegradationLevel.NORMAL)
                .build();
        
        when(circuitBreakerRegistry.getStatus()).thenReturn(java.util.Map.of("test-cb", cbStatus));
        when(degradationManager.getDegradationStatus()).thenReturn(java.util.Map.of("test-feature", degradationStatus));
        
        ResilienceHealthStatus status = resilienceService.getHealthStatus();
        
        assertNotNull(status);
        assertTrue(status.isOverallHealthy());
        assertEquals(1, status.getCircuitBreakers().size());
        assertEquals(1, status.getFeatures().size());
    }
    
    @Test
    @DisplayName("Should perform health check")
    void shouldPerformHealthCheck() {
        resilienceService.performHealthCheck();
        
        verify(degradationManager).performHealthCheck();
    }
    
    @Test
    @DisplayName("Should reset all circuit breakers")
    void shouldResetAllCircuitBreakers() {
        resilienceService.resetAllCircuitBreakers();
        
        verify(circuitBreakerRegistry).resetAll();
    }
    
    @Test
    @DisplayName("Should enter emergency mode")
    void shouldEnterEmergencyMode() {
        resilienceService.enterEmergencyMode();
        
        verify(degradationManager).enterEmergencyMode();
    }
    
    @Test
    @DisplayName("Should exit emergency mode")
    void shouldExitEmergencyMode() {
        resilienceService.exitEmergencyMode();
        
        verify(degradationManager).exitEmergencyMode();
    }
}
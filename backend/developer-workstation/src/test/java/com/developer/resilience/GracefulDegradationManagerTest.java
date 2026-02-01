package com.developer.resilience;

import com.developer.exception.ErrorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GracefulDegradationManager.
 * 
 * Requirements: 3.5
 */
class GracefulDegradationManagerTest {
    
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    private GracefulDegradationManager degradationManager;
    private ErrorContext errorContext;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        degradationManager = new GracefulDegradationManager(circuitBreakerRegistry);
        errorContext = ErrorContext.of("test-operation", "test-component");
    }
    
    @Test
    @DisplayName("Features should be available by default")
    void shouldHaveFeuresAvailableByDefault() {
        assertTrue(degradationManager.isFeatureAvailable("authentication"));
        assertTrue(degradationManager.isFeatureAvailable("basic-crud"));
        assertEquals(DegradationLevel.NORMAL, degradationManager.getDegradationLevel("authentication"));
    }
    
    @Test
    @DisplayName("Should degrade feature to specified level")
    void shouldDegradeFeature() {
        String featureName = "advanced-search";
        
        degradationManager.degradeFeature(featureName, DegradationLevel.REDUCED, "High load");
        
        assertTrue(degradationManager.isFeatureAvailable(featureName));
        assertEquals(DegradationLevel.REDUCED, degradationManager.getDegradationLevel(featureName));
    }
    
    @Test
    @DisplayName("Should disable feature when degraded to DISABLED level")
    void shouldDisableFeature() {
        String featureName = "reporting";
        
        degradationManager.degradeFeature(featureName, DegradationLevel.DISABLED, "System overload");
        
        assertFalse(degradationManager.isFeatureAvailable(featureName));
        assertEquals(DegradationLevel.DISABLED, degradationManager.getDegradationLevel(featureName));
    }
    
    @Test
    @DisplayName("Should restore feature to normal operation")
    void shouldRestoreFeature() {
        String featureName = "notifications";
        
        // First degrade the feature
        degradationManager.degradeFeature(featureName, DegradationLevel.DISABLED, "Test degradation");
        assertFalse(degradationManager.isFeatureAvailable(featureName));
        
        // Then restore it
        degradationManager.restoreFeature(featureName);
        
        assertTrue(degradationManager.isFeatureAvailable(featureName));
        assertEquals(DegradationLevel.NORMAL, degradationManager.getDegradationLevel(featureName));
    }
    
    @Test
    @DisplayName("Should execute primary operation when feature is normal")
    void shouldExecutePrimaryOperationWhenNormal() {
        String featureName = "test-feature";
        
        GracefulDegradationManager.OperationExecutor<String> primaryOp = () -> "primary result";
        GracefulDegradationManager.OperationExecutor<String> degradedOp = () -> "degraded result";
        
        String result = degradationManager.executeWithDegradation(featureName, primaryOp, degradedOp, errorContext);
        
        assertEquals("primary result", result);
        assertEquals(DegradationLevel.NORMAL, degradationManager.getDegradationLevel(featureName));
    }
    
    @Test
    @DisplayName("Should execute degraded operation when feature is reduced")
    void shouldExecuteDegradedOperationWhenReduced() {
        String featureName = "test-feature";
        degradationManager.degradeFeature(featureName, DegradationLevel.REDUCED, "Manual degradation");
        
        GracefulDegradationManager.OperationExecutor<String> primaryOp = () -> "primary result";
        GracefulDegradationManager.OperationExecutor<String> degradedOp = () -> "degraded result";
        
        String result = degradationManager.executeWithDegradation(featureName, primaryOp, degradedOp, errorContext);
        
        assertEquals("degraded result", result);
    }
    
    @Test
    @DisplayName("Should throw exception when feature is disabled")
    void shouldThrowExceptionWhenDisabled() {
        String featureName = "test-feature";
        degradationManager.degradeFeature(featureName, DegradationLevel.DISABLED, "Manual disable");
        
        GracefulDegradationManager.OperationExecutor<String> primaryOp = () -> "primary result";
        GracefulDegradationManager.OperationExecutor<String> degradedOp = () -> "degraded result";
        
        assertThrows(GracefulDegradationManager.FeatureDisabledException.class, 
                () -> degradationManager.executeWithDegradation(featureName, primaryOp, degradedOp, errorContext));
    }
    
    @Test
    @DisplayName("Should automatically degrade to reduced when primary operation fails")
    void shouldAutoDegradeOnPrimaryFailure() {
        String featureName = "test-feature";
        
        GracefulDegradationManager.OperationExecutor<String> failingPrimaryOp = () -> {
            throw new RuntimeException("Primary operation failed");
        };
        GracefulDegradationManager.OperationExecutor<String> degradedOp = () -> "degraded result";
        
        String result = degradationManager.executeWithDegradation(featureName, failingPrimaryOp, degradedOp, errorContext);
        
        assertEquals("degraded result", result);
        assertEquals(DegradationLevel.REDUCED, degradationManager.getDegradationLevel(featureName));
    }
    
    @Test
    @DisplayName("Should get degradation status for all features")
    void shouldGetDegradationStatus() {
        // Degrade some features
        degradationManager.degradeFeature("feature1", DegradationLevel.REDUCED, "Test");
        degradationManager.degradeFeature("feature2", DegradationLevel.DISABLED, "Test");
        
        Map<String, DegradationStatus> status = degradationManager.getDegradationStatus();
        
        assertFalse(status.isEmpty());
        assertTrue(status.containsKey("feature1"));
        assertTrue(status.containsKey("feature2"));
        
        DegradationStatus feature1Status = status.get("feature1");
        assertTrue(feature1Status.isAvailable());
        assertTrue(feature1Status.isDegraded());
        
        DegradationStatus feature2Status = status.get("feature2");
        assertFalse(feature2Status.isAvailable());
        assertTrue(feature2Status.isDisabled());
    }
    
    @Test
    @DisplayName("Should enter emergency mode and disable non-critical features")
    void shouldEnterEmergencyMode() {
        degradationManager.enterEmergencyMode();
        
        // Critical features should remain available
        assertTrue(degradationManager.isFeatureAvailable("authentication"));
        assertTrue(degradationManager.isFeatureAvailable("authorization"));
        assertTrue(degradationManager.isFeatureAvailable("basic-crud"));
        
        // Non-critical features should be disabled
        assertFalse(degradationManager.isFeatureAvailable("advanced-search"));
        assertFalse(degradationManager.isFeatureAvailable("reporting"));
        assertFalse(degradationManager.isFeatureAvailable("notifications"));
    }
    
    @Test
    @DisplayName("Should exit emergency mode and restore all features")
    void shouldExitEmergencyMode() {
        // First enter emergency mode
        degradationManager.enterEmergencyMode();
        assertFalse(degradationManager.isFeatureAvailable("reporting"));
        
        // Then exit emergency mode
        degradationManager.exitEmergencyMode();
        
        // All features should be restored
        assertTrue(degradationManager.isFeatureAvailable("reporting"));
        assertTrue(degradationManager.isFeatureAvailable("advanced-search"));
        assertTrue(degradationManager.isFeatureAvailable("notifications"));
    }
    
    @Test
    @DisplayName("Should perform health check and adjust degradation based on circuit breakers")
    void shouldPerformHealthCheck() {
        // Mock circuit breaker status
        CircuitBreakerStatus failingStatus = CircuitBreakerStatus.builder()
                .name("database-read")
                .state(CircuitBreakerState.OPEN)
                .failureCount(5)
                .successCount(0)
                .build();
        
        CircuitBreakerStatus healthyStatus = CircuitBreakerStatus.builder()
                .name("external-auth")
                .state(CircuitBreakerState.CLOSED)
                .failureCount(0)
                .successCount(10)
                .build();
        
        Map<String, CircuitBreakerStatus> statuses = Map.of(
                "database-read", failingStatus,
                "external-auth", healthyStatus
        );
        
        when(circuitBreakerRegistry.getStatus()).thenReturn(statuses);
        
        degradationManager.performHealthCheck();
        
        // Feature mapped to failing circuit breaker should be degraded
        assertEquals(DegradationLevel.REDUCED, degradationManager.getDegradationLevel("basic-crud"));
        
        // Feature mapped to healthy circuit breaker should remain normal
        assertEquals(DegradationLevel.NORMAL, degradationManager.getDegradationLevel("authentication"));
    }
}
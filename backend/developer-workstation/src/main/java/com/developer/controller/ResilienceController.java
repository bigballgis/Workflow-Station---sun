package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.resilience.ResilienceHealthStatus;
import com.developer.resilience.ResilienceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for resilience monitoring and management endpoints.
 * 
 * Requirements: 3.5
 */
@RestController
@RequestMapping("/api/resilience")
@Tag(name = "Resilience", description = "Circuit breaker and graceful degradation management")
@Slf4j
public class ResilienceController extends BaseController {
    
    private final ResilienceService resilienceService;
    
    public ResilienceController(ResilienceService resilienceService) {
        this.resilienceService = resilienceService;
    }
    
    @GetMapping("/health")
    @Operation(summary = "Get resilience health status", 
               description = "Returns the current status of all circuit breakers and feature degradation")
    public ResponseEntity<ApiResponse<ResilienceHealthStatus>> getHealthStatus() {
        return handleRequest(() -> {
            ResilienceHealthStatus status = resilienceService.getHealthStatus();
            log.debug("Retrieved resilience health status: {} circuit breakers, {} features", 
                    status.getCircuitBreakers().size(), status.getFeatures().size());
            return status;
        });
    }
    
    @PostMapping("/health-check")
    @Operation(summary = "Trigger health check", 
               description = "Manually trigger a health check and adjustment of degradation levels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> performHealthCheck() {
        return handleRequest(() -> {
            resilienceService.performHealthCheck();
            log.info("Manual health check performed");
            return null;
        });
    }
    
    @PostMapping("/circuit-breakers/reset")
    @Operation(summary = "Reset all circuit breakers", 
               description = "Reset all circuit breakers to CLOSED state")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetCircuitBreakers() {
        return handleRequest(() -> {
            resilienceService.resetAllCircuitBreakers();
            log.info("All circuit breakers reset by admin");
            return null;
        });
    }
    
    @PostMapping("/emergency-mode/enter")
    @Operation(summary = "Enter emergency mode", 
               description = "Disable non-critical features for emergency operation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> enterEmergencyMode() {
        return handleRequest(() -> {
            resilienceService.enterEmergencyMode();
            log.error("Emergency mode activated by admin");
            return null;
        });
    }
    
    @PostMapping("/emergency-mode/exit")
    @Operation(summary = "Exit emergency mode", 
               description = "Restore all features to normal operation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> exitEmergencyMode() {
        return handleRequest(() -> {
            resilienceService.exitEmergencyMode();
            log.info("Emergency mode deactivated by admin");
            return null;
        });
    }
}
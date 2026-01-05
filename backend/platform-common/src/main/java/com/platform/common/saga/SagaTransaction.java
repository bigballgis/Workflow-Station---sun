package com.platform.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Saga transaction record for distributed transaction management.
 * Validates: Requirements 9.1, 9.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaTransaction implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String sagaId;
    private String sagaType;
    private SagaStatus status;
    private Map<String, Object> payload;
    private int currentStep;
    private int totalSteps;
    @Builder.Default
    private List<SagaStepResult> stepResults = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String initiatorId;
    
    /**
     * Check if the saga is in a terminal state.
     */
    public boolean isTerminal() {
        return status == SagaStatus.COMPLETED || 
               status == SagaStatus.COMPENSATED || 
               status == SagaStatus.FAILED;
    }
    
    /**
     * Check if the saga needs compensation.
     */
    public boolean needsCompensation() {
        return status == SagaStatus.COMPENSATING;
    }
    
    /**
     * Add a step result.
     */
    public void addStepResult(SagaStepResult result) {
        if (stepResults == null) {
            stepResults = new ArrayList<>();
        }
        stepResults.add(result);
    }
    
    public enum SagaStatus {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }
}

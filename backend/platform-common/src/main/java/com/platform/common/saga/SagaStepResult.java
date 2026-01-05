package com.platform.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of a single saga step execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private int stepIndex;
    private String stepName;
    private StepStatus status;
    private Map<String, Object> output;
    private String errorMessage;
    private LocalDateTime executedAt;
    private LocalDateTime compensatedAt;
    private long durationMs;
    
    public enum StepStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        COMPENSATING,
        COMPENSATED,
        COMPENSATION_FAILED
    }
    
    /**
     * Check if this step was successfully completed.
     */
    public boolean isCompleted() {
        return status == StepStatus.COMPLETED;
    }
    
    /**
     * Check if this step needs compensation.
     */
    public boolean needsCompensation() {
        return status == StepStatus.COMPLETED || status == StepStatus.COMPENSATING;
    }
}

package com.platform.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Context passed to saga steps during execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaContext {
    
    private String sagaId;
    private String sagaType;
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();
    @Builder.Default
    private Map<String, Object> stepOutputs = new HashMap<>();
    private int currentStepIndex;
    private String traceId;
    
    /**
     * Get a value from the payload.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key, Class<T> type) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * Get output from a previous step.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStepOutput(String stepName, String key, Class<T> type) {
        Map<String, Object> stepOutput = (Map<String, Object>) stepOutputs.get(stepName);
        if (stepOutput == null) {
            return null;
        }
        return (T) stepOutput.get(key);
    }
    
    /**
     * Add output from current step.
     */
    public void addStepOutput(String stepName, Map<String, Object> output) {
        stepOutputs.put(stepName, output);
    }
}

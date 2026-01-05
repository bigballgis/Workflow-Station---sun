package com.platform.common.saga;

import java.util.Map;

/**
 * Interface for a single saga step.
 * Each step must implement both execute and compensate methods.
 */
public interface SagaStep {
    
    /**
     * Get the step name.
     */
    String getName();
    
    /**
     * Execute the step.
     * 
     * @param context Saga context with payload and previous step results
     * @return Step output data
     * @throws Exception if execution fails
     */
    Map<String, Object> execute(SagaContext context) throws Exception;
    
    /**
     * Compensate (rollback) the step.
     * 
     * @param context Saga context with payload and step results
     * @throws Exception if compensation fails
     */
    void compensate(SagaContext context) throws Exception;
    
    /**
     * Check if this step can be retried on failure.
     */
    default boolean isRetryable() {
        return true;
    }
    
    /**
     * Get the maximum number of retries for this step.
     */
    default int getMaxRetries() {
        return 3;
    }
}

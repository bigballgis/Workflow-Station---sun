package com.platform.common.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Saga orchestrator for managing distributed transactions.
 * Validates: Requirements 9.1, 9.2
 */
@Slf4j
@RequiredArgsConstructor
public class SagaOrchestrator {
    
    private final SagaRepository sagaRepository;
    private final List<SagaStep> steps;
    private final String sagaType;
    
    /**
     * Execute a new saga transaction.
     * 
     * @param payload Initial payload for the saga
     * @param initiatorId User who initiated the saga
     * @return Completed saga transaction
     */
    public SagaTransaction execute(Map<String, Object> payload, String initiatorId) {
        String sagaId = UUID.randomUUID().toString();
        
        SagaTransaction saga = SagaTransaction.builder()
                .sagaId(sagaId)
                .sagaType(sagaType)
                .status(SagaTransaction.SagaStatus.STARTED)
                .payload(payload)
                .currentStep(0)
                .totalSteps(steps.size())
                .stepResults(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .initiatorId(initiatorId)
                .build();
        
        sagaRepository.save(saga);
        log.info("Started saga {} of type {}", sagaId, sagaType);
        
        return executeSteps(saga);
    }
    
    /**
     * Resume a saga from its current state.
     */
    public SagaTransaction resume(String sagaId) {
        SagaTransaction saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
        
        if (saga.isTerminal()) {
            log.warn("Cannot resume terminal saga {}", sagaId);
            return saga;
        }
        
        if (saga.needsCompensation()) {
            return compensate(saga);
        }
        
        return executeSteps(saga);
    }
    
    private SagaTransaction executeSteps(SagaTransaction saga) {
        SagaContext context = buildContext(saga);
        
        saga.setStatus(SagaTransaction.SagaStatus.IN_PROGRESS);
        sagaRepository.save(saga);
        
        for (int i = saga.getCurrentStep(); i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            saga.setCurrentStep(i);
            context.setCurrentStepIndex(i);
            
            SagaStepResult result = executeStep(step, context, i);
            saga.addStepResult(result);
            saga.setUpdatedAt(LocalDateTime.now());
            sagaRepository.save(saga);
            
            if (result.getStatus() == SagaStepResult.StepStatus.FAILED) {
                log.error("Step {} failed in saga {}: {}", step.getName(), saga.getSagaId(), result.getErrorMessage());
                saga.setErrorMessage(result.getErrorMessage());
                return compensate(saga);
            }
            
            context.addStepOutput(step.getName(), result.getOutput());
        }
        
        saga.setStatus(SagaTransaction.SagaStatus.COMPLETED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepository.save(saga);
        
        log.info("Saga {} completed successfully", saga.getSagaId());
        return saga;
    }
    
    private SagaStepResult executeStep(SagaStep step, SagaContext context, int index) {
        LocalDateTime startTime = LocalDateTime.now();
        
        SagaStepResult result = SagaStepResult.builder()
                .stepIndex(index)
                .stepName(step.getName())
                .status(SagaStepResult.StepStatus.EXECUTING)
                .executedAt(startTime)
                .build();
        
        int retries = 0;
        Exception lastException = null;
        
        while (retries <= step.getMaxRetries()) {
            try {
                Map<String, Object> output = step.execute(context);
                result.setOutput(output);
                result.setStatus(SagaStepResult.StepStatus.COMPLETED);
                result.setDurationMs(System.currentTimeMillis() - startTime.getNano() / 1_000_000);
                log.debug("Step {} completed in saga", step.getName());
                return result;
            } catch (Exception e) {
                lastException = e;
                retries++;
                if (step.isRetryable() && retries <= step.getMaxRetries()) {
                    log.warn("Step {} failed, retrying ({}/{}): {}", 
                            step.getName(), retries, step.getMaxRetries(), e.getMessage());
                    try {
                        Thread.sleep((long) Math.pow(2, retries) * 100); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        result.setStatus(SagaStepResult.StepStatus.FAILED);
        result.setErrorMessage(lastException != null ? lastException.getMessage() : "Unknown error");
        return result;
    }
    
    private SagaTransaction compensate(SagaTransaction saga) {
        saga.setStatus(SagaTransaction.SagaStatus.COMPENSATING);
        sagaRepository.save(saga);
        
        log.info("Starting compensation for saga {}", saga.getSagaId());
        
        SagaContext context = buildContext(saga);
        List<SagaStepResult> results = saga.getStepResults();
        
        // Compensate in reverse order
        for (int i = results.size() - 1; i >= 0; i--) {
            SagaStepResult result = results.get(i);
            if (!result.needsCompensation()) {
                continue;
            }
            
            SagaStep step = steps.get(result.getStepIndex());
            
            try {
                step.compensate(context);
                result.setStatus(SagaStepResult.StepStatus.COMPENSATED);
                result.setCompensatedAt(LocalDateTime.now());
                log.debug("Step {} compensated in saga {}", step.getName(), saga.getSagaId());
            } catch (Exception e) {
                log.error("Compensation failed for step {} in saga {}: {}", 
                        step.getName(), saga.getSagaId(), e.getMessage());
                result.setStatus(SagaStepResult.StepStatus.COMPENSATION_FAILED);
                saga.setStatus(SagaTransaction.SagaStatus.FAILED);
                saga.setErrorMessage("Compensation failed: " + e.getMessage());
                sagaRepository.save(saga);
                return saga;
            }
        }
        
        saga.setStatus(SagaTransaction.SagaStatus.COMPENSATED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepository.save(saga);
        
        log.info("Saga {} compensated successfully", saga.getSagaId());
        return saga;
    }
    
    private SagaContext buildContext(SagaTransaction saga) {
        Map<String, Object> stepOutputs = new HashMap<>();
        for (SagaStepResult result : saga.getStepResults()) {
            if (result.getOutput() != null) {
                stepOutputs.put(result.getStepName(), result.getOutput());
            }
        }
        
        return SagaContext.builder()
                .sagaId(saga.getSagaId())
                .sagaType(saga.getSagaType())
                .payload(saga.getPayload())
                .stepOutputs(stepOutputs)
                .build();
    }
}

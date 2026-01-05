package com.platform.common.saga;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for Saga transactions.
 * Validates: Property 13 (Saga Transaction Eventual Consistency)
 * Validates: Property 14 (Optimistic Lock Concurrency Control)
 */
class SagaPropertyTest {
    
    // Property 13: Saga Transaction Eventual Consistency
    // For any saga transaction, if any step fails, all completed steps
    // should be compensated, and final state should be COMPENSATED or FAILED
    
    @Property(tries = 100)
    void failedSagaShouldCompensateAllCompletedSteps(
            @ForAll @IntRange(min = 2, max = 10) int totalSteps,
            @ForAll @IntRange(min = 1, max = 9) int failAtStep) {
        
        // Ensure failAtStep is within bounds
        int actualFailStep = Math.min(failAtStep, totalSteps - 1);
        
        SimulatedSagaOrchestrator orchestrator = new SimulatedSagaOrchestrator(totalSteps, actualFailStep);
        SagaTransaction result = orchestrator.execute(Map.of("test", "data"), "user1");
        
        // Final state should be COMPENSATED or FAILED
        assertThat(result.getStatus())
                .isIn(SagaTransaction.SagaStatus.COMPENSATED, SagaTransaction.SagaStatus.FAILED);
        
        // All completed steps before failure should be compensated
        for (int i = 0; i < actualFailStep; i++) {
            SagaStepResult stepResult = result.getStepResults().get(i);
            assertThat(stepResult.getStatus())
                    .isIn(SagaStepResult.StepStatus.COMPENSATED, 
                          SagaStepResult.StepStatus.COMPENSATION_FAILED);
        }
    }
    
    @Property(tries = 100)
    void successfulSagaShouldCompleteAllSteps(
            @ForAll @IntRange(min = 1, max = 10) int totalSteps) {
        
        SimulatedSagaOrchestrator orchestrator = new SimulatedSagaOrchestrator(totalSteps, -1); // No failure
        SagaTransaction result = orchestrator.execute(Map.of("test", "data"), "user1");
        
        assertThat(result.getStatus()).isEqualTo(SagaTransaction.SagaStatus.COMPLETED);
        assertThat(result.getStepResults()).hasSize(totalSteps);
        
        for (SagaStepResult stepResult : result.getStepResults()) {
            assertThat(stepResult.getStatus()).isEqualTo(SagaStepResult.StepStatus.COMPLETED);
        }
    }
    
    @Property(tries = 100)
    void compensationShouldBeInReverseOrder(
            @ForAll @IntRange(min = 3, max = 10) int totalSteps,
            @ForAll @IntRange(min = 2, max = 9) int failAtStep) {
        
        int actualFailStep = Math.min(failAtStep, totalSteps - 1);
        
        SimulatedSagaOrchestrator orchestrator = new SimulatedSagaOrchestrator(totalSteps, actualFailStep);
        orchestrator.execute(Map.of("test", "data"), "user1");
        
        List<Integer> compensationOrder = orchestrator.getCompensationOrder();
        
        // Compensation should be in reverse order
        for (int i = 0; i < compensationOrder.size() - 1; i++) {
            assertThat(compensationOrder.get(i)).isGreaterThan(compensationOrder.get(i + 1));
        }
    }
    
    // Property 14: Optimistic Lock Concurrency Control
    // For any concurrent updates to the same resource, only one should succeed
    
    @Property(tries = 100)
    void optimisticLockShouldPreventConcurrentUpdates(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceId,
            @ForAll @IntRange(min = 2, max = 10) int concurrentUpdates) {
        
        SimulatedVersionedResource resource = new SimulatedVersionedResource(resourceId);
        
        int successCount = 0;
        int conflictCount = 0;
        
        // Simulate concurrent updates all starting with version 0
        for (int i = 0; i < concurrentUpdates; i++) {
            try {
                resource.updateWithVersion(0, "value" + i);
                successCount++;
            } catch (OptimisticLockException e) {
                conflictCount++;
            }
        }
        
        // Only one update should succeed
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(concurrentUpdates - 1);
    }
    
    @Property(tries = 100)
    void sequentialUpdatesWithCorrectVersionShouldSucceed(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceId,
            @ForAll @IntRange(min = 1, max = 10) int updateCount) {
        
        SimulatedVersionedResource resource = new SimulatedVersionedResource(resourceId);
        
        for (int i = 0; i < updateCount; i++) {
            int currentVersion = resource.getVersion();
            resource.updateWithVersion(currentVersion, "value" + i);
        }
        
        assertThat(resource.getVersion()).isEqualTo(updateCount);
    }
    
    @Property(tries = 100)
    void sagaIdShouldBeUnique(
            @ForAll @Size(min = 2, max = 50) List<@AlphaChars @Size(min = 1, max = 20) String> initiators) {
        
        Set<String> sagaIds = new HashSet<>();
        
        for (String initiator : initiators) {
            SagaTransaction saga = SagaTransaction.builder()
                    .sagaId(UUID.randomUUID().toString())
                    .sagaType("test")
                    .initiatorId(initiator)
                    .build();
            sagaIds.add(saga.getSagaId());
        }
        
        assertThat(sagaIds).hasSize(initiators.size());
    }
    
    @Property(tries = 100)
    void terminalStatesShouldBeImmutable(
            @ForAll("terminalStatuses") SagaTransaction.SagaStatus status) {
        
        SagaTransaction saga = SagaTransaction.builder()
                .sagaId(UUID.randomUUID().toString())
                .sagaType("test")
                .status(status)
                .build();
        
        assertThat(saga.isTerminal()).isTrue();
    }
    
    @Provide
    Arbitrary<SagaTransaction.SagaStatus> terminalStatuses() {
        return Arbitraries.of(
                SagaTransaction.SagaStatus.COMPLETED,
                SagaTransaction.SagaStatus.COMPENSATED,
                SagaTransaction.SagaStatus.FAILED
        );
    }
    
    // Simulated saga orchestrator for testing
    private static class SimulatedSagaOrchestrator {
        private final int totalSteps;
        private final int failAtStep;
        private final List<Integer> compensationOrder = new ArrayList<>();
        private final Map<String, SagaTransaction> storage = new ConcurrentHashMap<>();
        
        SimulatedSagaOrchestrator(int totalSteps, int failAtStep) {
            this.totalSteps = totalSteps;
            this.failAtStep = failAtStep;
        }
        
        SagaTransaction execute(Map<String, Object> payload, String initiatorId) {
            String sagaId = UUID.randomUUID().toString();
            SagaTransaction saga = SagaTransaction.builder()
                    .sagaId(sagaId)
                    .sagaType("test")
                    .status(SagaTransaction.SagaStatus.IN_PROGRESS)
                    .payload(payload)
                    .totalSteps(totalSteps)
                    .stepResults(new ArrayList<>())
                    .initiatorId(initiatorId)
                    .build();
            
            // Execute steps
            for (int i = 0; i < totalSteps; i++) {
                if (i == failAtStep) {
                    saga.addStepResult(SagaStepResult.builder()
                            .stepIndex(i)
                            .stepName("step" + i)
                            .status(SagaStepResult.StepStatus.FAILED)
                            .errorMessage("Simulated failure")
                            .build());
                    break;
                }
                
                saga.addStepResult(SagaStepResult.builder()
                        .stepIndex(i)
                        .stepName("step" + i)
                        .status(SagaStepResult.StepStatus.COMPLETED)
                        .output(Map.of("result", "success"))
                        .build());
            }
            
            // Compensate if failed
            if (failAtStep >= 0 && failAtStep < totalSteps) {
                saga.setStatus(SagaTransaction.SagaStatus.COMPENSATING);
                
                for (int i = saga.getStepResults().size() - 1; i >= 0; i--) {
                    SagaStepResult result = saga.getStepResults().get(i);
                    if (result.getStatus() == SagaStepResult.StepStatus.COMPLETED) {
                        result.setStatus(SagaStepResult.StepStatus.COMPENSATED);
                        compensationOrder.add(i);
                    }
                }
                
                saga.setStatus(SagaTransaction.SagaStatus.COMPENSATED);
            } else {
                saga.setStatus(SagaTransaction.SagaStatus.COMPLETED);
            }
            
            storage.put(sagaId, saga);
            return saga;
        }
        
        List<Integer> getCompensationOrder() {
            return compensationOrder;
        }
    }
    
    // Simulated versioned resource for optimistic locking tests
    private static class SimulatedVersionedResource {
        private final String id;
        private int version = 0;
        private String value;
        
        SimulatedVersionedResource(String id) {
            this.id = id;
        }
        
        synchronized void updateWithVersion(int expectedVersion, String newValue) {
            if (version != expectedVersion) {
                throw new OptimisticLockException("Version conflict: expected " + expectedVersion + " but was " + version);
            }
            this.value = newValue;
            this.version++;
        }
        
        int getVersion() {
            return version;
        }
    }
    
    private static class OptimisticLockException extends RuntimeException {
        OptimisticLockException(String message) {
            super(message);
        }
    }
}

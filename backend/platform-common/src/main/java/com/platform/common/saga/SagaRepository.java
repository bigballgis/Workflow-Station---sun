package com.platform.common.saga;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for saga transaction persistence.
 */
public interface SagaRepository {
    
    /**
     * Save a saga transaction.
     */
    SagaTransaction save(SagaTransaction saga);
    
    /**
     * Find a saga by ID.
     */
    Optional<SagaTransaction> findById(String sagaId);
    
    /**
     * Find all sagas by type.
     */
    List<SagaTransaction> findByType(String sagaType);
    
    /**
     * Find all sagas by status.
     */
    List<SagaTransaction> findByStatus(SagaTransaction.SagaStatus status);
    
    /**
     * Find incomplete sagas (for recovery).
     */
    List<SagaTransaction> findIncomplete();
    
    /**
     * Delete a saga by ID.
     */
    void deleteById(String sagaId);
}

package com.platform.security.repository;

import com.platform.security.model.PermissionDelegation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for permission delegation data access.
 */
public interface PermissionDelegationRepository {
    
    /**
     * Save a delegation.
     */
    PermissionDelegation save(PermissionDelegation delegation);
    
    /**
     * Find delegation by ID.
     */
    Optional<PermissionDelegation> findById(String id);
    
    /**
     * Find all active delegations for a delegatee.
     */
    List<PermissionDelegation> findActiveDelegationsByDelegateeId(String delegateeId, LocalDateTime now);
    
    /**
     * Find all delegations by delegator ID.
     */
    List<PermissionDelegation> findByDelegatorId(String delegatorId);
    
    /**
     * Find all delegations that have expired but not yet marked as expired.
     */
    List<PermissionDelegation> findExpiredActiveDelegations(LocalDateTime now);
    
    /**
     * Update delegation status.
     */
    void updateStatus(String id, PermissionDelegation.DelegationStatus status);
    
    /**
     * Delete delegation by ID.
     */
    void deleteById(String id);
}

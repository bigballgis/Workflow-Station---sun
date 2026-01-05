package com.platform.security.service;

import com.platform.security.model.PermissionDelegation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for permission delegation management.
 * Validates: Requirements 4.7
 */
public interface PermissionDelegationService {
    
    /**
     * Create a new permission delegation.
     * 
     * @param delegatorId User ID of the delegator
     * @param delegateeId User ID of the delegatee
     * @param permissions Set of permission codes to delegate
     * @param roles Set of role codes to delegate
     * @param startTime Start time of the delegation
     * @param endTime End time of the delegation
     * @param reason Reason for delegation
     * @return Created delegation
     */
    PermissionDelegation createDelegation(String delegatorId, String delegateeId,
                                          Set<String> permissions, Set<String> roles,
                                          LocalDateTime startTime, LocalDateTime endTime,
                                          String reason);
    
    /**
     * Revoke an existing delegation.
     * 
     * @param delegationId Delegation ID
     * @param revokedBy User ID who revokes the delegation
     * @return true if revoked successfully
     */
    boolean revokeDelegation(String delegationId, String revokedBy);
    
    /**
     * Get delegation by ID.
     * 
     * @param delegationId Delegation ID
     * @return Delegation if found
     */
    Optional<PermissionDelegation> getDelegation(String delegationId);
    
    /**
     * Get all active delegations for a delegatee.
     * 
     * @param delegateeId User ID of the delegatee
     * @return List of active delegations
     */
    List<PermissionDelegation> getActiveDelegationsForDelegatee(String delegateeId);
    
    /**
     * Get all delegations created by a delegator.
     * 
     * @param delegatorId User ID of the delegator
     * @return List of delegations
     */
    List<PermissionDelegation> getDelegationsByDelegator(String delegatorId);
    
    /**
     * Get delegated permissions for a user.
     * 
     * @param userId User ID
     * @return Set of delegated permission codes
     */
    Set<String> getDelegatedPermissions(String userId);
    
    /**
     * Get delegated roles for a user.
     * 
     * @param userId User ID
     * @return Set of delegated role codes
     */
    Set<String> getDelegatedRoles(String userId);
    
    /**
     * Check if a user has a delegated permission.
     * 
     * @param userId User ID
     * @param permission Permission code
     * @return true if user has the delegated permission
     */
    boolean hasDelegatedPermission(String userId, String permission);
    
    /**
     * Expire all delegations that have passed their end time.
     * This should be called periodically by a scheduled task.
     * 
     * @return Number of delegations expired
     */
    int expireOverdueDelegations();
    
    /**
     * Validate delegation time range.
     * 
     * @param startTime Start time
     * @param endTime End time
     * @return true if time range is valid
     */
    boolean isValidTimeRange(LocalDateTime startTime, LocalDateTime endTime);
}

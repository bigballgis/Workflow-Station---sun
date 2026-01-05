package com.platform.security.service.impl;

import com.platform.cache.service.CacheService;
import com.platform.common.exception.ValidationException;
import com.platform.security.model.PermissionDelegation;
import com.platform.security.model.PermissionDelegation.DelegationStatus;
import com.platform.security.repository.PermissionDelegationRepository;
import com.platform.security.service.PermissionDelegationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PermissionDelegationService.
 * Validates: Requirements 4.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionDelegationServiceImpl implements PermissionDelegationService {
    
    private static final String DELEGATED_PERM_CACHE_PREFIX = "delegation:perm:";
    private static final String DELEGATED_ROLE_CACHE_PREFIX = "delegation:role:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    
    private final PermissionDelegationRepository delegationRepository;
    private final CacheService cacheService;
    
    @Override
    @Transactional
    public PermissionDelegation createDelegation(String delegatorId, String delegateeId,
                                                  Set<String> permissions, Set<String> roles,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  String reason) {
        validateDelegationParams(delegatorId, delegateeId, permissions, roles, startTime, endTime);
        
        PermissionDelegation delegation = PermissionDelegation.builder()
                .id(UUID.randomUUID().toString())
                .delegatorId(delegatorId)
                .delegateeId(delegateeId)
                .delegatedPermissions(permissions != null ? permissions : Collections.emptySet())
                .delegatedRoles(roles != null ? roles : Collections.emptySet())
                .startTime(startTime)
                .endTime(endTime)
                .reason(reason)
                .status(DelegationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .createdBy(delegatorId)
                .build();
        
        PermissionDelegation saved = delegationRepository.save(delegation);
        invalidateDelegationCache(delegateeId);
        
        log.info("Created permission delegation from {} to {}, valid from {} to {}",
                delegatorId, delegateeId, startTime, endTime);
        
        return saved;
    }
    
    @Override
    @Transactional
    public boolean revokeDelegation(String delegationId, String revokedBy) {
        Optional<PermissionDelegation> delegationOpt = delegationRepository.findById(delegationId);
        if (delegationOpt.isEmpty()) {
            log.warn("Delegation not found: {}", delegationId);
            return false;
        }
        
        PermissionDelegation delegation = delegationOpt.get();
        if (delegation.getStatus() != DelegationStatus.ACTIVE) {
            log.warn("Delegation {} is not active, cannot revoke", delegationId);
            return false;
        }
        
        delegationRepository.updateStatus(delegationId, DelegationStatus.REVOKED);
        invalidateDelegationCache(delegation.getDelegateeId());
        
        log.info("Revoked delegation {} by {}", delegationId, revokedBy);
        return true;
    }
    
    @Override
    public Optional<PermissionDelegation> getDelegation(String delegationId) {
        return delegationRepository.findById(delegationId);
    }
    
    @Override
    public List<PermissionDelegation> getActiveDelegationsForDelegatee(String delegateeId) {
        if (delegateeId == null) {
            return Collections.emptyList();
        }
        return delegationRepository.findActiveDelegationsByDelegateeId(delegateeId, LocalDateTime.now());
    }
    
    @Override
    public List<PermissionDelegation> getDelegationsByDelegator(String delegatorId) {
        if (delegatorId == null) {
            return Collections.emptyList();
        }
        return delegationRepository.findByDelegatorId(delegatorId);
    }

    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getDelegatedPermissions(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        
        String cacheKey = DELEGATED_PERM_CACHE_PREFIX + userId;
        Optional<Set> cached = cacheService.get(cacheKey, Set.class);
        if (cached.isPresent()) {
            return (Set<String>) cached.get();
        }
        
        List<PermissionDelegation> activeDelegations = getActiveDelegationsForDelegatee(userId);
        Set<String> delegatedPerms = activeDelegations.stream()
                .filter(PermissionDelegation::isActive)
                .flatMap(d -> d.getDelegatedPermissions().stream())
                .collect(Collectors.toSet());
        
        cacheService.set(cacheKey, delegatedPerms, CACHE_TTL);
        return delegatedPerms;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getDelegatedRoles(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        
        String cacheKey = DELEGATED_ROLE_CACHE_PREFIX + userId;
        Optional<Set> cached = cacheService.get(cacheKey, Set.class);
        if (cached.isPresent()) {
            return (Set<String>) cached.get();
        }
        
        List<PermissionDelegation> activeDelegations = getActiveDelegationsForDelegatee(userId);
        Set<String> delegatedRoles = activeDelegations.stream()
                .filter(PermissionDelegation::isActive)
                .flatMap(d -> d.getDelegatedRoles().stream())
                .collect(Collectors.toSet());
        
        cacheService.set(cacheKey, delegatedRoles, CACHE_TTL);
        return delegatedRoles;
    }
    
    @Override
    public boolean hasDelegatedPermission(String userId, String permission) {
        if (userId == null || permission == null) {
            return false;
        }
        return getDelegatedPermissions(userId).contains(permission);
    }
    
    @Override
    @Transactional
    public int expireOverdueDelegations() {
        List<PermissionDelegation> expiredDelegations = 
                delegationRepository.findExpiredActiveDelegations(LocalDateTime.now());
        
        Set<String> affectedUsers = new HashSet<>();
        for (PermissionDelegation delegation : expiredDelegations) {
            delegationRepository.updateStatus(delegation.getId(), DelegationStatus.EXPIRED);
            affectedUsers.add(delegation.getDelegateeId());
        }
        
        // Invalidate cache for all affected users
        affectedUsers.forEach(this::invalidateDelegationCache);
        
        if (!expiredDelegations.isEmpty()) {
            log.info("Expired {} delegations", expiredDelegations.size());
        }
        
        return expiredDelegations.size();
    }
    
    @Override
    public boolean isValidTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            return false;
        }
        // Start time cannot be more than 30 days in the past
        if (startTime.isBefore(LocalDateTime.now().minusDays(30))) {
            return false;
        }
        // End time cannot be more than 1 year in the future
        if (endTime.isAfter(LocalDateTime.now().plusYears(1))) {
            return false;
        }
        return true;
    }
    
    private void validateDelegationParams(String delegatorId, String delegateeId,
                                          Set<String> permissions, Set<String> roles,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        if (delegatorId == null || delegatorId.isBlank()) {
            throw new ValidationException("Delegator ID is required");
        }
        if (delegateeId == null || delegateeId.isBlank()) {
            throw new ValidationException("Delegatee ID is required");
        }
        if (delegatorId.equals(delegateeId)) {
            throw new ValidationException("Cannot delegate permissions to self");
        }
        if ((permissions == null || permissions.isEmpty()) && (roles == null || roles.isEmpty())) {
            throw new ValidationException("At least one permission or role must be delegated");
        }
        if (!isValidTimeRange(startTime, endTime)) {
            throw new ValidationException("Invalid delegation time range");
        }
    }
    
    private void invalidateDelegationCache(String userId) {
        if (userId != null) {
            cacheService.delete(DELEGATED_PERM_CACHE_PREFIX + userId);
            cacheService.delete(DELEGATED_ROLE_CACHE_PREFIX + userId);
        }
    }
}

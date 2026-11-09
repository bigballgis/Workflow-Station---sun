package com.admin.service;

import com.admin.client.EngineInternalClient;
import com.admin.dto.request.ClaimPoolResyncRequest;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the engine's BU + Role claim pools on the current UBR holders (platform access model §7.3, WP8b-2): a pool
 * is no longer frozen at task creation, so every write to {@code sys_user_business_unit_roles} reports the pools it
 * touched here, and the engine is told once the write is committed (it re-reads the holders, which an uncommitted
 * change would not show).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimPoolResyncService {

    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final EngineInternalClient engineInternalClient;

    /**
     * Records that holders of these BU + Role pools changed; the engine is called after the surrounding transaction
     * commits (immediately when there is none, the write being committed already) and not at all on rollback.
     *
     * @param businessUnitId UBR business unit id
     * @param roleIds        roles granted or revoked in that business unit; empty means nothing changed
     */
    public void ubrChanged(String businessUnitId, Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<ClaimPoolResyncRequest.Pool> pools = resolvePools(businessUnitId, roleIds);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            push(pools);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push(pools);
            }
        });
    }

    private List<ClaimPoolResyncRequest.Pool> resolvePools(String businessUnitId, Collection<String> roleIds) {
        BusinessUnit businessUnit = businessUnitRepository.findById(businessUnitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitId));
        LinkedHashSet<String> distinctRoleIds = new LinkedHashSet<>(roleIds);
        Map<String, Role> rolesById = roleRepository.findAllById(distinctRoleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        List<ClaimPoolResyncRequest.Pool> pools = new ArrayList<>(distinctRoleIds.size());
        for (String roleId : distinctRoleIds) {
            Role role = rolesById.get(roleId);
            if (role == null) {
                throw new RoleNotFoundException(roleId);
            }
            pools.add(new ClaimPoolResyncRequest.Pool(
                    businessUnit.getId(), businessUnit.getCode(), role.getId(), role.getCode()));
        }
        return pools;
    }

    private void push(List<ClaimPoolResyncRequest.Pool> pools) {
        try {
            engineInternalClient.resyncClaimPools(pools);
            log.info("Engine claim pools resynced after UBR change: {}", describe(pools));
        } catch (RuntimeException e) {
            // FALLBACK(external): the UBR change is committed and stays the authority; failing the request now would
            // report a saved grant/revoke as failed (Portal approvals would show an error for a granted role).
            // The backstop is the engine's ClaimPoolResyncScheduler (D-37): every 10 minutes it recomputes every open
            // BU + Role pool from the current UBR, so a missed push only delays the refresh.
            log.error("Engine claim-pool resync failed after a committed UBR change; pools={}: {}",
                    describe(pools), e.getMessage(), e);
        }
    }

    private static String describe(List<ClaimPoolResyncRequest.Pool> pools) {
        return pools.stream()
                .map(p -> p.businessUnitCode() + "/" + p.roleCode())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}

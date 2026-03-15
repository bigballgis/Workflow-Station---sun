package com.admin.bi.service.impl;

import com.admin.bi.component.SupersetRoleSyncComponent;
import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.entity.BiRbacMapping;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.bi.service.BiRbacMappingService;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RBAC 映射 Service 实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiRbacMappingServiceImpl implements BiRbacMappingService {

    private final BiRbacMappingRepository mappingRepository;
    private final BiSupersetRoleRepository supersetRoleRepository;
    private final RoleRepository roleRepository;
    private final SupersetRoleSyncComponent supersetRoleSyncComponent;

    @Override
    public SyncResultResponse syncSupersetRoles() {
        log.info("Manual Superset Role sync triggered");
        return supersetRoleSyncComponent.executeSyncOperation();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupersetRoleResponse> listSupersetRoles() {
        return supersetRoleRepository.findAll().stream()
                .map(this::toSupersetRoleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RbacMappingResponse> listMappings(String roleName, String roleType) {
        // 1. Query sys_roles with optional filters
        List<Role> sysRoles;
        if (roleName != null || roleType != null) {
            sysRoles = roleRepository.findAllActive().stream()
                    .filter(r -> roleName == null ||
                            r.getName().toLowerCase().contains(roleName.toLowerCase()))
                    .filter(r -> roleType == null ||
                            r.getType().equalsIgnoreCase(roleType))
                    .collect(Collectors.toList());
        } else {
            sysRoles = roleRepository.findAllActive();
        }

        // 2. Query all mappings
        List<BiRbacMapping> allMappings = mappingRepository.findAll();
        Map<String, List<BiRbacMapping>> mappingsBySysRole = allMappings.stream()
                .collect(Collectors.groupingBy(BiRbacMapping::getSysRoleId));

        // 3. Build superset role lookup
        Map<Integer, BiSupersetRole> supersetRoleMap = supersetRoleRepository.findAll().stream()
                .collect(Collectors.toMap(BiSupersetRole::getSupersetRoleId, r -> r));

        // 4. Build response for each sys role
        return sysRoles.stream().map(role -> {
            List<BiRbacMapping> roleMappings = mappingsBySysRole.getOrDefault(role.getId(), Collections.emptyList());
            List<SupersetRoleResponse> supersetRoles = roleMappings.stream()
                    .map(m -> supersetRoleMap.get(m.getSupersetRoleId()))
                    .filter(Objects::nonNull)
                    .map(this::toSupersetRoleResponse)
                    .collect(Collectors.toList());

            LocalDateTime lastUpdated = roleMappings.stream()
                    .map(BiRbacMapping::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            return RbacMappingResponse.builder()
                    .sysRoleId(role.getId())
                    .sysRoleName(role.getName())
                    .sysRoleCode(role.getCode())
                    .sysRoleType(role.getType())
                    .supersetRoles(supersetRoles)
                    .lastUpdatedAt(lastUpdated)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateMapping(String sysRoleId, RbacMappingUpdateRequest request) {
        // 1. Validate: only ACTIVE Superset_Roles can be mapped
        List<Integer> requestedIds = request.getSupersetRoleIds();
        if (requestedIds != null && !requestedIds.isEmpty()) {
            List<BiSupersetRole> supersetRoles = supersetRoleRepository.findBySupersetRoleIdIn(requestedIds);
            Map<Integer, BiSupersetRole> foundMap = supersetRoles.stream()
                    .collect(Collectors.toMap(BiSupersetRole::getSupersetRoleId, r -> r));

            for (Integer id : requestedIds) {
                BiSupersetRole role = foundMap.get(id);
                if (role == null) {
                    throw new IllegalArgumentException("Superset Role not found: " + id);
                }
                if (role.getStatus() != SupersetRoleStatus.ACTIVE) {
                    throw new IllegalArgumentException("Superset Role is not ACTIVE: " + id);
                }
            }
        }

        // 2. Delete all existing mappings for this sysRoleId
        mappingRepository.deleteBySysRoleId(sysRoleId);

        // 3. Create new mappings
        if (requestedIds != null) {
            for (Integer supersetRoleId : requestedIds) {
                BiRbacMapping mapping = BiRbacMapping.builder()
                        .id(UUID.randomUUID().toString())
                        .sysRoleId(sysRoleId)
                        .supersetRoleId(supersetRoleId)
                        .build();
                mappingRepository.save(mapping);
            }
        }

        log.info("Updated RBAC mapping for sysRoleId={}: {} Superset roles",
                sysRoleId, requestedIds != null ? requestedIds.size() : 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getEffectiveSupersetRoleIds(List<String> sysRoleIds) {
        if (sysRoleIds == null || sysRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Query all mappings for the given sysRoleIds
        List<BiRbacMapping> mappings = mappingRepository.findBySysRoleIdIn(sysRoleIds);

        // 2. Collect all mapped superset role IDs
        Set<Integer> supersetRoleIds = mappings.stream()
                .map(BiRbacMapping::getSupersetRoleId)
                .collect(Collectors.toSet());

        if (supersetRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Filter to only ACTIVE Superset_Roles
        List<BiSupersetRole> supersetRoles = supersetRoleRepository
                .findBySupersetRoleIdIn(new ArrayList<>(supersetRoleIds));

        return supersetRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.ACTIVE)
                .map(BiSupersetRole::getSupersetRoleId)
                .distinct()
                .collect(Collectors.toList());
    }

    private SupersetRoleResponse toSupersetRoleResponse(BiSupersetRole entity) {
        return SupersetRoleResponse.builder()
                .id(entity.getId())
                .supersetRoleId(entity.getSupersetRoleId())
                .name(entity.getName())
                .status(entity.getStatus())
                .lastSyncedAt(entity.getLastSyncedAt())
                .build();
    }
}

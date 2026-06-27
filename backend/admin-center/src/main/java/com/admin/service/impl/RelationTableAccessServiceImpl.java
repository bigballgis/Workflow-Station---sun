package com.admin.service.impl;

import com.admin.entity.RelationTableAccess;
import com.admin.repository.RelationTableAccessRepository;
import com.admin.service.RelationTableAccessService;
import com.platform.common.enums.RelationPermissionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Relation Table 访问权限服务实现
 * 参照 FunctionUnitAccessService 模式，通过 Business Role 控制 User Portal 中的数据可见性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableAccessServiceImpl implements RelationTableAccessService {

    private final RelationTableAccessRepository accessRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableAccess> getAccessConfig(Long tableId) {
        return accessRepository.findByTableId(tableId);
    }

    @Override
    @Transactional
    public RelationTableAccess addAccess(Long tableId, String targetType, String targetId, String permissionLevel) {
        // Check if access already exists
        if (accessRepository.existsByTableIdAndTargetTypeAndTargetId(tableId, targetType, targetId)) {
            log.warn("Access config already exists for tableId={}, targetType={}, targetId={}", tableId, targetType, targetId);
            throw new IllegalArgumentException("This role has already been assigned access permission");
        }

        RelationTableAccess access = RelationTableAccess.builder()
                .tableId(tableId)
                .targetType(targetType)
                .targetId(targetId)
                .permissionLevel(RelationPermissionLevel.normalize(permissionLevel))
                .build();

        access = accessRepository.save(access);
        log.info("Added access config for table {}: targetType={}, targetId={}, level={}",
                tableId, targetType, targetId, access.getPermissionLevel());
        return access;
    }

    @Override
    @Transactional
    public void batchSetAccess(Long tableId, List<String> targetIds, String permissionLevel) {
        // Delete existing access configs for this table
        accessRepository.deleteByTableId(tableId);

        String level = RelationPermissionLevel.normalize(permissionLevel);

        // Create new access configs
        List<RelationTableAccess> newConfigs = new ArrayList<>();
        for (String targetId : targetIds) {
            RelationTableAccess access = RelationTableAccess.builder()
                    .tableId(tableId)
                    .targetType("ROLE")
                    .targetId(targetId)
                    .permissionLevel(level)
                    .build();
            newConfigs.add(access);
        }

        accessRepository.saveAll(newConfigs);
        log.info("Batch set {} access configs for table {} at level {}", targetIds.size(), tableId, level);
    }

    @Override
    @Transactional
    public RelationTableAccess updatePermissionLevel(String accessId, String permissionLevel) {
        RelationTableAccess access = accessRepository.findById(accessId)
                .orElseThrow(() -> new IllegalArgumentException("Access config not found: " + accessId));
        access.setPermissionLevel(RelationPermissionLevel.normalize(permissionLevel));
        access = accessRepository.save(access);
        log.info("Updated access {} permission level to {}", accessId, access.getPermissionLevel());
        return access;
    }

    @Override
    @Transactional
    public void removeAccess(String accessId) {
        accessRepository.deleteById(accessId);
        log.info("Removed access config: {}", accessId);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolvePermissionLevel(Long tableId, Collection<String> userRoleIds) {
        if (userRoleIds == null || userRoleIds.isEmpty()) {
            return null;
        }

        List<RelationTableAccess> configs = accessRepository.findByTableId(tableId);
        if (configs.isEmpty()) {
            return null;
        }

        boolean found = false;
        for (RelationTableAccess config : configs) {
            if ("ROLE".equals(config.getTargetType()) && userRoleIds.contains(config.getTargetId())) {
                found = true;
                // Highest privilege wins across multiple matching roles.
                if (RelationPermissionLevel.canWrite(config.getPermissionLevel())) {
                    return RelationPermissionLevel.READ_WRITE;
                }
            }
        }
        return found ? RelationPermissionLevel.READONLY : null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAccess(Long tableId, List<String> userRoleIds) {
        return resolvePermissionLevel(tableId, userRoleIds) != null;
    }
}

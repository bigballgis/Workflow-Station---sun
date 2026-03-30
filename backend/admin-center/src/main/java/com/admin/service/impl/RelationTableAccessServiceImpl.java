package com.admin.service.impl;

import com.admin.entity.RelationTableAccess;
import com.admin.repository.RelationTableAccessRepository;
import com.admin.service.RelationTableAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    public RelationTableAccess addAccess(Long tableId, String targetType, String targetId) {
        // Check if access already exists
        if (accessRepository.existsByTableIdAndTargetTypeAndTargetId(tableId, targetType, targetId)) {
            log.warn("Access config already exists for tableId={}, targetType={}, targetId={}", tableId, targetType, targetId);
            throw new IllegalArgumentException("该角色已被分配访问权限");
        }

        RelationTableAccess access = RelationTableAccess.builder()
                .tableId(tableId)
                .targetType(targetType)
                .targetId(targetId)
                .build();

        access = accessRepository.save(access);
        log.info("Added access config for table {}: targetType={}, targetId={}", tableId, targetType, targetId);
        return access;
    }

    @Override
    @Transactional
    public void batchSetAccess(Long tableId, List<String> targetIds) {
        // Delete existing access configs for this table
        accessRepository.deleteByTableId(tableId);

        // Create new access configs
        List<RelationTableAccess> newConfigs = new ArrayList<>();
        for (String targetId : targetIds) {
            RelationTableAccess access = RelationTableAccess.builder()
                    .tableId(tableId)
                    .targetType("ROLE")
                    .targetId(targetId)
                    .build();
            newConfigs.add(access);
        }

        accessRepository.saveAll(newConfigs);
        log.info("Batch set {} access configs for table {}", targetIds.size(), tableId);
    }

    @Override
    @Transactional
    public void removeAccess(String accessId) {
        accessRepository.deleteById(accessId);
        log.info("Removed access config: {}", accessId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAccess(Long tableId, List<String> userRoleIds) {
        if (userRoleIds == null || userRoleIds.isEmpty()) {
            return false;
        }

        List<RelationTableAccess> configs = accessRepository.findByTableId(tableId);

        // If no access configs exist, no one has access (unlike FunctionUnit which defaults to open)
        if (configs.isEmpty()) {
            return false;
        }

        // Check if any of the user's roles has access
        for (RelationTableAccess config : configs) {
            if ("ROLE".equals(config.getTargetType()) && userRoleIds.contains(config.getTargetId())) {
                return true;
            }
        }

        return false;
    }
}

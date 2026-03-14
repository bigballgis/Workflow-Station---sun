package com.admin.bi.component;

import com.admin.bi.config.BiProperties;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.exception.SupersetSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Superset 角色同步组件
 * 从 Superset 数据库 ab_role 表同步角色信息到本地 bi_superset_role 注册表
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupersetRoleSyncComponent {

    private final JdbcTemplate jdbcTemplate;
    private final BiSupersetRoleRepository roleRepository;
    private final BiProperties biProperties;

    private static final String SUPERSET_ROLE_QUERY =
            "SELECT id, name FROM public.ab_role";

    /**
     * 定时同步入口
     */
    @Scheduled(cron = "${bi.sync.cron:0 0 */6 * * ?}")
    public void scheduledSync() {
        if (!biProperties.getSync().isEnabled()) {
            log.debug("Superset Role scheduled sync is disabled, skipping");
            return;
        }
        log.info("Starting scheduled Superset Role sync");
        try {
            SyncResultResponse result = executeSyncOperation();
            log.info("Scheduled Superset Role sync completed: created={}, updated={}, inactivated={}",
                    result.getCreated(), result.getUpdated(), result.getAutoInactivated());
        } catch (SupersetSyncException e) {
            log.error("Scheduled Superset Role sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行同步操作（可由定时任务或手动触发调用）
     *
     * @return 同步结果摘要
     * @throws SupersetSyncException 当 Superset 数据库查询失败时
     */
    @Transactional
    public SyncResultResponse executeSyncOperation() {
        LocalDateTime syncTime = LocalDateTime.now();

        // 1. 查询 Superset 数据库中的 ab_role 记录
        List<Map<String, Object>> supersetRoles;
        try {
            supersetRoles = jdbcTemplate.queryForList(SUPERSET_ROLE_QUERY);
        } catch (Exception e) {
            log.error("Failed to query Superset ab_role table: {}", e.getMessage(), e);
            throw new SupersetSyncException("Failed to query Superset ab_role table: " + e.getMessage(), e);
        }

        // 2. 查询所有本地注册记录，按 supersetRoleId 建立索引
        List<BiSupersetRole> localRecords = roleRepository.findAll();
        Map<Integer, BiSupersetRole> localMap = localRecords.stream()
                .collect(Collectors.toMap(BiSupersetRole::getSupersetRoleId, Function.identity()));

        // 3. 收集 Superset 端存在的角色 ID 集合
        Set<Integer> supersetRoleIds = new HashSet<>();

        int created = 0;
        int updated = 0;

        // 4. 遍历 Superset 数据，执行新增/更新/恢复逻辑
        for (Map<String, Object> row : supersetRoles) {
            Integer supersetRoleId = (Integer) row.get("id");
            String name = (String) row.get("name");

            supersetRoleIds.add(supersetRoleId);

            BiSupersetRole existing = localMap.get(supersetRoleId);

            if (existing == null) {
                // 新增：创建新注册记录，状态为 ACTIVE
                BiSupersetRole newRecord = BiSupersetRole.builder()
                        .supersetRoleId(supersetRoleId)
                        .name(name)
                        .status(SupersetRoleStatus.ACTIVE)
                        .lastSyncedAt(syncTime)
                        .createdAt(syncTime)
                        .updatedAt(syncTime)
                        .build();
                roleRepository.save(newRecord);
                created++;
                log.debug("Created new Superset Role: supersetRoleId={}, name={}", supersetRoleId, name);
            } else {
                // 已存在：检查是否需要更新 name 或恢复状态
                boolean nameChanged = !Objects.equals(existing.getName(), name);

                if (existing.getStatus() == SupersetRoleStatus.INACTIVE) {
                    // INACTIVE 恢复为 ACTIVE
                    existing.setStatus(SupersetRoleStatus.ACTIVE);
                    if (nameChanged) {
                        existing.setName(name);
                    }
                    existing.setLastSyncedAt(syncTime);
                    roleRepository.save(existing);
                    updated++;
                    log.debug("Restored INACTIVE Superset Role to ACTIVE: supersetRoleId={}", supersetRoleId);
                } else {
                    // ACTIVE：检查 name 是否变化
                    if (nameChanged) {
                        existing.setName(name);
                        existing.setLastSyncedAt(syncTime);
                        roleRepository.save(existing);
                        updated++;
                        log.debug("Updated Superset Role name: supersetRoleId={}", supersetRoleId);
                    } else {
                        existing.setLastSyncedAt(syncTime);
                        roleRepository.save(existing);
                    }
                }
            }
        }

        // 5. 处理本地多余记录：不在 Superset 结果中的标记为 INACTIVE
        int inactivated = 0;
        for (BiSupersetRole localRecord : localRecords) {
            if (!supersetRoleIds.contains(localRecord.getSupersetRoleId())) {
                if (localRecord.getStatus() != SupersetRoleStatus.INACTIVE) {
                    localRecord.setStatus(SupersetRoleStatus.INACTIVE);
                    localRecord.setLastSyncedAt(syncTime);
                    roleRepository.save(localRecord);
                    inactivated++;
                    log.debug("Set Superset Role to INACTIVE: supersetRoleId={}", localRecord.getSupersetRoleId());
                }
            }
        }

        log.info("Superset Role sync completed: created={}, updated={}, inactivated={}", created, updated, inactivated);

        return SyncResultResponse.builder()
                .created(created)
                .updated(updated)
                .autoInactivated(inactivated)
                .syncedAt(syncTime)
                .build();
    }
}

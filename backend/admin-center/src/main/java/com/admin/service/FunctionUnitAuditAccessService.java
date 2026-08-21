package com.admin.service;

import com.admin.dto.request.FunctionUnitAccessRequest;
import com.admin.dto.response.FunctionUnitAuditAccessInfo;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitAuditAccess;
import com.admin.repository.FunctionUnitAuditAccessRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 功能单元审计授权服务 —— 「谁能查看该功能单元下的全部 request」。
 *
 * <p>与 {@link FunctionUnitAccessService}（「谁能发起该功能单元」）刻意保持完全独立：
 * 两种权限不同源、不互相回退，审计授权不得让人多出发起能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitAuditAccessService {

    private final FunctionUnitAuditAccessRepository auditAccessRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<FunctionUnitAuditAccessInfo> getAuditAccessConfigs(String functionUnitId) {
        return auditAccessRepository.findByFunctionUnitId(functionUnitId)
                .stream()
                .map(access -> {
                    FunctionUnitAuditAccessInfo info = FunctionUnitAuditAccessInfo.fromEntity(access);
                    if (FunctionUnitAuditAccess.TARGET_TYPE_ROLE.equals(access.getTargetType())) {
                        roleRepository.findById(access.getTargetId())
                                .ifPresent(role -> {
                                    info.setTargetName(role.getName());
                                    info.setTargetCode(role.getCode());
                                });
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public FunctionUnitAuditAccessInfo addAuditAccessConfig(String functionUnitId,
                                                            FunctionUnitAccessRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + functionUnitId));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new EntityNotFoundException("角色不存在: " + request.getRoleId()));

        if (auditAccessRepository.existsByFunctionUnitIdAndRoleId(functionUnitId, request.getRoleId())) {
            throw new IllegalArgumentException("This role already has audit access to this function unit");
        }

        FunctionUnitAuditAccess access = FunctionUnitAuditAccess.builder()
                .functionUnit(functionUnit)
                .targetType(FunctionUnitAuditAccess.TARGET_TYPE_ROLE)
                .targetId(request.getRoleId())
                .build();

        access = auditAccessRepository.save(access);
        log.info("Added audit access for function unit {}: roleId={}", functionUnitId, request.getRoleId());

        FunctionUnitAuditAccessInfo info = FunctionUnitAuditAccessInfo.fromEntity(access);
        info.setTargetName(role.getName());
        info.setTargetCode(role.getCode());
        return info;
    }

    @Transactional
    public void removeAuditAccessConfig(String functionUnitId, String accessId) {
        FunctionUnitAuditAccess access = auditAccessRepository.findById(accessId)
                .orElseThrow(() -> new EntityNotFoundException("审计授权不存在: " + accessId));

        if (!access.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new IllegalArgumentException("Audit access config does not belong to this function unit");
        }

        auditAccessRepository.delete(access);
        log.info("Removed audit access {} from function unit {}", accessId, functionUnitId);
    }

    /**
     * Does this role set grant audit access to the unit? Role <em>codes</em> are the
     * contract with user-portal — ids differ per environment.
     */
    @Transactional(readOnly = true)
    public boolean hasAuditAccess(String functionUnitId, List<String> userRoleIds) {
        if (userRoleIds == null || userRoleIds.isEmpty()) {
            return false;
        }
        return auditAccessRepository.findByFunctionUnitId(functionUnitId).stream()
                .anyMatch(cfg -> FunctionUnitAuditAccess.TARGET_TYPE_ROLE.equals(cfg.getTargetType())
                        && userRoleIds.contains(cfg.getTargetId()));
    }

    /**
     * Carries audit grants onto a newly imported version of the same unit.
     * Grants live on the catalog row, so without this they silently vanish on
     * every redeploy and reviewers lose access with no error anywhere.
     */
    @Transactional
    public int copyAuditAccessFromSiblingVersions(String code, String targetFunctionUnitId) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        List<FunctionUnitAuditAccess> sourceConfigs = findLatestSiblingAuditConfigs(code, targetFunctionUnitId);
        if (sourceConfigs.isEmpty()) {
            return 0;
        }

        FunctionUnit target = functionUnitRepository.findById(targetFunctionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + targetFunctionUnitId));

        int copied = 0;
        for (FunctionUnitAuditAccess source : sourceConfigs) {
            if (auditAccessRepository.existsByFunctionUnitIdAndRoleId(targetFunctionUnitId, source.getTargetId())) {
                continue;
            }
            auditAccessRepository.save(FunctionUnitAuditAccess.builder()
                    .functionUnit(target)
                    .targetType(source.getTargetType())
                    .targetId(source.getTargetId())
                    .build());
            copied++;
        }
        if (copied > 0) {
            log.info("Copied {} audit access config(s) for code {} onto catalog row {}",
                    copied, code, targetFunctionUnitId);
        }
        return copied;
    }

    private List<FunctionUnitAuditAccess> findLatestSiblingAuditConfigs(String code, String targetFunctionUnitId) {
        for (FunctionUnit sibling : functionUnitRepository.findByCodeOrderByVersionDesc(code)) {
            if (targetFunctionUnitId.equals(sibling.getId())) {
                continue;
            }
            List<FunctionUnitAuditAccess> configs = auditAccessRepository.findByFunctionUnitId(sibling.getId());
            if (!configs.isEmpty()) {
                return configs;
            }
        }
        return List.of();
    }
}

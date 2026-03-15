package com.developer.component.impl;

import com.developer.component.CommonTableComponent;
import com.developer.dto.CommonTableRequest;
import com.developer.dto.FieldDefinitionRequest;
import com.developer.entity.CommonFieldDefinition;
import com.developer.entity.CommonTableDefinition;
import com.developer.entity.CommonTableDeployment;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.CommonFieldDefinitionRepository;
import com.developer.repository.CommonTableDefinitionRepository;
import com.developer.repository.CommonTableDeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 公共表管理组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommonTableComponentImpl implements CommonTableComponent {

    private final CommonTableDefinitionRepository commonTableRepository;
    private final CommonFieldDefinitionRepository commonFieldRepository;
    private final CommonTableDeploymentRepository deploymentRepository;
    private final ObjectMapper objectMapper;
    private final com.developer.repository.UserRepository userRepository;

    @Override
    public List<CommonTableDefinition> findAll() {
        return commonTableRepository.findAllWithFields();
    }

    @Override
    public CommonTableDefinition findById(Long id) {
        return commonTableRepository.findByIdWithFields(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommonTable", id));
    }

    @Override
    public CommonTableDefinition findByCode(String code) {
        return commonTableRepository.findByCodeWithFields(code)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "公共表不存在: " + code, "请检查表编码"));
    }

    @Override
    @Transactional
    public CommonTableDefinition create(CommonTableRequest request, String createdBy) {
        if (commonTableRepository.existsByCode(request.getCode())) {
            throw new BusinessException("CONFLICT_CODE_EXISTS",
                    "表编码已存在: " + request.getCode(), "请使用其他编码");
        }
        if (commonTableRepository.existsByName(request.getName())) {
            throw new BusinessException("CONFLICT_NAME_EXISTS",
                    "表名称已存在: " + request.getName(), "请使用其他名称");
        }

        CommonTableDefinition table = CommonTableDefinition.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "DRAFT")
                .createdBy(createdBy)
                .build();

        table = commonTableRepository.save(table);

        if (request.getFields() != null) {
            addFields(table, request.getFields());
        }

        return commonTableRepository.save(table);
    }

    @Override
    @Transactional
    public CommonTableDefinition update(Long id, CommonTableRequest request) {
        CommonTableDefinition table = findById(id);

        if (commonTableRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new BusinessException("CONFLICT_CODE_EXISTS",
                    "表编码已存在: " + request.getCode(), "请使用其他编码");
        }
        if (commonTableRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BusinessException("CONFLICT_NAME_EXISTS",
                    "表名称已存在: " + request.getName(), "请使用其他名称");
        }

        table.setCode(request.getCode());
        table.setName(request.getName());
        table.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            table.setStatus(request.getStatus());
        }

        // Replace fields: clear + delete + re-add
        table.getFieldDefinitions().clear();
        commonFieldRepository.deleteByCommonTableId(id);
        commonFieldRepository.flush();

        if (request.getFields() != null) {
            addFields(table, request.getFields());
        }

        return commonTableRepository.save(table);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CommonTableDefinition table = findById(id);
        commonTableRepository.delete(table);
        log.info("Deleted common table: {} ({})", table.getCode(), id);
    }

    @Override
    @Transactional
    public CommonTableDefinition deploy(Long id, String deployedBy) {
        CommonTableDefinition table = findById(id);
        String nextVersion = incrementVersion(table.getVersion());
        String deployerName = resolveFullName(deployedBy);
        table.setVersion(nextVersion);
        table.setStatus("PUBLISHED");
        table.setDeployedAt(Instant.now());
        table.setDeployedBy(deployerName);
        table.setEnabled(true);
        table = commonTableRepository.save(table);

        String snapshot = buildFieldSnapshot(table);
        CommonTableDeployment deployment = CommonTableDeployment.builder()
                .commonTable(table)
                .version(nextVersion)
                .status("COMPLETED")
                .fieldSnapshot(snapshot)
                .deployedAt(Instant.now())
                .deployedBy(deployerName)
                .build();
        deploymentRepository.save(deployment);

        log.info("Deployed common table: {} ({}) version {}", table.getCode(), id, nextVersion);
        return table;
    }

    @Override
    @Transactional
    public CommonTableDefinition updateEnabled(Long id, boolean enabled) {
        CommonTableDefinition table = findById(id);
        table.setEnabled(enabled);
        return commonTableRepository.save(table);
    }

    @Override
    public List<CommonTableDeployment> findDeployments(Long id) {
        return deploymentRepository.findByCommonTable_IdOrderByDeployedAtDesc(id);
    }

    @Override
    public List<CommonTableDeployment> findAllDeployments() {
        return deploymentRepository.findAllByOrderByDeployedAtDesc();
    }

    private String resolveFullName(String userId) {
        if (userId == null || userId.isBlank()) return userId;
        return userRepository.findById(userId)
                .map(u -> {
                    String full = u.getFullName();
                    return (full != null && !full.isBlank()) ? full : u.getUsername();
                })
                .orElse(userId);
    }

    private String incrementVersion(String version) {
        if (version == null || version.isBlank()) return "1.0.0";
        String[] parts = version.split("\\.");
        if (parts.length < 3) return "1.0.0";
        try {
            int patch = Integer.parseInt(parts[2]) + 1;
            return parts[0] + "." + parts[1] + "." + patch;
        } catch (NumberFormatException e) {
            return "1.0.0";
        }
    }

    private String buildFieldSnapshot(CommonTableDefinition table) {
        try {
            return objectMapper.writeValueAsString(table.getFieldDefinitions());
        } catch (Exception e) {
            return "[]";
        }
    }

    private void addFields(CommonTableDefinition table, List<FieldDefinitionRequest> fieldRequests) {
        int order = 0;
        for (FieldDefinitionRequest req : fieldRequests) {
            CommonFieldDefinition field = CommonFieldDefinition.builder()
                    .commonTable(table)
                    .fieldName(req.getFieldName())
                    .displayName(req.getFieldName())
                    .dataType(req.getDataType())
                    .length(req.getLength())
                    .precision(req.getPrecision())
                    .scale(req.getScale())
                    .nullable(req.getNullable() != null ? req.getNullable() : true)
                    .defaultValue(req.getDefaultValue())
                    .isPrimaryKey(req.getIsPrimaryKey() != null ? req.getIsPrimaryKey() : false)
                    .isUnique(req.getIsUnique() != null ? req.getIsUnique() : false)
                    .description(req.getDescription())
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : order)
                    .build();
            table.getFieldDefinitions().add(commonFieldRepository.save(field));
            order++;
        }
    }
}

package com.developer.component.impl;

import com.developer.component.CommonTableComponent;
import com.developer.dto.CommonTableRequest;
import com.developer.dto.FieldDefinitionRequest;
import com.developer.entity.CommonFieldDefinition;
import com.developer.entity.CommonTableDefinition;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.CommonFieldDefinitionRepository;
import com.developer.repository.CommonTableDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

package com.admin.service;

import com.admin.dto.response.AllocatePrimaryKeyResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RelationTablePrimaryKeyAllocationService {

    private final RelationTableDefinitionRepository tableRepository;
    private final PrimaryKeyAllocationService primaryKeyAllocationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AllocatePrimaryKeyResponse allocate(Long tableId, String fieldName, Integer count, String scopeKey) {
        RelationTableDefinition table = tableRepository.findByIdWithFields(tableId)
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "RelationTable not found: " + tableId));
        RelationFieldDefinition field = table.getFieldDefinitions().stream()
                .filter(f -> fieldName.equals(f.getFieldName()))
                .findFirst()
                .orElseThrow(() -> new AdminBusinessException("NOT_FOUND", "Field not found: " + fieldName));
        if (!Boolean.TRUE.equals(field.getIsPrimaryKey())) {
            throw new AdminBusinessException("NOT_PK_FIELD", "Field is not a primary key: " + fieldName);
        }
        PkGenerationConfig config = toPkConfig(field.getPkGenerationJson());
        int n = count != null && count > 0 ? count : 1;
        String effectiveScope = scopeKey != null ? scopeKey : "rt-" + tableId;
        List<String> values = primaryKeyAllocationService.allocate(
                tableId, fieldName, config, n, effectiveScope);
        return AllocatePrimaryKeyResponse.builder().values(values).build();
    }

    private PkGenerationConfig toPkConfig(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PkGenerationConfig.builder().strategy("uuid").build();
        }
        return objectMapper.convertValue(json, PkGenerationConfig.class);
    }
}

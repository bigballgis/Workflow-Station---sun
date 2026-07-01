package com.developer.component.impl;

import com.developer.component.PrimaryKeyAllocationComponent;
import com.developer.dto.AllocatePrimaryKeyRequest;
import com.developer.dto.AllocatePrimaryKeyResponse;
import com.developer.entity.FieldDefinition;
import com.developer.entity.TableDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.TableDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PrimaryKeyAllocationComponentImpl implements PrimaryKeyAllocationComponent {

    private final TableDefinitionRepository tableDefinitionRepository;
    private final PrimaryKeyAllocationService primaryKeyAllocationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AllocatePrimaryKeyResponse allocate(AllocatePrimaryKeyRequest request, Long functionUnitId) {
        TableDefinition table = tableDefinitionRepository.findByIdWithFields(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getTableId()));
        if (!table.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new DeveloperBusinessException("FORBIDDEN", "Table does not belong to Function Unit");
        }
        FieldDefinition field = table.getFieldDefinitions().stream()
                .filter(f -> request.getFieldName().equals(f.getFieldName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", request.getFieldName()));
        if (!Boolean.TRUE.equals(field.getIsPrimaryKey())) {
            throw new DeveloperBusinessException("NOT_PK_FIELD", "Field is not a primary key: " + request.getFieldName());
        }
        PkGenerationConfig config = toPkConfig(field.getPkGenerationJson());
        int count = request.getCount() != null && request.getCount() > 0 ? request.getCount() : 1;
        List<String> values = primaryKeyAllocationService.allocate(
                request.getTableId(), request.getFieldName(), config, count, "");
        return AllocatePrimaryKeyResponse.builder().values(values).build();
    }

    private PkGenerationConfig toPkConfig(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PkGenerationConfig.builder().strategy("uuid").build();
        }
        return objectMapper.convertValue(json, PkGenerationConfig.class);
    }
}

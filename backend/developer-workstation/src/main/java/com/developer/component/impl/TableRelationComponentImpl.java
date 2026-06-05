package com.developer.component.impl;

import com.developer.component.TableRelationComponent;
import com.developer.dto.TableRelationDTO;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableRelation;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.service.FieldFkPkSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 表关系组件实现
 */
@Component
@Slf4j
public class TableRelationComponentImpl implements TableRelationComponent {

    private final TableRelationRepository tableRelationRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FieldFkPkSyncService fieldFkPkSyncService;

    public TableRelationComponentImpl(TableRelationRepository tableRelationRepository,
                                      FunctionUnitRepository functionUnitRepository,
                                      FieldFkPkSyncService fieldFkPkSyncService) {
        this.tableRelationRepository = tableRelationRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.fieldFkPkSyncService = fieldFkPkSyncService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableRelationDTO> getByFunctionUnitId(Long functionUnitId) {
        return fieldFkPkSyncService.deriveRelationsFromFields(functionUnitId);
    }

    @Override
    @Transactional
    public List<TableRelationDTO> saveAll(Long functionUnitId, List<TableRelationDTO> dtos) {
        functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        fieldFkPkSyncService.applyRelationsToFieldMetadata(functionUnitId, dtos);

        // Keep dw_table_relations in sync for export/legacy tooling
        tableRelationRepository.deleteByFunctionUnitId(functionUnitId);
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId).orElseThrow();
        List<TableRelation> entities = dtos.stream()
                .map(dto -> toEntity(dto, functionUnit))
                .toList();
        tableRelationRepository.saveAll(entities);

        return fieldFkPkSyncService.deriveRelationsFromFields(functionUnitId);
    }

    @Override
    @Transactional
    public void deleteByFunctionUnitId(Long functionUnitId) {
        tableRelationRepository.deleteByFunctionUnitId(functionUnitId);
    }

    private TableRelationDTO toDTO(TableRelation entity) {
        return TableRelationDTO.builder()
                .id(entity.getId())
                .sourceTableId(entity.getSourceTableId())
                .sourceFieldName(entity.getSourceFieldName())
                .relationType(entity.getRelationType())
                .targetTableId(entity.getTargetTableId())
                .targetFieldName(entity.getTargetFieldName())
                .build();
    }

    private TableRelation toEntity(TableRelationDTO dto, FunctionUnit functionUnit) {
        return TableRelation.builder()
                .functionUnit(functionUnit)
                .sourceTableId(dto.getSourceTableId())
                .sourceFieldName(dto.getSourceFieldName())
                .relationType(dto.getRelationType())
                .targetTableId(dto.getTargetTableId())
                .targetFieldName(dto.getTargetFieldName())
                .build();
    }
}

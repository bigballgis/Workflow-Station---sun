package com.developer.component.impl;

import com.developer.component.TableRelationComponent;
import com.developer.dto.TableRelationDTO;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableRelation;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableRelationRepository;
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

    public TableRelationComponentImpl(TableRelationRepository tableRelationRepository,
                                      FunctionUnitRepository functionUnitRepository) {
        this.tableRelationRepository = tableRelationRepository;
        this.functionUnitRepository = functionUnitRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableRelationDTO> getByFunctionUnitId(Long functionUnitId) {
        return tableRelationRepository.findByFunctionUnitId(functionUnitId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public List<TableRelationDTO> saveAll(Long functionUnitId, List<TableRelationDTO> dtos) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        // 删除现有关系，替换为新的
        tableRelationRepository.deleteByFunctionUnitId(functionUnitId);

        List<TableRelation> entities = dtos.stream()
                .map(dto -> toEntity(dto, functionUnit))
                .toList();

        return tableRelationRepository.saveAll(entities)
                .stream()
                .map(this::toDTO)
                .toList();
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

package com.developer.component.impl;

import com.developer.entity.FieldDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.BpmnProcessSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模拟协作类。
 *
 * <p>从 {@link ProcessDesignComponentImpl} 拆出，负责加载子表字段并委托 {@link BpmnProcessSimulator} 模拟执行
 * （含多实例 mock collection 自动生成）。行为零变化。</p>
 */
@Component
@Slf4j
public class ProcessSimulationHelper {

    private final TableDefinitionRepository tableDefinitionRepository;

    public ProcessSimulationHelper(TableDefinitionRepository tableDefinitionRepository) {
        this.tableDefinitionRepository = tableDefinitionRepository;
    }

    public Map<String, Object> simulate(Long functionUnitId, String bpmnXml, Map<String, Object> variables) {
        Map<Long, List<FieldDefinition>> fieldsByTableId = loadSubTableFieldsById(functionUnitId);
        Map<String, Object> result = new LinkedHashMap<>(
                BpmnProcessSimulator.simulate(bpmnXml, variables, fieldsByTableId));
        result.put("status", "SIMULATED");
        return result;
    }

    private Map<Long, List<FieldDefinition>> loadSubTableFieldsById(Long functionUnitId) {
        if (functionUnitId == null) {
            return Map.of();
        }
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        Map<Long, List<FieldDefinition>> fieldsByTableId = new LinkedHashMap<>();
        for (TableDefinition table : tables) {
            if (table.getId() == null || table.getTableType() != TableType.SUB) {
                continue;
            }
            fieldsByTableId.put(
                    table.getId(),
                    table.getFieldDefinitions() != null ? table.getFieldDefinitions() : List.of());
        }
        return fieldsByTableId;
    }
}

package com.developer.service.impl;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.*;
import com.developer.enums.*;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import com.developer.service.AiWriteService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI 数据写入服务实现
 * 全量替换写入策略：MODIFY 模式下先删除旧数据再写入新数据，NEW 模式下直接写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiWriteServiceImpl implements AiWriteService {

    private final FunctionUnitRepository functionUnitRepository;
    private final IconRepository iconRepository;
    private final EntityManager entityManager;

    @Override
    public void applyGeneratedData(Long functionUnitId, AiGeneratedData generatedData) {
        log.info("开始写入 AI 生成数据到功能单元: {}", functionUnitId);

        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new com.developer.exception.AiGenerationException(
                        "AI_WRITE_NOT_FOUND", "功能单元不存在: " + functionUnitId));

        // Determine mode: if FunctionUnit has existing component data, it's MODIFY mode
        boolean isModifyMode = hasExistingData(functionUnit);

        if (isModifyMode) {
            log.info("MODIFY 模式：清除功能单元 {} 的现有组件数据", functionUnitId);
            clearExistingData(functionUnit);
            entityManager.flush();
        }

        // Write new data from AiGeneratedData
        Map<String, TableDefinition> tableMap = writeTableDefinitions(functionUnit, generatedData);
        writeForeignKeys(generatedData, tableMap);
        writeFormDefinitions(functionUnit, generatedData, tableMap);
        writeActionDefinitions(functionUnit, generatedData);
        writeProcessDefinition(functionUnit, generatedData);

        // Handle icon matching/creation before saving
        handleIcon(functionUnit, generatedData);

        // Update FunctionUnit name/description if provided
        if (generatedData.getName() != null && !generatedData.getName().isBlank()) {
            functionUnit.setName(generatedData.getName());
        }
        if (generatedData.getDescription() != null && !generatedData.getDescription().isBlank()) {
            functionUnit.setDescription(generatedData.getDescription());
        }

        // Save — JPA cascades will persist all children
        functionUnitRepository.save(functionUnit);
        log.info("AI 生成数据写入完成，功能单元: {}", functionUnitId);
    }

    private boolean hasExistingData(FunctionUnit functionUnit) {
        return functionUnit.getProcessDefinition() != null
                || (functionUnit.getTableDefinitions() != null && !functionUnit.getTableDefinitions().isEmpty())
                || (functionUnit.getFormDefinitions() != null && !functionUnit.getFormDefinitions().isEmpty())
                || (functionUnit.getActionDefinitions() != null && !functionUnit.getActionDefinitions().isEmpty());
    }

    private void clearExistingData(FunctionUnit functionUnit) {
        functionUnit.getTableDefinitions().clear();
        functionUnit.getFormDefinitions().clear();
        functionUnit.getActionDefinitions().clear();
        if (functionUnit.getProcessDefinition() != null) {
            functionUnit.setProcessDefinition(null);
        }
    }

    private void handleIcon(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        Map<String, Object> iconData = generatedData.getIcon();
        if (iconData == null) return;

        String name = (String) iconData.get("name");
        if (name == null) return;

        Optional<Icon> existingIcon = iconRepository.findByName(name);
        if (existingIcon.isPresent()) {
            functionUnit.setIcon(existingIcon.get());
            log.info("匹配到已有图标: {}", name);
        } else {
            String categoryStr = (String) iconData.get("category");
            String svgContent = (String) iconData.get("svgContent");
            String description = (String) iconData.get("description");

            Icon newIcon = Icon.builder()
                    .name(name)
                    .category(IconCategory.valueOf(categoryStr))
                    .svgContent(svgContent)
                    .description(description)
                    .fileSize(svgContent != null ? svgContent.getBytes(StandardCharsets.UTF_8).length : 0)
                    .build();

            newIcon = iconRepository.save(newIcon);
            functionUnit.setIcon(newIcon);
            log.info("创建新图标: {}", name);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, TableDefinition> writeTableDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        Map<String, TableDefinition> tableMap = new LinkedHashMap<>();
        List<Map<String, Object>> tableDefs = generatedData.getTableDefinitions();
        if (tableDefs == null) return tableMap;

        for (Map<String, Object> tableData : tableDefs) {
            TableDefinition table = TableDefinition.builder()
                    .functionUnit(functionUnit)
                    .tableName((String) tableData.get("tableName"))
                    .tableType(TableType.valueOf((String) tableData.get("tableType")))
                    .tableDisplayName((String) tableData.get("tableDisplayName"))
                    .description((String) tableData.get("description"))
                    .build();

            // Write field definitions
            List<Map<String, Object>> fieldDefs = (List<Map<String, Object>>) tableData.get("fieldDefinitions");
            if (fieldDefs != null) {
                for (Map<String, Object> fieldData : fieldDefs) {
                    FieldDefinition field = FieldDefinition.builder()
                            .tableDefinition(table)
                            .fieldName((String) fieldData.get("fieldName"))
                            .dataType(DataType.valueOf((String) fieldData.get("dataType")))
                            .length(toInteger(fieldData.get("length")))
                            .precision(toInteger(fieldData.get("precision")))
                            .scale(toInteger(fieldData.get("scale")))
                            .nullable(toBoolean(fieldData.get("nullable"), true))
                            .defaultValue((String) fieldData.get("defaultValue"))
                            .isPrimaryKey(toBoolean(fieldData.get("isPrimaryKey"), false))
                            .isUnique(toBoolean(fieldData.get("isUnique"), false))
                            .description((String) fieldData.get("description"))
                            .sortOrder(toInt(fieldData.get("sortOrder")))
                            .build();
                    table.getFieldDefinitions().add(field);
                }
            }

            functionUnit.getTableDefinitions().add(table);
            tableMap.put(table.getTableName(), table);
        }

        return tableMap;
    }

    @SuppressWarnings("unchecked")
    private void writeForeignKeys(AiGeneratedData generatedData, Map<String, TableDefinition> tableMap) {
        List<Map<String, Object>> tableDefs = generatedData.getTableDefinitions();
        if (tableDefs == null) return;

        for (Map<String, Object> tableData : tableDefs) {
            String tableName = (String) tableData.get("tableName");
            TableDefinition parentTable = tableMap.get(tableName);
            if (parentTable == null) continue;

            List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) tableData.get("foreignKeys");
            if (foreignKeys == null) continue;

            for (Map<String, Object> fkData : foreignKeys) {
                String fieldName = (String) fkData.get("fieldName");
                String refTableName = (String) fkData.get("refTableName");
                String refFieldName = (String) fkData.get("refFieldName");

                // Resolve field in parent table
                FieldDefinition fieldDef = findFieldByName(parentTable, fieldName);
                // Resolve referenced table and field
                TableDefinition refTable = tableMap.get(refTableName);
                FieldDefinition refFieldDef = refTable != null ? findFieldByName(refTable, refFieldName) : null;

                if (fieldDef == null || refTable == null || refFieldDef == null) {
                    log.warn("跳过外键：无法解析引用 - table={}, field={}, refTable={}, refField={}",
                            tableName, fieldName, refTableName, refFieldName);
                    continue;
                }

                ForeignKey foreignKey = ForeignKey.builder()
                        .tableDefinition(parentTable)
                        .fieldDefinition(fieldDef)
                        .refTableDefinition(refTable)
                        .refFieldDefinition(refFieldDef)
                        .onDelete(fkData.get("onDelete") != null ? (String) fkData.get("onDelete") : "NO ACTION")
                        .onUpdate(fkData.get("onUpdate") != null ? (String) fkData.get("onUpdate") : "NO ACTION")
                        .build();

                parentTable.getForeignKeys().add(foreignKey);
            }
        }
    }

    private FieldDefinition findFieldByName(TableDefinition table, String fieldName) {
        if (table == null || fieldName == null) return null;
        return table.getFieldDefinitions().stream()
                .filter(f -> fieldName.equals(f.getFieldName()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void writeFormDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData,
                                       Map<String, TableDefinition> tableMap) {
        List<Map<String, Object>> formDefs = generatedData.getFormDefinitions();
        if (formDefs == null) return;

        for (Map<String, Object> formData : formDefs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> configJson = (Map<String, Object>) formData.get("configJson");

            FormDefinition form = FormDefinition.builder()
                    .functionUnit(functionUnit)
                    .formName((String) formData.get("formName"))
                    .formType(FormType.valueOf((String) formData.get("formType")))
                    .configJson(configJson)
                    .description((String) formData.get("description"))
                    .build();

            // Write table bindings
            List<Map<String, Object>> bindings = (List<Map<String, Object>>) formData.get("tableBindings");
            TableDefinition primaryTable = null;

            if (bindings != null) {
                for (Map<String, Object> bindingData : bindings) {
                    String tableName = (String) bindingData.get("tableName");
                    TableDefinition boundTable = tableMap.get(tableName);

                    BindingType bindingType = BindingType.valueOf((String) bindingData.get("bindingType"));

                    FormTableBinding binding = FormTableBinding.builder()
                            .form(form)
                            .table(boundTable)
                            .bindingType(bindingType)
                            .bindingMode(BindingMode.valueOf((String) bindingData.get("bindingMode")))
                            .foreignKeyField((String) bindingData.get("foreignKeyField"))
                            .sortOrder(toInteger(bindingData.get("sortOrder")))
                            .build();

                    form.getTableBindings().add(binding);

                    // Track PRIMARY binding for backward compat boundTable field
                    if (bindingType == BindingType.PRIMARY && boundTable != null) {
                        primaryTable = boundTable;
                    }
                }
            }

            // Backward compat: set boundTable to the PRIMARY binding's table
            form.setBoundTable(primaryTable);
            functionUnit.getFormDefinitions().add(form);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeActionDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        List<Map<String, Object>> actionDefs = generatedData.getActionDefinitions();
        if (actionDefs == null) return;

        for (Map<String, Object> actionData : actionDefs) {
            Map<String, Object> configJson = (Map<String, Object>) actionData.get("configJson");

            ActionDefinition action = ActionDefinition.builder()
                    .functionUnit(functionUnit)
                    .actionName((String) actionData.get("actionName"))
                    .actionType(ActionType.valueOf((String) actionData.get("actionType")))
                    .configJson(configJson)
                    .icon((String) actionData.get("icon"))
                    .buttonColor((String) actionData.get("buttonColor"))
                    .description((String) actionData.get("description"))
                    .isDefault(toBoolean(actionData.get("isDefault"), false))
                    .build();

            functionUnit.getActionDefinitions().add(action);
        }
    }

    private void writeProcessDefinition(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        Map<String, Object> procData = generatedData.getProcessDefinition();
        if (procData == null) return;

        String bpmnXml = (String) procData.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.isBlank()) return;

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .functionUnit(functionUnit)
                .functionUnitVersionId(functionUnit.getId())
                .bpmnXml(bpmnXml)
                .build();

        functionUnit.setProcessDefinition(processDefinition);
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object value) {
        Integer result = toInteger(value);
        return result != null ? result : 0;
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
}

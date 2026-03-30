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
    public void applyGeneratedData(Long functionUnitId, AiGeneratedData generatedData, String regenerateScope) {
        log.info("Applying AI generated data to function unit: {}, scope: {}", functionUnitId,
                regenerateScope != null ? regenerateScope : "ALL");

        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new com.developer.exception.AiGenerationException(
                        "AI_WRITE_NOT_FOUND", "Function unit not found: " + functionUnitId));

        // Determine mode: if FunctionUnit has existing component data, it's MODIFY mode
        boolean isModifyMode = hasExistingData(functionUnit);

        if (isModifyMode) {
            if (regenerateScope != null && !"ALL".equalsIgnoreCase(regenerateScope)) {
                log.info("MODIFY mode (scoped): clearing '{}' data for function unit {}", regenerateScope, functionUnitId);
                clearScopedData(functionUnit, regenerateScope);
            } else {
                log.info("MODIFY mode: clearing existing component data for function unit {}", functionUnitId);
                clearExistingData(functionUnit);
            }
            entityManager.flush();
        }

        // Write new data from AiGeneratedData
        Map<String, TableDefinition> tableMap = writeTableDefinitions(functionUnit, generatedData);
        entityManager.flush(); // Ensure TableDefinitions get database IDs before writing relations
        writeForeignKeys(generatedData, tableMap);
        writeTableRelations(functionUnit, generatedData, tableMap);
        writeFormDefinitions(functionUnit, generatedData, tableMap);
        writeActionDefinitions(functionUnit, generatedData);
        writeDecisionDefinitions(functionUnit, generatedData);
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
        log.info("AI generated data write complete, function unit: {}", functionUnitId);
    }

    private boolean hasExistingData(FunctionUnit functionUnit) {
        return functionUnit.getProcessDefinition() != null
                || (functionUnit.getTableDefinitions() != null && !functionUnit.getTableDefinitions().isEmpty())
                || (functionUnit.getFormDefinitions() != null && !functionUnit.getFormDefinitions().isEmpty())
                || (functionUnit.getActionDefinitions() != null && !functionUnit.getActionDefinitions().isEmpty())
                || (functionUnit.getDecisionDefinitions() != null && !functionUnit.getDecisionDefinitions().isEmpty())
                || (functionUnit.getTableRelations() != null && !functionUnit.getTableRelations().isEmpty());
    }

    private void clearExistingData(FunctionUnit functionUnit) {
        // tableRelations must be cleared before tableDefinitions (dependency order)
        functionUnit.getTableRelations().clear();
        functionUnit.getTableDefinitions().clear();
        functionUnit.getFormDefinitions().clear();
        functionUnit.getActionDefinitions().clear();
        functionUnit.getDecisionDefinitions().clear();
        if (functionUnit.getProcessDefinition() != null) {
            functionUnit.setProcessDefinition(null);
        }
    }

    private void clearScopedData(FunctionUnit functionUnit, String scope) {
        switch (scope.toUpperCase()) {
            case "TABLES" -> {
                // tableRelations depend on tableDefinitions, must clear both
                functionUnit.getTableRelations().clear();
                functionUnit.getTableDefinitions().clear();
            }
            case "FORMS" -> functionUnit.getFormDefinitions().clear();
            case "ACTIONS" -> functionUnit.getActionDefinitions().clear();
            case "DECISIONS" -> functionUnit.getDecisionDefinitions().clear();
            case "PROCESS" -> functionUnit.setProcessDefinition(null);
            case "TABLE_RELATIONS" -> functionUnit.getTableRelations().clear();
            default -> {
                log.warn("Unknown regenerate scope '{}', falling back to full clear", scope);
                clearExistingData(functionUnit);
            }
        }
    }

    private void handleIcon(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        Map<String, Object> iconData = generatedData.getIcon();
        if (iconData == null) return;

        String name = (String) iconData.get("name");
        if (name == null || name.isBlank()) return;

        Optional<Icon> existingIcon = iconRepository.findByName(name);
        if (existingIcon.isPresent()) {
            functionUnit.setIcon(existingIcon.get());
            log.info("Matched existing icon: {}", name);
        } else {
            String categoryStr = (String) iconData.get("category");
            String svgContent = (String) iconData.get("svgContent");
            String description = (String) iconData.get("description");

            IconCategory category;
            try {
                category = IconCategory.valueOf(categoryStr);
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Invalid icon category '{}', using default GENERAL", categoryStr);
                category = IconCategory.GENERAL;
            }

            Icon newIcon = Icon.builder()
                    .name(name)
                    .category(category)
                    .svgContent(svgContent)
                    .description(description)
                    .fileSize(svgContent != null ? svgContent.getBytes(StandardCharsets.UTF_8).length : 0)
                    .build();

            newIcon = iconRepository.save(newIcon);
            functionUnit.setIcon(newIcon);
            log.info("Created new icon: {}", name);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, TableDefinition> writeTableDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        Map<String, TableDefinition> tableMap = new LinkedHashMap<>();
        List<Map<String, Object>> tableDefs = generatedData.getTableDefinitions();
        if (tableDefs == null) return tableMap;

        for (Map<String, Object> tableData : tableDefs) {
            String tableTypeStr = (String) tableData.get("tableType");
            TableType tableType;
            try {
                tableType = TableType.valueOf(tableTypeStr);
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Invalid table type '{}', skipping table", tableTypeStr);
                continue;
            }

            TableDefinition table = TableDefinition.builder()
                    .functionUnit(functionUnit)
                    .tableName((String) tableData.get("tableName"))
                    .tableType(tableType)
                    .tableDisplayName((String) (tableData.get("tableDisplayName") != null ? tableData.get("tableDisplayName") : tableData.get("displayName")))
                    .description((String) (tableData.get("description") != null ? tableData.get("description") : tableData.get("comment")))
                    .build();

            // Write field definitions — support both "fieldDefinitions" and "fields" key names
            List<Map<String, Object>> fieldDefs = (List<Map<String, Object>>) tableData.get("fieldDefinitions");
            if (fieldDefs == null) {
                fieldDefs = (List<Map<String, Object>>) tableData.get("fields");
            }
            if (fieldDefs != null) {
                for (Map<String, Object> fieldData : fieldDefs) {
                    DataType dataType;
                    try {
                        dataType = DataType.valueOf((String) fieldData.get("dataType"));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        log.warn("Invalid field data type '{}', defaulting to VARCHAR", fieldData.get("dataType"));
                        dataType = DataType.VARCHAR;
                    }

                    FieldDefinition field = FieldDefinition.builder()
                            .tableDefinition(table)
                            .fieldName((String) fieldData.get("fieldName"))
                            .dataType(dataType)
                            .length(toInteger(fieldData.get("length")))
                            .precision(toInteger(fieldData.get("precision")))
                            .scale(toInteger(fieldData.get("scale")))
                            .nullable(toBoolean(fieldData.get("nullable"), true))
                            .defaultValue((String) fieldData.get("defaultValue"))
                            .isPrimaryKey(toBoolean(
                                    fieldData.get("isPrimaryKey") != null ? fieldData.get("isPrimaryKey") : fieldData.get("primaryKey"),
                                    false))
                            .isUnique(toBoolean(fieldData.get("isUnique"), false))
                            .description((String) (fieldData.get("description") != null ? fieldData.get("description") : fieldData.get("comment")))
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
                    log.warn("Skipping foreign key: cannot resolve reference - table={}, field={}, refTable={}, refField={}",
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

    private void writeTableRelations(FunctionUnit functionUnit, AiGeneratedData generatedData,
                                      Map<String, TableDefinition> tableMap) {
        List<Map<String, Object>> relationDefs = generatedData.getTableRelations();
        if (relationDefs == null) return;

        for (Map<String, Object> relData : relationDefs) {
            String sourceTableName = (String) relData.get("sourceTableName");
            String targetTableName = (String) relData.get("targetTableName");

            TableDefinition sourceTable = tableMap.get(sourceTableName);
            TableDefinition targetTable = tableMap.get(targetTableName);

            if (sourceTable == null) {
                log.warn("writeTableRelations: source table '{}' not found in tableMap, skipping", sourceTableName);
                continue;
            }
            if (targetTable == null) {
                log.warn("writeTableRelations: target table '{}' not found in tableMap, skipping", targetTableName);
                continue;
            }

            TableRelation relation = TableRelation.builder()
                    .functionUnit(functionUnit)
                    .sourceTableId(sourceTable.getId())
                    .sourceFieldName((String) relData.get("sourceFieldName"))
                    .relationType((String) relData.get("relationType"))
                    .targetTableId(targetTable.getId())
                    .targetFieldName((String) relData.get("targetFieldName"))
                    .build();

            functionUnit.getTableRelations().add(relation);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeFormDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData,
                                       Map<String, TableDefinition> tableMap) {
        List<Map<String, Object>> formDefs = generatedData.getFormDefinitions();
        if (formDefs == null) return;

        for (Map<String, Object> formData : formDefs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> configJson = (Map<String, Object>) formData.get("configJson");

            FormType formType;
            try {
                formType = FormType.valueOf((String) formData.get("formType"));
            } catch (IllegalArgumentException | NullPointerException e) {
                String formTypeStr = (String) formData.get("formType");
                formType = mapLegacyFormType(formTypeStr);
                if (formType != null) {
                    log.info("Auto-mapped deprecated form type '{}' to '{}'", formTypeStr, formType.name());
                } else {
                    log.warn("Invalid form type '{}', skipping form", formTypeStr);
                    continue;
                }
            }

            FormDefinition form = FormDefinition.builder()
                    .functionUnit(functionUnit)
                    .formName((String) formData.get("formName"))
                    .formType(formType)
                    .configJson(configJson)
                    .description((String) formData.get("description"))
                    .build();

            // Write table bindings — support both "tableBindings" and legacy "fieldBindings"+"bindingTableId" format
            List<Map<String, Object>> bindings = (List<Map<String, Object>>) formData.get("tableBindings");
            TableDefinition primaryTable = null;

            if (bindings != null) {
                for (Map<String, Object> bindingData : bindings) {
                    String tableName = (String) bindingData.get("tableName");
                    TableDefinition boundTable = tableMap.get(tableName);

                    BindingType bindingType;
                    try {
                        bindingType = BindingType.valueOf((String) bindingData.get("bindingType"));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        log.warn("Invalid binding type '{}', defaulting to PRIMARY", bindingData.get("bindingType"));
                        bindingType = BindingType.PRIMARY;
                    }

                    BindingMode bindingMode;
                    try {
                        bindingMode = BindingMode.valueOf((String) bindingData.get("bindingMode"));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        log.warn("Invalid binding mode '{}', defaulting to EDITABLE", bindingData.get("bindingMode"));
                        bindingMode = BindingMode.EDITABLE;
                    }

                    FormTableBinding binding = FormTableBinding.builder()
                            .form(form)
                            .table(boundTable)
                            .bindingType(bindingType)
                            .bindingMode(bindingMode)
                            .foreignKeyField((String) bindingData.get("foreignKeyField"))
                            .sortOrder(toInteger(bindingData.get("sortOrder")))
                            .build();

                    form.getTableBindings().add(binding);

                    // Track PRIMARY binding for backward compat boundTable field
                    if (bindingType == BindingType.PRIMARY && boundTable != null) {
                        primaryTable = boundTable;
                    }
                }
            } else {
                // Fallback: LLM may generate "bindingTableId" at form level instead of "tableBindings" array
                String bindingTableId = (String) formData.get("bindingTableId");
                if (bindingTableId != null) {
                    TableDefinition boundTable = tableMap.get(bindingTableId);
                    if (boundTable != null) {
                        FormTableBinding binding = FormTableBinding.builder()
                                .form(form)
                                .table(boundTable)
                                .bindingType(BindingType.PRIMARY)
                                .bindingMode(BindingMode.EDITABLE)
                                .build();
                        form.getTableBindings().add(binding);
                        primaryTable = boundTable;
                    }
                }
            }

            // Write fieldPermissions
            @SuppressWarnings("unchecked")
            Map<String, String> fieldPermissions = (Map<String, String>) formData.get("fieldPermissions");
            if (fieldPermissions != null) {
                form.setFieldPermissions(new HashMap<>(fieldPermissions));
            }

            // Write showLiveValues (default true if not provided, handled by @Builder.Default)
            Object showLiveValuesObj = formData.get("showLiveValues");
            if (showLiveValuesObj instanceof Boolean) {
                form.setShowLiveValues((Boolean) showLiveValuesObj);
            }

            // Write stageBindings
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stageBindingsData = (List<Map<String, Object>>) formData.get("stageBindings");
            if (stageBindingsData != null) {
                for (Map<String, Object> sbData : stageBindingsData) {
                    FormStageBinding stageBinding = FormStageBinding.builder()
                            .form(form)
                            .stageId((String) sbData.get("stageId"))
                            .stageName((String) sbData.get("stageName"))
                            .build();
                    form.getStageBindings().add(stageBinding);
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

            ActionType actionType;
            try {
                actionType = ActionType.valueOf((String) actionData.get("actionType"));
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Invalid action type '{}', skipping action", actionData.get("actionType"));
                continue;
            }

            ActionDefinition action = ActionDefinition.builder()
                    .functionUnit(functionUnit)
                    .actionName((String) actionData.get("actionName"))
                    .actionType(actionType)
                    .configJson(configJson)
                    .icon((String) actionData.get("icon"))
                    .buttonColor((String) actionData.get("buttonColor"))
                    .description((String) actionData.get("description"))
                    .isDefault(toBoolean(actionData.get("isDefault"), false))
                    .build();

            functionUnit.getActionDefinitions().add(action);
        }
    }

    private void writeDecisionDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        List<Map<String, Object>> decisionDefs = generatedData.getDecisionDefinitions();
        if (decisionDefs == null) return;

        for (Map<String, Object> decisionData : decisionDefs) {
            String decisionKey = (String) decisionData.get("decisionKey");
            if (decisionKey == null || decisionKey.isBlank()) {
                log.warn("Skipping decision definition without decisionKey");
                continue;
            }

            DecisionDefinition decision = DecisionDefinition.builder()
                    .functionUnit(functionUnit)
                    .decisionKey(decisionKey)
                    .decisionName((String) decisionData.get("decisionName"))
                    .dmnXml((String) decisionData.get("dmnXml"))
                    .hitPolicy((String) decisionData.get("hitPolicy"))
                    .description((String) decisionData.get("description"))
                    .build();

            functionUnit.getDecisionDefinitions().add(decision);
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

    private FormType mapLegacyFormType(String formTypeStr) {
        if (formTypeStr == null) return null;
        return switch (formTypeStr.toUpperCase()) {
            case "MAIN" -> FormType.PROCESS;
            case "SUB" -> FormType.TASK;
            default -> null;
        };
    }
}

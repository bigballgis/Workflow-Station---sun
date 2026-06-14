package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.entity.TableRelation;
import com.developer.enums.ActionType;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.SubMode;
import com.developer.enums.TableType;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.TableRelationRepository;
import com.developer.util.FormConfigJsonBindingIdRewriter;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能单元导入写入协作类。
 * 负责把解析后的导入数据落库为实体：表/外键/表关系/表单壳/表单绑定/动作/决策，
 * 以及对应的反序列化辅助逻辑。行为与拆分前逐字一致。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FunctionUnitImportWriter {

    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final DecisionDefinitionRepository decisionDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final TableRelationRepository tableRelationRepository;
    private final DmnXmlParser dmnXmlParser;
    private final ObjectMapper objectMapper;

    void recordSourceIdMapping(Object sourceIdObj, Long newId, Map<Long, Long> mapping) {
        if (sourceIdObj instanceof Number sourceId && newId != null) {
            mapping.put(sourceId.longValue(), newId);
        }
    }

    TableDefinition importTable(FunctionUnit functionUnit, Map<String, Object> tableData) {
        TableDefinition table = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName((String) tableData.get("tableName"))
                .tableType(TableType.valueOf((String) tableData.get("tableType")))
                .tableDisplayName((String) tableData.get("tableDisplayName"))
                .displayName((String) tableData.get("description"))
                .build();
        table = tableDefinitionRepository.save(table);

        // Import field definitions
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) tableData.get("fields");
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> fieldData = fields.get(i);
                Integer sortOrder = fieldData.get("sortOrder") instanceof Number number
                        ? number.intValue()
                        : i;
                Boolean nullable = fieldData.get("nullable") instanceof Boolean boolVal ? boolVal : true;
                Boolean isPrimaryKey = fieldData.get("isPrimaryKey") instanceof Boolean pkVal ? pkVal : false;
                Boolean isUnique = fieldData.get("isUnique") instanceof Boolean uniqueVal ? uniqueVal : false;
                FieldDefinition field = FieldDefinition.builder()
                        .tableDefinition(table)
                        .fieldName((String) fieldData.get("fieldName"))
                        .dataType(DataType.valueOf((String) fieldData.get("dataType")))
                        .length(fieldData.get("length") != null ? ((Number) fieldData.get("length")).intValue() : null)
                        .precision(fieldData.get("precision") != null ? ((Number) fieldData.get("precision")).intValue() : null)
                        .scale(fieldData.get("scale") != null ? ((Number) fieldData.get("scale")).intValue() : null)
                        .nullable(nullable)
                        .defaultValue((String) fieldData.get("defaultValue"))
                        .isPrimaryKey(isPrimaryKey)
                        .isUnique(isUnique)
                        .displayName((String) fieldData.get("displayName"))
                        .sortOrder(sortOrder)
                        .build();
                table.getFieldDefinitions().add(field);
            }
            tableDefinitionRepository.save(table);
        }
        return table;
    }

    void importForeignKeys(List<Map<String, Object>> tables,
                           Map<String, Long> importedTableNameToId,
                           Map<String, Map<String, FieldDefinition>> importedFieldLookup) {
        for (Map<String, Object> tableData : tables) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) tableData.get("foreignKeys");
            if (foreignKeys == null || foreignKeys.isEmpty()) {
                continue;
            }
            String tableName = (String) tableData.get("tableName");
            Long tableId = importedTableNameToId.get(tableName);
            if (tableId == null) {
                continue;
            }
            TableDefinition table = tableDefinitionRepository.findByIdWithFields(tableId)
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", tableId));
            Map<String, FieldDefinition> fieldByName = importedFieldLookup.getOrDefault(tableName, Map.of());
            for (Map<String, Object> fkData : foreignKeys) {
                String fieldName = (String) fkData.get("fieldName");
                String refTableName = (String) fkData.get("refTableName");
                String refFieldName = (String) fkData.get("refFieldName");
                FieldDefinition field = fieldByName.get(fieldName);
                Map<String, FieldDefinition> refFieldByName = importedFieldLookup.getOrDefault(refTableName, Map.of());
                FieldDefinition refField = refFieldByName.get(refFieldName);
                Long refTableId = importedTableNameToId.get(refTableName);
                if (field == null || refField == null || refTableId == null) {
                    log.warn("Skipping foreign key import for {}.{} -> {}.{} (missing field/table)",
                            tableName, fieldName, refTableName, refFieldName);
                    continue;
                }
                TableDefinition refTable = tableDefinitionRepository.getReferenceById(refTableId);
                ForeignKey foreignKey = ForeignKey.builder()
                        .tableDefinition(table)
                        .fieldDefinition(field)
                        .refTableDefinition(refTable)
                        .refFieldDefinition(refField)
                        .onDelete(fkData.get("onDelete") instanceof String onDelete ? onDelete : "NO ACTION")
                        .onUpdate(fkData.get("onUpdate") instanceof String onUpdate ? onUpdate : "NO ACTION")
                        .build();
                table.getForeignKeys().add(foreignKey);
            }
            if (!table.getForeignKeys().isEmpty()) {
                tableDefinitionRepository.save(table);
            }
        }
    }

    void importTableRelations(FunctionUnit functionUnit,
                              List<Map<String, Object>> tableRelations,
                              Map<String, Long> importedTableNameToId) {
        for (Map<String, Object> relationData : tableRelations) {
            String sourceTableName = (String) relationData.get("sourceTableName");
            String targetTableName = (String) relationData.get("targetTableName");
            Long sourceTableId = importedTableNameToId.get(sourceTableName);
            Long targetTableId = importedTableNameToId.get(targetTableName);
            if (sourceTableId == null || targetTableId == null) {
                log.warn("Skipping table relation import {} -> {} (table not found)",
                        sourceTableName, targetTableName);
                continue;
            }
            TableRelation relation = TableRelation.builder()
                    .functionUnit(functionUnit)
                    .sourceTableId(sourceTableId)
                    .sourceFieldName((String) relationData.get("sourceFieldName"))
                    .relationType((String) relationData.get("relationType"))
                    .targetTableId(targetTableId)
                    .targetFieldName((String) relationData.get("targetFieldName"))
                    .build();
            tableRelationRepository.save(relation);
        }
    }

    FormDefinition importFormShell(FunctionUnit functionUnit,
                                   Map<String, Object> formData,
                                   Map<String, Long> importedTableNameToId) {
        Map<String, Object> configJsonMap = parseConfigJsonObject(formData.get("configJson"));
        Map<String, String> fieldPermissions = parseFieldPermissions(formData.get("fieldPermissions"));
        Boolean showLiveValues = formData.get("showLiveValues") instanceof Boolean boolVal ? boolVal : true;

        FormDefinition form = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName((String) formData.get("formName"))
                .formType(FormType.valueOf((String) formData.get("formType")))
                .displayName((String) formData.get("description"))
                .configJson(configJsonMap != null ? configJsonMap : new HashMap<>())
                .fieldPermissions(fieldPermissions)
                .showLiveValues(showLiveValues)
                .build();

        String boundTableName = (String) formData.get("boundTableName");
        if (boundTableName != null && importedTableNameToId.containsKey(boundTableName)) {
            form.setBoundTable(tableDefinitionRepository.getReferenceById(importedTableNameToId.get(boundTableName)));
        }

        return formDefinitionRepository.save(form);
    }

    void finalizeFormImport(FormDefinition form,
                            Map<String, Object> formData,
                            Map<String, Long> importedTableNameToId) {
        Map<Long, Long> bindingIdMapping = importFormTableBindings(form, formData, importedTableNameToId);

        Map<String, Object> configJson = parseConfigJsonObject(formData.get("configJson"));
        if (configJson == null) {
            configJson = form.getConfigJson() != null ? new HashMap<>(form.getConfigJson()) : new HashMap<>();
        } else {
            configJson = new HashMap<>(configJson);
        }
        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, bindingIdMapping);
        form.setConfigJson(configJson);

        importFormStageBindings(form, formData);
        formDefinitionRepository.save(form);
    }

    private Map<Long, Long> importFormTableBindings(FormDefinition form,
                                                    Map<String, Object> formData,
                                                    Map<String, Long> importedTableNameToId) {
        Map<Long, Long> bindingIdMapping = new HashMap<>();
        Object bindingsObj = formData.get("tableBindings");
        if (!(bindingsObj instanceof List<?> bindingsList)) {
            return bindingIdMapping;
        }
        for (Object bindingObj : bindingsList) {
            if (!(bindingObj instanceof Map<?, ?> bindingMapRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> bindingData = (Map<String, Object>) bindingMapRaw;
            BindingType bindingType = BindingType.valueOf((String) bindingData.get("bindingType"));
            BindingMode bindingMode = BindingMode.valueOf((String) bindingData.get("bindingMode"));

            TableDefinition table = null;
            String tableName = (String) bindingData.get("tableName");
            if (tableName != null && importedTableNameToId.containsKey(tableName)) {
                table = tableDefinitionRepository.getReferenceById(importedTableNameToId.get(tableName));
            }

            Long relationTableId = bindingData.get("relationTableId") instanceof Number number
                    ? number.longValue() : null;
            Integer sortOrder = bindingData.get("sortOrder") instanceof Number number
                    ? number.intValue() : null;
            SubMode subMode = bindingData.get("subMode") instanceof String subModeStr
                    ? SubMode.valueOf(subModeStr) : null;

            FormTableBinding binding = FormTableBinding.builder()
                    .form(form)
                    .table(table)
                    .relationTableId(relationTableId)
                    .bindingType(bindingType)
                    .bindingMode(bindingMode)
                    .foreignKeyField((String) bindingData.get("foreignKeyField"))
                    .sortOrder(sortOrder)
                    .subMode(subMode)
                    .build();
            FormTableBinding savedBinding = formTableBindingRepository.save(binding);

            if (bindingData.get("bindingId") instanceof Number sourceBindingId) {
                bindingIdMapping.put(sourceBindingId.longValue(), savedBinding.getId());
            }
        }
        return bindingIdMapping;
    }

    private void importFormStageBindings(FormDefinition form, Map<String, Object> formData) {
        Object stageBindingsObj = formData.get("stageBindings");
        if (!(stageBindingsObj instanceof List<?> stageBindingsList)) {
            return;
        }
        for (Object stageObj : stageBindingsList) {
            if (!(stageObj instanceof Map<?, ?> stageMapRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> stageData = (Map<String, Object>) stageMapRaw;
            Boolean readOnly = stageData.get("readOnly") instanceof Boolean boolVal ? boolVal : false;
            FormStageBinding stageBinding = FormStageBinding.builder()
                    .form(form)
                    .stageId((String) stageData.get("stageId"))
                    .stageName((String) stageData.get("stageName"))
                    .readOnly(readOnly)
                    .build();
            form.getStageBindings().add(stageBinding);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJsonObject(Object configJsonObj) {
        if (configJsonObj instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        if (configJsonObj instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse configJson string: {}", e.getMessage());
            }
        }
        return null;
    }

    private Map<String, String> parseFieldPermissions(Object fieldPermissionsObj) {
        if (fieldPermissionsObj instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result;
        }
        return new HashMap<>();
    }

    ActionDefinition importAction(FunctionUnit functionUnit, Map<String, Object> actionData) {
        Map<String, Object> configJsonMap = parseConfigJsonObject(actionData.get("configJson"));
        ActionDefinition action = ActionDefinition.builder()
                .functionUnit(functionUnit)
                .actionName((String) actionData.get("actionName"))
                .actionType(ActionType.valueOf((String) actionData.get("actionType")))
                .configJson(configJsonMap != null ? configJsonMap : new HashMap<>())
                .build();
        return actionDefinitionRepository.save(action);
    }

    /**
     * Import decision definitions (from DMN XML)
     * Use DmnXmlParser for decisionKey/hitPolicy; name from XML decision element
     * Conflict: overwrite same decisionKey within functionUnit
     */
    void importDecision(FunctionUnit functionUnit, String dmnXml) {
        if (dmnXml == null || dmnXml.isBlank()) {
            log.warn("Skipping empty DMN XML during import");
            return;
        }

        String decisionKey = dmnXmlParser.extractDecisionKey(dmnXml);
        if (decisionKey == null || decisionKey.isBlank()) {
            log.warn("Skipping DMN XML without decision key during import");
            return;
        }

        String hitPolicy = dmnXmlParser.extractHitPolicy(dmnXml);

        // Extract decision name from the model
        String decisionName = null;
        try {
            var model = dmnXmlParser.parseToModel(dmnXml);
            if (model != null && model.getDecisionName() != null) {
                decisionName = model.getDecisionName();
            }
        } catch (Exception e) {
            log.warn("Failed to parse decision name from DMN XML for key {}: {}", decisionKey, e.getMessage());
        }

        // Handle conflict: overwrite if same decisionKey exists in this functionUnit
        List<DecisionDefinition> existing = decisionDefinitionRepository.findByFunctionUnitId(functionUnit.getId());
        existing.stream()
                .filter(d -> decisionKey.equals(d.getDecisionKey()))
                .findFirst()
                .ifPresent(d -> decisionDefinitionRepository.deleteById(d.getId()));

        DecisionDefinition decision = DecisionDefinition.builder()
                .functionUnit(functionUnit)
                .decisionKey(decisionKey)
                .decisionName(decisionName)
                .dmnXml(dmnXml)
                .hitPolicy(hitPolicy)
                .build();
        decisionDefinitionRepository.save(decision);
    }
}

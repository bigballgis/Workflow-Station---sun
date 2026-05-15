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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 数据写入服务实现
 * 全量替换写入策略：MODIFY 模式下先删除旧数据再写入新数据，NEW 模式下直接写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiWriteServiceImpl implements AiWriteService {
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile(
            "<(?:bpmn:)?process\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern DEFINITIONS_OPEN_TAG_PATTERN = Pattern.compile(
            "<((?:bpmn:)?definitions)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);


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
            if (configJson == null) {
                configJson = new HashMap<>();
            }

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

            // AI often returns empty configJson or rule: [] — Form Design canvas would be blank.
            ensureAiFormCreateLayout(configJson, primaryTable);

            // Backward compat: set boundTable to the PRIMARY binding's table
            form.setBoundTable(primaryTable);
            functionUnit.getFormDefinitions().add(form);
        }
    }

    /**
     * Ensure form-create skeleton and backfill {@code rule} from PRIMARY table fields when AI left rules empty.
     */
    @SuppressWarnings("unchecked")
    private void ensureAiFormCreateLayout(Map<String, Object> configJson, TableDefinition primaryTable) {
        ensureFormConfigJsonStructure(configJson);
        Object ruleObj = configJson.get("rule");
        boolean ruleEmpty = !(ruleObj instanceof List) || ((List<?>) ruleObj).isEmpty();
        if (!ruleEmpty) {
            return;
        }
        if (primaryTable == null || primaryTable.getFieldDefinitions() == null
                || primaryTable.getFieldDefinitions().isEmpty()) {
            return;
        }
        List<FieldDefinition> fields = new ArrayList<>(primaryTable.getFieldDefinitions());
        fields.sort(Comparator.comparingInt(f -> f.getSortOrder() != null ? f.getSortOrder() : 0));
        List<Map<String, Object>> rules = new ArrayList<>();
        for (FieldDefinition field : fields) {
            rules.add(fieldToFormCreateRule(field));
        }
        configJson.put("rule", rules);
        log.info("Backfilled {} form-create rules from table '{}' for AI-generated form",
                rules.size(), primaryTable.getTableName());
    }

    @SuppressWarnings("unchecked")
    private void ensureFormConfigJsonStructure(Map<String, Object> configJson) {
        if (!(configJson.get("rule") instanceof List)) {
            configJson.put("rule", new ArrayList<>());
        }
        if (!(configJson.get("options") instanceof Map) || ((Map<?, ?>) configJson.get("options")).isEmpty()) {
            configJson.put("options", defaultFormCreateOptions());
        }
        if (!(configJson.get("subForms") instanceof Map)) {
            configJson.put("subForms", new HashMap<String, Object>());
        }
        if (!(configJson.get("subListViews") instanceof Map)) {
            configJson.put("subListViews", new HashMap<String, Object>());
        }
        if (!(configJson.get("relationViews") instanceof Map)) {
            configJson.put("relationViews", new HashMap<String, Object>());
        }
        if (!(configJson.get("subTablePortalViews") instanceof Map)) {
            configJson.put("subTablePortalViews", new HashMap<String, Object>());
        }
    }

    private Map<String, Object> defaultFormCreateOptions() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("size", "default");
        form.put("inline", false);
        form.put("labelWidth", "125px");
        form.put("labelPosition", "left");
        form.put("hideRequiredAsterisk", false);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("form", form);
        options.put("language", Map.of("en", Map.of("clickToUpload", "Upload")));
        options.put("resetBtn", Map.of("show", false, "innerText", "Reset"));
        options.put("submitBtn", Map.of("show", true, "innerText", "Submit"));
        return options;
    }

    private Map<String, Object> fieldToFormCreateRule(FieldDefinition field) {
        String title = field.getDescription() != null && !field.getDescription().isBlank()
                ? field.getDescription()
                : field.getFieldName();
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("field", field.getFieldName());
        rule.put("title", title);
        Map<String, Object> props = new LinkedHashMap<>();
        List<Map<String, Object>> validate = new ArrayList<>();
        rule.put("props", props);
        rule.put("validate", validate);
        if (Boolean.FALSE.equals(field.getNullable())) {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("required", true);
            req.put("message", title + " is required");
            req.put("trigger", "blur");
            validate.add(req);
        }
        DataType dt = field.getDataType();
        if (dt == null) {
            dt = DataType.VARCHAR;
        }
        switch (dt) {
            case VARCHAR -> {
                rule.put("type", "input");
                props.put("placeholder", "Enter " + title);
                if (field.getLength() != null) {
                    props.put("maxlength", field.getLength());
                    props.put("showWordLimit", true);
                } else {
                    props.put("maxlength", 255);
                    props.put("showWordLimit", true);
                }
            }
            case TEXT -> {
                rule.put("type", "input");
                props.put("type", "textarea");
                props.put("placeholder", "Enter " + title);
                props.put("rows", 3);
            }
            case INTEGER, BIGINT -> {
                rule.put("type", "inputNumber");
                props.put("placeholder", "Enter " + title);
                props.put("precision", 0);
            }
            case DECIMAL -> {
                rule.put("type", "inputNumber");
                props.put("placeholder", "Enter " + title);
                props.put("precision", field.getScale() != null ? field.getScale() : 2);
            }
            case BOOLEAN -> {
                rule.put("type", "switch");
            }
            case DATE -> {
                rule.put("type", "datePicker");
                props.put("type", "date");
                props.put("placeholder", "Select " + title);
                props.put("valueFormat", "YYYY-MM-DD");
            }
            case TIME -> {
                rule.put("type", "datePicker");
                props.put("type", "time");
                props.put("placeholder", "Select " + title);
            }
            case TIMESTAMP -> {
                rule.put("type", "datePicker");
                props.put("type", "datetime");
                props.put("placeholder", "Select " + title);
                props.put("valueFormat", "YYYY-MM-DD HH:mm:ss");
            }
            case JSON -> {
                rule.put("type", "input");
                props.put("type", "textarea");
                props.put("placeholder", "JSON " + title);
                props.put("rows", 4);
            }
            case FILE -> {
                rule.put("type", "upload");
                props.put("action", "/api/v1/upload");
                props.put("accept", ".jpg,.jpeg,.png,.pdf,.docx,.xlsx");
                props.put("limit", 1);
                props.put("multiple", false);
                props.put("listType", "text");
            }
            case BYTEA -> {
                rule.put("type", "input");
                props.put("disabled", true);
                props.put("placeholder", "(binary)");
            }
            default -> {
                rule.put("type", "input");
                props.put("placeholder", "Enter " + title);
            }
        }
        return rule;
    }

    @SuppressWarnings("unchecked")
    private void writeActionDefinitions(FunctionUnit functionUnit, AiGeneratedData generatedData) {
        List<Map<String, Object>> actionDefs = generatedData.getActionDefinitions();
        if (actionDefs == null) return;

        for (Map<String, Object> actionData : actionDefs) {
            Map<String, Object> configJson = (Map<String, Object>) actionData.get("configJson");
            if (configJson == null) {
                configJson = new HashMap<>();
            }

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
        bpmnXml = ensureRenderableBpmnDiagram(bpmnXml);

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .functionUnit(functionUnit)
                .functionUnitVersionId(functionUnit.getId())
                .bpmnXml(bpmnXml)
                .build();

        functionUnit.setProcessDefinition(processDefinition);
    }

    /**
     * AI may output BPMN semantic XML without BPMN DI section (BPMNDiagram/BPMNPlane),
     * which causes bpmn-js to fail with "no diagram to display". Append a minimal DI
     * section and missing namespaces when needed so the designer can render safely.
     */
    private String ensureRenderableBpmnDiagram(String bpmnXml) {
        String normalized = bpmnXml;
        if (containsIgnoreCase(normalized, "BPMNDiagram")) {
            return normalized;
        }

        String processId = extractProcessId(normalized);
        if (processId == null || processId.isBlank()) {
            return normalized;
        }

        normalized = ensureDiagramNamespaces(normalized);
        String diagramXml = """
                <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                  <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="%s" />
                </bpmndi:BPMNDiagram>
                """.formatted(processId);

        if (normalized.contains("</bpmn:definitions>")) {
            return normalized.replace("</bpmn:definitions>", diagramXml + "\n</bpmn:definitions>");
        }
        if (normalized.contains("</definitions>")) {
            return normalized.replace("</definitions>", diagramXml + "\n</definitions>");
        }
        return normalized;
    }

    private String extractProcessId(String bpmnXml) {
        Matcher matcher = PROCESS_ID_PATTERN.matcher(bpmnXml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String ensureDiagramNamespaces(String bpmnXml) {
        Matcher matcher = DEFINITIONS_OPEN_TAG_PATTERN.matcher(bpmnXml);
        if (!matcher.find()) {
            return bpmnXml;
        }

        String tagName = matcher.group(1);
        String attrs = matcher.group(2);
        String updatedAttrs = attrs;
        if (!updatedAttrs.contains("xmlns:bpmndi")) {
            updatedAttrs += " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"";
        }
        if (!updatedAttrs.contains("xmlns:dc")) {
            updatedAttrs += " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"";
        }
        if (!updatedAttrs.contains("xmlns:di")) {
            updatedAttrs += " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"";
        }

        String replacement = "<" + tagName + updatedAttrs + ">";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private boolean containsIgnoreCase(String text, String token) {
        return text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
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

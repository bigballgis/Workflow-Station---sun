package com.developer.service.impl;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.enums.*;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 生成数据的结构校验协作类
 * <p>
 * 负责枚举值、字段约束、表/表单/动作/决策定义、表关系、引用完整性、唯一性等结构性校验。
 * DMN XML 安全校验委托 {@link AiSecurityValidator}。
 * 由 {@link AiValidationServiceImpl} 门面委托调用，校验规则逻辑与原实现逐字保持一致。
 */
@Component
public class AiStructureValidator {

    private final AiSecurityValidator securityValidator;

    public AiStructureValidator(AiSecurityValidator securityValidator) {
        this.securityValidator = securityValidator;
    }

    @SuppressWarnings("unchecked")
    void validateTableDefinitions(List<Map<String, Object>> tableDefinitions, AiValidationResult result) {
        if (tableDefinitions == null) return;
        for (int i = 0; i < tableDefinitions.size(); i++) {
            Map<String, Object> table = tableDefinitions.get(i);
            validateEnumValue(table.get("tableType"), TableType.class,
                    "tableDefinitions[" + i + "].tableType", result);

            // Support both "fieldDefinitions" and "fields" key names
            List<Map<String, Object>> fields = (List<Map<String, Object>>) table.get("fieldDefinitions");
            if (fields == null) {
                fields = (List<Map<String, Object>>) table.get("fields");
            }
            if (fields != null) {
                boolean hasPrimaryKey = false;
                for (int j = 0; j < fields.size(); j++) {
                    Map<String, Object> field = fields.get(j);
                    String fieldPath = "tableDefinitions[" + i + "].fieldDefinitions[" + j + "]";

                    validateEnumValue(field.get("dataType"), DataType.class,
                            fieldPath + ".dataType", result);

                    String dataType = field.get("dataType") != null ? field.get("dataType").toString() : null;

                    if ("DECIMAL".equals(dataType)) {
                        if (field.get("precision") == null || toInt(field.get("precision")) <= 0) {
                            result.addError("FIELD_CONSTRAINT", fieldPath + ".precision",
                                    "DECIMAL type requires precision > 0");
                        }
                        if (field.get("scale") == null || toInt(field.get("scale")) < 0) {
                            result.addError("FIELD_CONSTRAINT", fieldPath + ".scale",
                                    "DECIMAL type requires scale >= 0");
                        }
                    }

                    if ("VARCHAR".equals(dataType)) {
                        if (field.get("length") == null || toInt(field.get("length")) <= 0) {
                            result.addError("FIELD_CONSTRAINT", fieldPath + ".length",
                                    "VARCHAR type requires length > 0");
                        }
                    }

                    Boolean isPrimaryKey = (Boolean) field.get("isPrimaryKey");
                    if (isPrimaryKey == null) {
                        isPrimaryKey = (Boolean) field.get("primaryKey");
                    }
                    if (Boolean.TRUE.equals(isPrimaryKey)) {
                        hasPrimaryKey = true;
                    }
                }
                if (!hasPrimaryKey) {
                    result.addError("FIELD_CONSTRAINT", "tableDefinitions[" + i + "]",
                            "Table definition must contain at least one primary key field");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    void validateFormDefinitions(List<Map<String, Object>> formDefinitions, AiValidationResult result) {
        if (formDefinitions == null) return;
        for (int i = 0; i < formDefinitions.size(); i++) {
            Map<String, Object> form = formDefinitions.get(i);
            String formPath = "formDefinitions[" + i + "]";

            // Task 4.6: 旧版 FormType 校验兼容（产生警告而非错误）
            String formTypeStr = (String) form.get("formType");
            if (formTypeStr != null) {
                try {
                    FormType.valueOf(formTypeStr);
                } catch (IllegalArgumentException e) {
                    if ("MAIN".equals(formTypeStr) || "SUB".equals(formTypeStr)) {
                        String mapped = "MAIN".equals(formTypeStr) ? "PROCESS" : "TASK";
                        result.addWarning("DEPRECATED_ENUM", formPath + ".formType",
                                "Deprecated form type '" + formTypeStr + "', will be auto-mapped to '" + mapped + "'");
                    } else {
                        result.addError("INVALID_ENUM", formPath + ".formType",
                                "Invalid enum value: " + formTypeStr);
                    }
                }
            }

            List<Map<String, Object>> bindings = (List<Map<String, Object>>) form.get("tableBindings");
            // Skip binding validation if LLM used legacy format (bindingTableId)
            if (bindings != null) {
                for (int j = 0; j < bindings.size(); j++) {
                    Map<String, Object> binding = bindings.get(j);
                    String bindingPath = formPath + ".tableBindings[" + j + "]";
                    validateEnumValue(binding.get("bindingType"), BindingType.class,
                            bindingPath + ".bindingType", result);
                    validateEnumValue(binding.get("bindingMode"), BindingMode.class,
                            bindingPath + ".bindingMode", result);
                }
            }

            // Task 4.2: configJson 扩展字段校验
            Map<String, Object> configJson = (Map<String, Object>) form.get("configJson");
            validateConfigJsonExtensions(configJson, formPath, result);

            // Task 4.3: fieldPermissions 值校验
            Map<String, String> fieldPermissions = (Map<String, String>) form.get("fieldPermissions");
            if (fieldPermissions != null) {
                Set<String> validPermissions = Set.of("READONLY", "EDITABLE");
                for (Map.Entry<String, String> entry : fieldPermissions.entrySet()) {
                    if (!validPermissions.contains(entry.getValue())) {
                        result.addError("INVALID_ENUM",
                                formPath + ".fieldPermissions." + entry.getKey(),
                                "Invalid permission value: " + entry.getValue() + ", must be READONLY or EDITABLE");
                    }
                }
            }

            // Task 4.3: showLiveValues 类型校验
            Object showLiveValues = form.get("showLiveValues");
            if (showLiveValues != null && !(showLiveValues instanceof Boolean)) {
                result.addError("FIELD_CONSTRAINT", formPath + ".showLiveValues",
                        "showLiveValues must be a Boolean");
            }

            // Task 4.3: Task Form 缺少 fieldPermissions 警告
            if ("TASK".equals(formTypeStr) && (fieldPermissions == null || fieldPermissions.isEmpty())) {
                result.addWarning("BEST_PRACTICE", formPath + ".fieldPermissions",
                        "Task Form typically requires fieldPermissions configuration");
            }
        }
    }

    void validateActionDefinitions(List<Map<String, Object>> actionDefinitions, AiValidationResult result) {
        if (actionDefinitions == null) return;
        for (int i = 0; i < actionDefinitions.size(); i++) {
            Map<String, Object> action = actionDefinitions.get(i);
            validateEnumValue(action.get("actionType"), ActionType.class,
                    "actionDefinitions[" + i + "].actionType", result);

            // Task 4.4: visibilityCondition 格式校验
            @SuppressWarnings("unchecked")
            Map<String, Object> actionConfig = (Map<String, Object>) action.get("configJson");
            if (actionConfig != null) {
                Object visibilityCondition = actionConfig.get("visibilityCondition");
                if (visibilityCondition != null) {
                    if (visibilityCondition instanceof String) {
                        result.addError("FORMAT_MISMATCH",
                                "actionDefinitions[" + i + "].configJson.visibilityCondition",
                                "visibilityCondition must be a ConditionExpression object {field, operator, value}, not a string");
                    } else if (visibilityCondition instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> condition = (Map<String, Object>) visibilityCondition;
                        Set<String> validOperators = Set.of("equals", "not-equals", "contains",
                                "greater-than", "less-than", "is-empty", "is-not-empty");
                        String operator = (String) condition.get("operator");
                        if (operator != null && !validOperators.contains(operator)) {
                            result.addError("INVALID_ENUM",
                                    "actionDefinitions[" + i + "].configJson.visibilityCondition.operator",
                                    "Invalid operator: " + operator);
                        }
                    }
                }
            }
        }
    }

    /**
     * 校验 configJson 业务逻辑扩展字段
     * 检查 formulas/linkages/crossFieldRules/summaryRules 结构
     */
    @SuppressWarnings("unchecked")
    void validateConfigJsonExtensions(Map<String, Object> configJson, String formPath, AiValidationResult result) {
        if (configJson == null) return;

        // formulas 校验
        List<Map<String, Object>> formulas = (List<Map<String, Object>>) configJson.get("formulas");
        if (formulas != null) {
            for (int i = 0; i < formulas.size(); i++) {
                Map<String, Object> formula = formulas.get(i);
                String path = formPath + ".configJson.formulas[" + i + "]";
                String targetField = (String) formula.get("targetField");
                if (targetField == null || targetField.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".targetField", "Formula targetField must not be empty");
                }
                String expression = (String) formula.get("expression");
                if (expression == null || expression.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".expression", "Formula expression must not be empty");
                }
                Object dependsOn = formula.get("dependsOn");
                if (dependsOn == null || !(dependsOn instanceof List) || ((List<?>) dependsOn).isEmpty()) {
                    result.addError("FIELD_CONSTRAINT", path + ".dependsOn", "Formula dependsOn must be a non-empty array");
                }
            }
        }

        // linkages 校验
        Set<String> validLinkageTypes = Set.of("option-filtering", "value-auto-fill", "field-state-change");
        List<Map<String, Object>> linkages = (List<Map<String, Object>>) configJson.get("linkages");
        if (linkages != null) {
            for (int i = 0; i < linkages.size(); i++) {
                Map<String, Object> linkage = linkages.get(i);
                String path = formPath + ".configJson.linkages[" + i + "]";
                String sourceField = (String) linkage.get("sourceField");
                if (sourceField == null || sourceField.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".sourceField", "Linkage sourceField must not be empty");
                }
                String targetField = (String) linkage.get("targetField");
                if (targetField == null || targetField.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".targetField", "Linkage targetField must not be empty");
                }
                String linkageType = (String) linkage.get("linkageType");
                if (linkageType != null && !validLinkageTypes.contains(linkageType)) {
                    result.addError("INVALID_ENUM", path + ".linkageType", "Invalid linkage type: " + linkageType);
                }
            }
        }

        // crossFieldRules 校验
        List<Map<String, Object>> crossFieldRules = (List<Map<String, Object>>) configJson.get("crossFieldRules");
        if (crossFieldRules != null) {
            for (int i = 0; i < crossFieldRules.size(); i++) {
                Map<String, Object> rule = crossFieldRules.get(i);
                String path = formPath + ".configJson.crossFieldRules[" + i + "]";
                Object fields = rule.get("fields");
                if (fields == null || !(fields instanceof List) || ((List<?>) fields).isEmpty()) {
                    result.addError("FIELD_CONSTRAINT", path + ".fields", "CrossFieldRule fields must be a non-empty array");
                }
                String message = (String) rule.get("message");
                if (message == null || message.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".message", "CrossFieldRule message must not be empty");
                }
                String targetField = (String) rule.get("targetField");
                if (targetField == null || targetField.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".targetField", "CrossFieldRule targetField must not be empty");
                }
            }
        }

        // summaryRules 校验
        Set<String> validAggregations = Set.of("SUM", "AVG", "COUNT", "MIN", "MAX");
        List<Map<String, Object>> summaryRules = (List<Map<String, Object>>) configJson.get("summaryRules");
        if (summaryRules != null) {
            for (int i = 0; i < summaryRules.size(); i++) {
                Map<String, Object> rule = summaryRules.get(i);
                String path = formPath + ".configJson.summaryRules[" + i + "]";
                String sourceColumn = (String) rule.get("sourceColumn");
                if (sourceColumn == null || sourceColumn.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".sourceColumn", "SummaryRule sourceColumn must not be empty");
                }
                String targetField = (String) rule.get("targetField");
                if (targetField == null || targetField.isBlank()) {
                    result.addError("FIELD_CONSTRAINT", path + ".targetField", "SummaryRule targetField must not be empty");
                }
                String aggregation = (String) rule.get("aggregation");
                if (aggregation != null && !validAggregations.contains(aggregation)) {
                    result.addError("INVALID_ENUM", path + ".aggregation", "Invalid aggregation: " + aggregation);
                }
            }
        }
    }

    /**
     * 校验决策定义数据
     * 检查 decisionKey 非空/长度、hitPolicy 合法值、dmnXml 安全性
     */
    void validateDecisionDefinitions(List<Map<String, Object>> decisionDefinitions, AiValidationResult result) {
        if (decisionDefinitions == null) return;

        Set<String> validHitPolicies = Set.of("FIRST", "UNIQUE", "PRIORITY", "ANY", "COLLECT", "RULE_ORDER", "OUTPUT_ORDER");

        for (int i = 0; i < decisionDefinitions.size(); i++) {
            Map<String, Object> decision = decisionDefinitions.get(i);
            String path = "decisionDefinitions[" + i + "]";

            // decisionKey 非空且长度限制
            String decisionKey = (String) decision.get("decisionKey");
            if (decisionKey == null || decisionKey.isBlank()) {
                result.addError("FIELD_CONSTRAINT", path + ".decisionKey", "decisionKey must not be empty");
            } else if (decisionKey.length() > 100) {
                result.addError("FIELD_CONSTRAINT", path + ".decisionKey", "decisionKey must not exceed 100 characters");
            }

            // hitPolicy 合法值
            String hitPolicy = (String) decision.get("hitPolicy");
            if (hitPolicy != null && !validHitPolicies.contains(hitPolicy)) {
                result.addError("INVALID_ENUM", path + ".hitPolicy", "Invalid hit policy: " + hitPolicy);
            }

            // dmnXml 安全校验
            String dmnXml = (String) decision.get("dmnXml");
            securityValidator.validateDmnXml(dmnXml, path, result);
        }
    }

    /**
     * 校验表关系数据
     * 检查 relationType 合法值、sourceFieldName/targetFieldName 非空、引用完整性
     */
    void validateTableRelations(AiGeneratedData generatedData, AiValidationResult result) {
        List<Map<String, Object>> tableRelations = generatedData.getTableRelations();
        if (tableRelations == null) return;

        Set<String> validRelationTypes = Set.of("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_MANY");

        // 构建表名集合用于引用完整性校验
        Set<String> tableNames = new HashSet<>();
        if (generatedData.getTableDefinitions() != null) {
            for (Map<String, Object> table : generatedData.getTableDefinitions()) {
                String name = (String) table.get("tableName");
                if (name != null) tableNames.add(name);
            }
        }

        for (int i = 0; i < tableRelations.size(); i++) {
            Map<String, Object> relation = tableRelations.get(i);
            String path = "tableRelations[" + i + "]";

            // relationType 合法值
            String relationType = (String) relation.get("relationType");
            if (relationType != null && !validRelationTypes.contains(relationType)) {
                result.addError("INVALID_ENUM", path + ".relationType", "Invalid relation type: " + relationType);
            }

            // sourceFieldName / targetFieldName 非空
            String sourceFieldName = (String) relation.get("sourceFieldName");
            if (sourceFieldName == null || sourceFieldName.isBlank()) {
                result.addError("FIELD_CONSTRAINT", path + ".sourceFieldName", "sourceFieldName must not be empty");
            }
            String targetFieldName = (String) relation.get("targetFieldName");
            if (targetFieldName == null || targetFieldName.isBlank()) {
                result.addError("FIELD_CONSTRAINT", path + ".targetFieldName", "targetFieldName must not be empty");
            }

            // 引用完整性：sourceTableName / targetTableName 必须存在于 tableDefinitions
            String sourceTableName = (String) relation.get("sourceTableName");
            if (sourceTableName != null && !tableNames.contains(sourceTableName)) {
                result.addError("REFERENCE_INTEGRITY", path + ".sourceTableName",
                        "Referenced table '" + sourceTableName + "' does not exist in tableDefinitions");
            }
            String targetTableName = (String) relation.get("targetTableName");
            if (targetTableName != null && !tableNames.contains(targetTableName)) {
                result.addError("REFERENCE_INTEGRITY", path + ".targetTableName",
                        "Referenced table '" + targetTableName + "' does not exist in tableDefinitions");
            }
        }
    }

    void validateIcon(Map<String, Object> icon, AiValidationResult result) {
        if (icon == null) return;
        validateEnumValue(icon.get("category"), IconCategory.class, "icon.category", result);
    }

    private <E extends Enum<E>> void validateEnumValue(Object value, Class<E> enumClass,
                                                        String fieldPath, AiValidationResult result) {
        if (value == null) return;
        String strValue = value.toString();
        try {
            Enum.valueOf(enumClass, strValue);
        } catch (IllegalArgumentException e) {
            result.addError("INVALID_ENUM", fieldPath, "Invalid enum value: " + strValue);
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}

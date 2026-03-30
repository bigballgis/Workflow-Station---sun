package com.developer.service.impl;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiQualityScore;
import com.developer.dto.AiValidationResult;
import com.developer.enums.*;
import com.developer.service.AiValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 生成数据校验服务实现
 */
@Slf4j
@Service
public class AiValidationServiceImpl implements AiValidationService {

    @Override
    public AiValidationResult validate(AiGeneratedData generatedData) {
        AiValidationResult result = AiValidationResult.builder().build();

        if (generatedData == null) {
            result.addError("NULL_DATA", "generatedData", "Generated data must not be null");
            return result;
        }

        // Enum value and field constraint validation
        validateTableDefinitions(generatedData.getTableDefinitions(), result);
        validateFormDefinitions(generatedData.getFormDefinitions(), result);
        validateActionDefinitions(generatedData.getActionDefinitions(), result);
        validateDecisionDefinitions(generatedData.getDecisionDefinitions(), result);
        validateTableRelations(generatedData, result);
        validateIcon(generatedData.getIcon(), result);

        // SVG security validation and BPMN XML validation
        validateSvg(generatedData.getIcon(), result);
        validateBpmnXml(generatedData.getProcessDefinition(), result);

        // Reference integrity and uniqueness validation
        validateReferenceIntegrity(generatedData, result);
        validateUniqueness(generatedData, result);

        return result;
    }

    @SuppressWarnings("unchecked")
    private void validateTableDefinitions(List<Map<String, Object>> tableDefinitions, AiValidationResult result) {
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
    private void validateFormDefinitions(List<Map<String, Object>> formDefinitions, AiValidationResult result) {
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

    private void validateActionDefinitions(List<Map<String, Object>> actionDefinitions, AiValidationResult result) {
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
    private void validateConfigJsonExtensions(Map<String, Object> configJson, String formPath, AiValidationResult result) {
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
    private void validateDecisionDefinitions(List<Map<String, Object>> decisionDefinitions, AiValidationResult result) {
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
            if (dmnXml != null && !dmnXml.isBlank()) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new InputSource(new StringReader(dmnXml)));
                    // 检查危险标签
                    for (String tag : new String[]{"script", "iframe"}) {
                        if (doc.getElementsByTagName(tag).getLength() > 0) {
                            result.addError("DMN_VALIDATION", path + ".dmnXml",
                                    "DMN XML contains dangerous tag: <" + tag + ">");
                        }
                    }
                } catch (Exception e) {
                    result.addError("DMN_VALIDATION", path + ".dmnXml",
                            "DMN XML is not valid: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 校验表关系数据
     * 检查 relationType 合法值、sourceFieldName/targetFieldName 非空、引用完整性
     */
    private void validateTableRelations(AiGeneratedData generatedData, AiValidationResult result) {
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

    private void validateIcon(Map<String, Object> icon, AiValidationResult result) {
        if (icon == null) return;
        validateEnumValue(icon.get("category"), IconCategory.class, "icon.category", result);
    }

    private void validateSvg(Map<String, Object> icon, AiValidationResult result) {
        if (icon == null) return;

        // Validate icon.name length
        String name = (String) icon.get("name");
        if (name != null && name.length() > 100) {
            result.addError("SVG_VALIDATION", "icon.name", "Icon name must not exceed 100 characters");
        }

        String svgContent = (String) icon.get("svgContent");
        if (svgContent == null || svgContent.isBlank()) return;

        // Check size ≤ 10KB
        if (svgContent.getBytes(StandardCharsets.UTF_8).length > 10240) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG content must not exceed 10KB");
        }

        // Parse XML
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(svgContent)));

            // Root element must be <svg>
            if (!"svg".equals(doc.getDocumentElement().getTagName())) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG root element must be <svg>");
            }

            // Check for dangerous tags
            String[] dangerousTags = {"script", "iframe", "object", "embed"};
            for (String tag : dangerousTags) {
                if (doc.getElementsByTagName(tag).getLength() > 0) {
                    result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains dangerous tag: <" + tag + ">");
                }
            }

            // Check for on* event attributes and javascript: protocol
            checkDangerousAttributes(doc.getDocumentElement(), result);

        } catch (Exception e) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG is not valid XML: " + e.getMessage());
        }
    }

    private void checkDangerousAttributes(Element element, AiValidationResult result) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrName = attr.getNodeName().toLowerCase();
            String attrValue = attr.getNodeValue().toLowerCase().trim();

            if (attrName.startsWith("on")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains event attribute: " + attrName);
            }
            if (attrValue.contains("javascript:")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains javascript: protocol reference");
            }
            if (attrValue.contains("url(") && (attrValue.contains("http:") || attrValue.contains("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains external resource reference");
            }
            // Check xlink:href with external URLs
            if (attrName.contains("href") && (attrValue.startsWith("http:") || attrValue.startsWith("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains external resource reference");
            }
        }

        // Recursively check child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                checkDangerousAttributes((Element) children.item(i), result);
            }
        }
    }

    private void validateBpmnXml(Map<String, Object> processDefinition, AiValidationResult result) {
        if (processDefinition == null) return;

        String bpmnXml = (String) processDefinition.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.isBlank()) return;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(bpmnXml)));

            // Check for BPMN 2.0 namespace
            String namespaceURI = doc.getDocumentElement().getNamespaceURI();
            if (namespaceURI == null || !namespaceURI.contains("omg.org/spec/BPMN")) {
                result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML missing BPMN 2.0 namespace declaration");
            }
        } catch (Exception e) {
            result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML is not valid: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void validateReferenceIntegrity(AiGeneratedData generatedData, AiValidationResult result) {
        List<Map<String, Object>> tables = generatedData.getTableDefinitions();
        if (tables == null) tables = List.of();

        // Build lookup: tableName -> table map, and tableName -> set of fieldNames
        Map<String, Map<String, Object>> tableMap = new HashMap<>();
        Map<String, Set<String>> tableFieldMap = new HashMap<>();
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");
            if (tableName != null) {
                tableMap.put(tableName, table);
                Set<String> fieldNames = new HashSet<>();
                List<Map<String, Object>> fields = (List<Map<String, Object>>) table.get("fieldDefinitions");
                if (fields == null) {
                    fields = (List<Map<String, Object>>) table.get("fields");
                }
                if (fields != null) {
                    for (Map<String, Object> field : fields) {
                        String fn = (String) field.get("fieldName");
                        if (fn != null) fieldNames.add(fn);
                    }
                }
                tableFieldMap.put(tableName, fieldNames);
            }
        }

        // Validate ForeignKey references
        for (int i = 0; i < tables.size(); i++) {
            Map<String, Object> table = tables.get(i);
            List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) table.get("foreignKeys");
            if (foreignKeys == null) continue;
            for (int j = 0; j < foreignKeys.size(); j++) {
                Map<String, Object> fk = foreignKeys.get(j);
                String refTableName = (String) fk.get("refTableName");
                String refFieldName = (String) fk.get("refFieldName");
                String fkPath = "tableDefinitions[" + i + "].foreignKeys[" + j + "]";

                if (refTableName != null && !tableMap.containsKey(refTableName)) {
                    result.addError("REFERENCE_INTEGRITY", fkPath + ".refTableName",
                        "Referenced table '" + refTableName + "' does not exist");
                }
                if (refTableName != null && refFieldName != null && tableFieldMap.containsKey(refTableName)) {
                    if (!tableFieldMap.get(refTableName).contains(refFieldName)) {
                        result.addError("REFERENCE_INTEGRITY", fkPath + ".refFieldName",
                            "Referenced field '" + refFieldName + "' does not exist in table '" + refTableName + "'");
                    }
                }
            }
        }

        // Validate FormTableBinding references
        List<Map<String, Object>> forms = generatedData.getFormDefinitions();
        if (forms != null) {
            for (int i = 0; i < forms.size(); i++) {
                List<Map<String, Object>> bindings = (List<Map<String, Object>>) forms.get(i).get("tableBindings");
                if (bindings != null) {
                    for (int j = 0; j < bindings.size(); j++) {
                        String tableName = (String) bindings.get(j).get("tableName");
                        if (tableName != null && !tableMap.containsKey(tableName)) {
                            result.addError("REFERENCE_INTEGRITY",
                                "formDefinitions[" + i + "].tableBindings[" + j + "].tableName",
                                "Referenced table '" + tableName + "' does not exist");
                        }
                    }
                } else {
                    // Fallback: check "bindingTableId" at form level
                    String bindingTableId = (String) forms.get(i).get("bindingTableId");
                    if (bindingTableId != null && !tableMap.containsKey(bindingTableId)) {
                        result.addError("REFERENCE_INTEGRITY",
                            "formDefinitions[" + i + "].bindingTableId",
                            "Referenced table '" + bindingTableId + "' does not exist");
                    }
                }
            }
        }

        // Validate TableRelation references
        List<Map<String, Object>> tableRelations = generatedData.getTableRelations();
        if (tableRelations != null) {
            for (int i = 0; i < tableRelations.size(); i++) {
                Map<String, Object> relation = tableRelations.get(i);
                String sourceTableName = (String) relation.get("sourceTableName");
                String targetTableName = (String) relation.get("targetTableName");
                String relPath = "tableRelations[" + i + "]";

                if (sourceTableName != null && !tableMap.containsKey(sourceTableName)) {
                    result.addError("REFERENCE_INTEGRITY", relPath + ".sourceTableName",
                            "Referenced table '" + sourceTableName + "' does not exist");
                }
                if (targetTableName != null && !tableMap.containsKey(targetTableName)) {
                    result.addError("REFERENCE_INTEGRITY", relPath + ".targetTableName",
                            "Referenced table '" + targetTableName + "' does not exist");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateUniqueness(AiGeneratedData generatedData, AiValidationResult result) {
        // tableName uniqueness
        checkUniqueness(generatedData.getTableDefinitions(), "tableName", "tableDefinitions", result);
        // formName uniqueness
        checkUniqueness(generatedData.getFormDefinitions(), "formName", "formDefinitions", result);
        // actionName uniqueness
        checkUniqueness(generatedData.getActionDefinitions(), "actionName", "actionDefinitions", result);
        // decisionKey uniqueness
        checkUniqueness(generatedData.getDecisionDefinitions(), "decisionKey", "decisionDefinitions", result);

        // fieldName uniqueness per table
        List<Map<String, Object>> tables = generatedData.getTableDefinitions();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) tables.get(i).get("fieldDefinitions");
                if (fields == null) {
                    fields = (List<Map<String, Object>>) tables.get(i).get("fields");
                }
                if (fields != null) {
                    Set<String> seen = new HashSet<>();
                    for (int j = 0; j < fields.size(); j++) {
                        String fieldName = (String) fields.get(j).get("fieldName");
                        if (fieldName != null && !seen.add(fieldName)) {
                            result.addError("UNIQUENESS", "tableDefinitions[" + i + "].fieldDefinitions[" + j + "].fieldName",
                                "Duplicate field name: " + fieldName);
                        }
                    }
                }
            }
        }
    }

    private void checkUniqueness(List<Map<String, Object>> items, String nameField, String arrayPath, AiValidationResult result) {
        if (items == null) return;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String name = (String) items.get(i).get(nameField);
            if (name != null && !seen.add(name)) {
                result.addError("UNIQUENESS", arrayPath + "[" + i + "]." + nameField, "Duplicate name: " + name);
            }
        }
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

    // ==================== Quality Score ====================

    @Override
    @SuppressWarnings("unchecked")
    public AiQualityScore computeQualityScore(AiGeneratedData data) {
        int completeness = computeCompleteness(data);
        int consistency = computeConsistency(data);
        int complexity = computeComplexity(data);
        int naming = computeNaming(data);

        List<String> suggestions = new ArrayList<>();
        if (completeness < 20) {
            suggestions.add("Consider adding more entity types for a complete function unit");
        }
        if (consistency < 20) {
            suggestions.add("Some references are invalid, check table bindings and foreign keys");
        }
        if (complexity < 20) {
            suggestions.add("Consider using more diverse field types (DECIMAL, DATE, BOOLEAN) for richer data modeling");
        }
        if (naming < 20) {
            suggestions.add("Use snake_case for table names and camelCase for field names");
        }

        Map<String, Integer> dimensions = new java.util.LinkedHashMap<>();
        dimensions.put("completeness", completeness);
        dimensions.put("consistency", consistency);
        dimensions.put("complexity", complexity);
        dimensions.put("naming", naming);

        return AiQualityScore.builder()
                .totalScore(completeness + consistency + complexity + naming)
                .dimensions(dimensions)
                .suggestions(suggestions)
                .build();
    }

    /**
     * 完整性评分：是否包含所有实体类型（tables, forms, actions, process, decisions, tableRelations）
     */
    private int computeCompleteness(AiGeneratedData data) {
        int score = 0;
        int entityTypes = 6;
        if (data.getTableDefinitions() != null && !data.getTableDefinitions().isEmpty()) score += 25 / entityTypes;
        if (data.getFormDefinitions() != null && !data.getFormDefinitions().isEmpty()) score += 25 / entityTypes;
        if (data.getActionDefinitions() != null && !data.getActionDefinitions().isEmpty()) score += 25 / entityTypes;
        if (data.getProcessDefinition() != null) score += 25 / entityTypes;
        if (data.getDecisionDefinitions() != null && !data.getDecisionDefinitions().isEmpty()) score += 25 / entityTypes;
        if (data.getTableRelations() != null && !data.getTableRelations().isEmpty()) score += 25 / entityTypes;
        return Math.min(score, 25);
    }

    /**
     * 一致性评分：引用完整性得分（表绑定、外键引用是否指向已存在的表）
     */
    @SuppressWarnings("unchecked")
    private int computeConsistency(AiGeneratedData data) {
        Set<String> tableNames = new HashSet<>();
        if (data.getTableDefinitions() != null) {
            for (Map<String, Object> table : data.getTableDefinitions()) {
                String name = (String) table.get("tableName");
                if (name != null) tableNames.add(name);
            }
        }
        if (tableNames.isEmpty()) return 25; // 无表定义时不扣分

        int totalRefs = 0;
        int validRefs = 0;

        // 检查 formDefinitions 的 tableBindings 引用
        if (data.getFormDefinitions() != null) {
            for (Map<String, Object> form : data.getFormDefinitions()) {
                List<Map<String, Object>> bindings = (List<Map<String, Object>>) form.get("tableBindings");
                if (bindings != null) {
                    for (Map<String, Object> binding : bindings) {
                        String tableName = (String) binding.get("tableName");
                        if (tableName != null) {
                            totalRefs++;
                            if (tableNames.contains(tableName)) validRefs++;
                        }
                    }
                }
            }
        }

        // 检查 tableRelations 引用
        if (data.getTableRelations() != null) {
            for (Map<String, Object> relation : data.getTableRelations()) {
                String source = (String) relation.get("sourceTableName");
                String target = (String) relation.get("targetTableName");
                if (source != null) {
                    totalRefs++;
                    if (tableNames.contains(source)) validRefs++;
                }
                if (target != null) {
                    totalRefs++;
                    if (tableNames.contains(target)) validRefs++;
                }
            }
        }

        // 检查 foreignKeys 引用
        if (data.getTableDefinitions() != null) {
            for (Map<String, Object> table : data.getTableDefinitions()) {
                List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) table.get("foreignKeys");
                if (foreignKeys != null) {
                    for (Map<String, Object> fk : foreignKeys) {
                        String refTableName = (String) fk.get("refTableName");
                        if (refTableName != null) {
                            totalRefs++;
                            if (tableNames.contains(refTableName)) validRefs++;
                        }
                    }
                }
            }
        }

        return totalRefs == 0 ? 25 : (int) (25.0 * validRefs / totalRefs);
    }

    /**
     * 复杂度评分：字段类型多样性和合理性
     */
    @SuppressWarnings("unchecked")
    private int computeComplexity(AiGeneratedData data) {
        if (data.getTableDefinitions() == null || data.getTableDefinitions().isEmpty()) return 25;

        Set<String> usedDataTypes = new HashSet<>();
        int totalFields = 0;

        for (Map<String, Object> table : data.getTableDefinitions()) {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) table.get("fieldDefinitions");
            if (fields == null) {
                fields = (List<Map<String, Object>>) table.get("fields");
            }
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    totalFields++;
                    String dataType = (String) field.get("dataType");
                    if (dataType != null) usedDataTypes.add(dataType);
                }
            }
        }

        if (totalFields == 0) return 25;

        // 类型多样性：使用的不同数据类型数量越多越好（最多 8 种得满分）
        int diversityScore = Math.min((int) (25.0 * usedDataTypes.size() / 8), 25);
        return diversityScore;
    }

    /**
     * 命名规范评分：表名 snake_case、字段名 snake_case 检查
     */
    @SuppressWarnings("unchecked")
    private int computeNaming(AiGeneratedData data) {
        if (data.getTableDefinitions() == null || data.getTableDefinitions().isEmpty()) return 25;

        int total = 0;
        int valid = 0;

        for (Map<String, Object> table : data.getTableDefinitions()) {
            String tableName = (String) table.get("tableName");
            if (tableName != null) {
                total++;
                if (tableName.matches("^[a-z][a-z0-9_]*$")) valid++;
            }

            List<Map<String, Object>> fields = (List<Map<String, Object>>) table.get("fieldDefinitions");
            if (fields == null) {
                fields = (List<Map<String, Object>>) table.get("fields");
            }
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    String fieldName = (String) field.get("fieldName");
                    if (fieldName != null) {
                        total++;
                        // 字段名允许 snake_case 或 camelCase
                        if (fieldName.matches("^[a-z][a-zA-Z0-9]*$") || fieldName.matches("^[a-z][a-z0-9_]*$")) {
                            valid++;
                        }
                    }
                }
            }
        }

        return total == 0 ? 25 : (int) (25.0 * valid / total);
    }
}

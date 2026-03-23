package com.developer.service.impl;

import com.developer.dto.AiGeneratedData;
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
            result.addError("NULL_DATA", "generatedData", "生成数据不能为空");
            return result;
        }

        // Task 4.1: Enum value and field constraint validation
        validateTableDefinitions(generatedData.getTableDefinitions(), result);
        validateFormDefinitions(generatedData.getFormDefinitions(), result);
        validateActionDefinitions(generatedData.getActionDefinitions(), result);
        validateIcon(generatedData.getIcon(), result);

        // Task 4.2: SVG security validation and BPMN XML validation
        validateSvg(generatedData.getIcon(), result);
        validateBpmnXml(generatedData.getProcessDefinition(), result);

        // Task 4.3: Reference integrity and uniqueness validation
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
                                    "DECIMAL 类型必须指定 precision 且大于 0");
                        }
                        if (field.get("scale") == null || toInt(field.get("scale")) < 0) {
                            result.addError("FIELD_CONSTRAINT", fieldPath + ".scale",
                                    "DECIMAL 类型必须指定 scale 且不小于 0");
                        }
                    }

                    if ("VARCHAR".equals(dataType)) {
                        if (field.get("length") == null || toInt(field.get("length")) <= 0) {
                            result.addError("FIELD_CONSTRAINT", fieldPath + ".length",
                                    "VARCHAR 类型必须指定 length 且大于 0");
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
                            "表定义必须包含至少一个主键字段");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateFormDefinitions(List<Map<String, Object>> formDefinitions, AiValidationResult result) {
        if (formDefinitions == null) return;
        for (int i = 0; i < formDefinitions.size(); i++) {
            Map<String, Object> form = formDefinitions.get(i);
            validateEnumValue(form.get("formType"), FormType.class,
                    "formDefinitions[" + i + "].formType", result);

            List<Map<String, Object>> bindings = (List<Map<String, Object>>) form.get("tableBindings");
            // Skip binding validation if LLM used legacy format (bindingTableId)
            if (bindings != null) {
                for (int j = 0; j < bindings.size(); j++) {
                    Map<String, Object> binding = bindings.get(j);
                    String bindingPath = "formDefinitions[" + i + "].tableBindings[" + j + "]";
                    validateEnumValue(binding.get("bindingType"), BindingType.class,
                            bindingPath + ".bindingType", result);
                    validateEnumValue(binding.get("bindingMode"), BindingMode.class,
                            bindingPath + ".bindingMode", result);
                }
            }
        }
    }

    private void validateActionDefinitions(List<Map<String, Object>> actionDefinitions, AiValidationResult result) {
        if (actionDefinitions == null) return;
        for (int i = 0; i < actionDefinitions.size(); i++) {
            Map<String, Object> action = actionDefinitions.get(i);
            validateEnumValue(action.get("actionType"), ActionType.class,
                    "actionDefinitions[" + i + "].actionType", result);
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
            result.addError("SVG_VALIDATION", "icon.name", "图标名称长度不能超过 100 字符");
        }

        String svgContent = (String) icon.get("svgContent");
        if (svgContent == null || svgContent.isBlank()) return;

        // Check size ≤ 10KB
        if (svgContent.getBytes(StandardCharsets.UTF_8).length > 10240) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 内容大小不能超过 10KB");
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
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 根元素必须为 <svg>");
            }

            // Check for dangerous tags
            String[] dangerousTags = {"script", "iframe", "object", "embed"};
            for (String tag : dangerousTags) {
                if (doc.getElementsByTagName(tag).getLength() > 0) {
                    result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 包含危险标签: <" + tag + ">");
                }
            }

            // Check for on* event attributes and javascript: protocol
            checkDangerousAttributes(doc.getDocumentElement(), result);

        } catch (Exception e) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 不是合法的 XML: " + e.getMessage());
        }
    }

    private void checkDangerousAttributes(Element element, AiValidationResult result) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrName = attr.getNodeName().toLowerCase();
            String attrValue = attr.getNodeValue().toLowerCase().trim();

            if (attrName.startsWith("on")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 包含事件属性: " + attrName);
            }
            if (attrValue.contains("javascript:")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 包含 javascript: 协议引用");
            }
            if (attrValue.contains("url(") && (attrValue.contains("http:") || attrValue.contains("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 包含外部资源引用");
            }
            // Check xlink:href with external URLs
            if (attrName.contains("href") && (attrValue.startsWith("http:") || attrValue.startsWith("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG 包含外部资源引用");
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
                result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML 缺少 BPMN 2.0 命名空间声明");
            }
        } catch (Exception e) {
            result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML 格式不合法: " + e.getMessage());
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
                        "引用的表 '" + refTableName + "' 不存在");
                }
                if (refTableName != null && refFieldName != null && tableFieldMap.containsKey(refTableName)) {
                    if (!tableFieldMap.get(refTableName).contains(refFieldName)) {
                        result.addError("REFERENCE_INTEGRITY", fkPath + ".refFieldName",
                            "引用的字段 '" + refFieldName + "' 在表 '" + refTableName + "' 中不存在");
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
                                "引用的表 '" + tableName + "' 不存在");
                        }
                    }
                } else {
                    // Fallback: check "bindingTableId" at form level
                    String bindingTableId = (String) forms.get(i).get("bindingTableId");
                    if (bindingTableId != null && !tableMap.containsKey(bindingTableId)) {
                        result.addError("REFERENCE_INTEGRITY",
                            "formDefinitions[" + i + "].bindingTableId",
                            "引用的表 '" + bindingTableId + "' 不存在");
                    }
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
                                "重复的字段名: " + fieldName);
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
                result.addError("UNIQUENESS", arrayPath + "[" + i + "]." + nameField, "重复的名称: " + name);
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
            result.addError("INVALID_ENUM", fieldPath, "非法枚举值: " + strValue);
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

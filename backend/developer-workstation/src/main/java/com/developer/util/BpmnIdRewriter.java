package com.developer.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN XML 中数据库 ID 引用的重写器。
 *
 * <p>用于克隆 / 导入功能单元时，把 BPMN 扩展属性中残留的源功能单元 ID
 * 替换为目标功能单元的对应 ID。涉及的扩展元素形如：
 * <pre>{@code
 *   <custom:property name="subTableId" value="13" />
 *   <custom:property name="formId" value="11" />
 *   <custom:property name="actionIds" value="[12,34,56]" />
 *   <custom_1:values name="actionIds" value="[12,34]" />
 * }</pre>
 *
 * <p>解析顺序（块内）：
 * <ol>
 *   <li><b>名字优先</b>：当同一 {@code <X:properties>} 块内同时存在 {@code subTableName} +
 *       {@code subTableId}（或 {@code tableName} + {@code tableId} / {@code formName} +
 *       {@code formId}）时，按名字在克隆侧查找对应实体的 ID 并写回；
 *       这样即使源数据中 ID 与名字不一致（用户在设计器换过表/表单导致 ID 没刷新），
 *       克隆出的 BPMN 仍能指向正确的克隆实体。</li>
 *   <li><b>ID 兜底</b>：没有名字属性，或名字在克隆侧找不到时，使用旧 ID → 新 ID 映射。</li>
 *   <li><b>actionIds 数组</b>：按 ID 逐个映射，找不到的保持原值（actionNames 长度与 actionIds 经常
 *       不一致，无法可靠按名字匹配）。</li>
 * </ol>
 *
 * <p>支持 Base64 编码与原文 XML：会在解码后重写并按原编码方式回写。
 */
public final class BpmnIdRewriter {

    /** 匹配 {@code <prefix:properties ...> ... </prefix:properties>} 块（含开闭标签）。 */
    private static final Pattern PROPERTIES_BLOCK = Pattern.compile(
            "<(\\w+):properties\\b[^>]*>.*?</\\1:properties>",
            Pattern.DOTALL);

    /** 匹配 {@code <prefix:properties ...>} 开标签。 */
    private static final Pattern OPENING_PROPERTIES_TAG = Pattern.compile(
            "<(\\w+):properties\\b[^>]*>",
            Pattern.DOTALL);

    /** 匹配 {@code <prefix:property ... />} 或 {@code <prefix:values ... />} 自闭合扩展元素。 */
    private static final Pattern EXTENSION_ELEMENT = Pattern.compile(
            "<(\\w+):(property|values)\\s+([^/>]*?)/>",
            Pattern.DOTALL);

    private static final Pattern NAME_ATTR = Pattern.compile(
            "\\bname\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern VALUE_ATTR = Pattern.compile(
            "(\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'])");

    private BpmnIdRewriter() {
    }

    /**
     * 旧 API：仅按 ID 映射重写。
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping) {
        return rewrite(bpmnXml, tableIdMapping, formIdMapping, actionIdMapping,
                Map.of(), Map.of());
    }

    /**
     * 重写 BPMN XML 中的 ID 引用，名字优先、ID 兜底。
     *
     * @param bpmnXml             原始 BPMN XML（可能为 Base64 编码）
     * @param tableIdMapping      旧 TableDefinition.id → 新 id（用于 subTableId / tableId 兜底）
     * @param formIdMapping       旧 FormDefinition.id → 新 id（用于 formId 兜底）
     * @param actionIdMapping     旧 ActionDefinition.id → 新 id（按数组逐个映射 actionIds）
     * @param clonedTableNameToId 克隆侧 表名 → 克隆 TableDefinition.id（块内有 subTableName/tableName 时优先用）
     * @param clonedFormNameToId  克隆侧 表单名 → 克隆 FormDefinition.id（块内有 formName 时优先用）
     * @return 重写后的 BPMN XML，编码方式与输入保持一致；输入为 null/空白时原样返回
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping,
                                 Map<String, Long> clonedTableNameToId,
                                 Map<String, Long> clonedFormNameToId) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }
        boolean hasAnyMapping = isNonEmpty(tableIdMapping)
                || isNonEmpty(formIdMapping)
                || isNonEmpty(actionIdMapping)
                || isNonEmpty(clonedTableNameToId)
                || isNonEmpty(clonedFormNameToId);
        if (!hasAnyMapping) {
            return bpmnXml;
        }

        String decoded = XmlEncodingUtil.smartDecode(bpmnXml);
        boolean wasEncoded = !decoded.equals(bpmnXml);

        String rewritten = rewriteAll(decoded,
                tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId);

        if (rewritten.equals(decoded)) {
            return bpmnXml;
        }
        return wasEncoded ? XmlEncodingUtil.encode(rewritten) : rewritten;
    }

    /**
     * 主重写流程：
     * <ul>
     *   <li>对 {@code <X:properties>} 块内容按"名字优先 + ID 兜底"重写；</li>
     *   <li>对块外的零散 {@code <X:property>} / {@code <X:values>} 自闭合元素按 ID 兜底重写。</li>
     * </ul>
     */
    private static String rewriteAll(String xml,
                                     Map<Long, Long> tableIdMapping,
                                     Map<Long, Long> formIdMapping,
                                     Map<Long, Long> actionIdMapping,
                                     Map<String, Long> clonedTableNameToId,
                                     Map<String, Long> clonedFormNameToId) {
        Matcher m = PROPERTIES_BLOCK.matcher(xml);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (m.find()) {
            // 块外区域：仅按 ID 兜底
            sb.append(rewriteUnwrapped(
                    xml.substring(pos, m.start()),
                    tableIdMapping, formIdMapping, actionIdMapping));
            // 块内：使用名字+ID 综合判断
            sb.append(rewriteBlock(
                    m.group(),
                    tableIdMapping, formIdMapping, actionIdMapping,
                    clonedTableNameToId, clonedFormNameToId));
            pos = m.end();
        }
        sb.append(rewriteUnwrapped(
                xml.substring(pos),
                tableIdMapping, formIdMapping, actionIdMapping));
        return sb.toString();
    }

    /**
     * 重写 {@code <X:properties>...</X:properties>} 块的内容，应用名字优先 + ID 兜底策略。
     */
    private static String rewriteBlock(String block,
                                       Map<Long, Long> tableIdMapping,
                                       Map<Long, Long> formIdMapping,
                                       Map<Long, Long> actionIdMapping,
                                       Map<String, Long> clonedTableNameToId,
                                       Map<String, Long> clonedFormNameToId) {
        Matcher openM = OPENING_PROPERTIES_TAG.matcher(block);
        if (!openM.find()) {
            return block;
        }
        int contentStart = openM.end();
        int contentEnd = block.lastIndexOf("</");
        if (contentEnd <= contentStart) {
            return block;
        }
        String openTag = block.substring(0, contentStart);
        String content = block.substring(contentStart, contentEnd);
        String closeTag = block.substring(contentEnd);

        Map<String, String> nameToValue = parsePropertyMap(content);

        Map<String, String> rewrites = new LinkedHashMap<>();

        String newSubTableId = resolveSingularId(
                nameToValue.get("subTableId"),
                nameToValue.get("subTableName"),
                tableIdMapping, clonedTableNameToId);
        if (newSubTableId != null && !newSubTableId.equals(nameToValue.get("subTableId"))) {
            rewrites.put("subTableId", newSubTableId);
        }

        String newTableId = resolveSingularId(
                nameToValue.get("tableId"),
                nameToValue.get("tableName"),
                tableIdMapping, clonedTableNameToId);
        if (newTableId != null && !newTableId.equals(nameToValue.get("tableId"))) {
            rewrites.put("tableId", newTableId);
        }

        String newFormId = resolveSingularId(
                nameToValue.get("formId"),
                nameToValue.get("formName"),
                formIdMapping, clonedFormNameToId);
        if (newFormId != null && !newFormId.equals(nameToValue.get("formId"))) {
            rewrites.put("formId", newFormId);
        }

        if (nameToValue.containsKey("actionIds") && isNonEmpty(actionIdMapping)) {
            String oldActionIds = nameToValue.get("actionIds");
            String newActionIds = remapArrayLongs(oldActionIds, actionIdMapping);
            if (!Objects.equals(oldActionIds, newActionIds)) {
                rewrites.put("actionIds", newActionIds);
            }
        }

        if (rewrites.isEmpty()) {
            return block;
        }

        String newContent = content;
        for (Map.Entry<String, String> e : rewrites.entrySet()) {
            newContent = replacePropertyValueByName(newContent, e.getKey(), e.getValue());
        }
        return openTag + newContent + closeTag;
    }

    /**
     * 重写块外（unwrapped）的零散扩展元素，仅做 ID 映射兜底。
     */
    private static String rewriteUnwrapped(String segment,
                                           Map<Long, Long> tableIdMapping,
                                           Map<Long, Long> formIdMapping,
                                           Map<Long, Long> actionIdMapping) {
        Map<String, Function<String, String>> remappers = new HashMap<>();
        if (isNonEmpty(tableIdMapping)) {
            Function<String, String> tableRemap = v -> remapSingularLong(v, tableIdMapping);
            remappers.put("subTableId", tableRemap);
            remappers.put("tableId", tableRemap);
        }
        if (isNonEmpty(formIdMapping)) {
            remappers.put("formId", v -> remapSingularLong(v, formIdMapping));
        }
        if (isNonEmpty(actionIdMapping)) {
            remappers.put("actionIds", v -> remapArrayLongs(v, actionIdMapping));
        }
        if (remappers.isEmpty()) {
            return segment;
        }

        Matcher m = EXTENSION_ELEMENT.matcher(segment);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String element = m.group();
            String attrs = m.group(3);

            Matcher nameM = NAME_ATTR.matcher(attrs);
            if (!nameM.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            Function<String, String> remapper = remappers.get(nameM.group(1));
            if (remapper == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            Matcher valueM = VALUE_ATTR.matcher(attrs);
            if (!valueM.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            String oldValue = valueM.group(2);
            String newValue = remapper.apply(oldValue);
            if (Objects.equals(oldValue, newValue)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            String oldValueAttr = valueM.group();
            String newValueAttr = valueM.group(1) + newValue + valueM.group(3);
            String newElement = element.replace(oldValueAttr, newValueAttr);
            m.appendReplacement(sb, Matcher.quoteReplacement(newElement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析 properties 块内容，提取所有 {@code <X:property>} / {@code <X:values>} 的 name → value。
     */
    private static Map<String, String> parsePropertyMap(String content) {
        Map<String, String> result = new HashMap<>();
        Matcher m = EXTENSION_ELEMENT.matcher(content);
        while (m.find()) {
            String attrs = m.group(3);
            Matcher nameM = NAME_ATTR.matcher(attrs);
            Matcher valueM = VALUE_ATTR.matcher(attrs);
            if (nameM.find() && valueM.find()) {
                result.putIfAbsent(nameM.group(1), valueM.group(2));
            }
        }
        return result;
    }

    /**
     * 名字优先解析单一 ID。
     * <ol>
     *   <li>oldName 非空且在 nameToId 中命中 → 返回名字对应的新 ID（推荐路径）。</li>
     *   <li>否则若 oldIdStr 在 idMapping 中命中 → 返回映射后的新 ID。</li>
     *   <li>都失败 → 返回 null（调用方保留原值）。</li>
     * </ol>
     */
    private static String resolveSingularId(String oldIdStr,
                                            String oldName,
                                            Map<Long, Long> idMapping,
                                            Map<String, Long> nameToId) {
        if (oldIdStr == null) {
            return null;
        }
        if (oldName != null && !oldName.isBlank() && isNonEmpty(nameToId)) {
            Long byName = nameToId.get(oldName.trim());
            if (byName != null) {
                return String.valueOf(byName);
            }
        }
        if (isNonEmpty(idMapping)) {
            try {
                Long oldId = Long.parseLong(oldIdStr.trim());
                Long mapped = idMapping.get(oldId);
                if (mapped != null) {
                    return String.valueOf(mapped);
                }
            } catch (NumberFormatException ignored) {
                // keep original
            }
        }
        return null;
    }

    /**
     * 在 properties 块内容里替换指定 name 对应的 {@code <X:property|values>} 元素的 value。
     * 兼容两种属性顺序与单/双引号。
     */
    private static String replacePropertyValueByName(String content, String propertyName, String newValue) {
        Pattern p = Pattern.compile(
                "(<\\w+:(?:property|values)\\s+[^/>]*?\\bname\\s*=\\s*[\"']"
                        + Pattern.quote(propertyName)
                        + "[\"'][^/>]*?\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'][^/>]*?/>)"
                        + "|"
                        + "(<\\w+:(?:property|values)\\s+[^/>]*?\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'][^/>]*?\\bname\\s*=\\s*[\"']"
                        + Pattern.quote(propertyName)
                        + "[\"'][^/>]*?/>)",
                Pattern.DOTALL);
        Matcher m = p.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (m.group(1) != null) {
                replacement = m.group(1) + newValue + m.group(3);
            } else {
                replacement = m.group(4) + newValue + m.group(6);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String remapSingularLong(String value, Map<Long, Long> mapping) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            Long old = Long.parseLong(value.trim());
            Long mapped = mapping.get(old);
            return mapped != null ? String.valueOf(mapped) : value;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String remapArrayLongs(String value, Map<Long, Long> mapping) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        boolean wrapped = trimmed.startsWith("[") && trimmed.endsWith("]");
        String inner = wrapped ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        if (inner.isBlank()) {
            return value;
        }
        String[] parts = inner.split(",");
        StringBuilder sb = new StringBuilder();
        if (wrapped) {
            sb.append('[');
        }
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String part = parts[i].trim();
            try {
                Long old = Long.parseLong(part);
                Long mapped = mapping.get(old);
                if (mapped != null) {
                    sb.append(mapped);
                    changed = true;
                } else {
                    sb.append(part);
                }
            } catch (NumberFormatException e) {
                sb.append(part);
            }
        }
        if (wrapped) {
            sb.append(']');
        }
        return changed ? sb.toString() : value;
    }

    private static boolean isNonEmpty(Map<?, ?> m) {
        return m != null && !m.isEmpty();
    }
}

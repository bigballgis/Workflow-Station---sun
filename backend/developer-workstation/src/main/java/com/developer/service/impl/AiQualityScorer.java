package com.developer.service.impl;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiQualityScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 生成数据的质量评分协作类
 * <p>
 * 四维度评分：完整性、一致性、复杂度、命名规范。每维 0-25 分，总分 0-100。
 * 由 {@link AiValidationServiceImpl} 门面委托调用，评分逻辑与原实现逐字保持一致。
 */
@Component
public class AiQualityScorer {

    AiQualityScore computeQualityScore(AiGeneratedData data) {
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

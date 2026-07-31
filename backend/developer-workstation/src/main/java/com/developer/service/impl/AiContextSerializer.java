package com.developer.service.impl;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.ActionDefinition;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.entity.TableRelation;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 功能单元上下文序列化协作类。
 * 负责将 {@link FunctionUnit} 及其关联实体转换为发送给 AI Agent (Activepieces) 的 {@link FunctionUnitContextDTO}。
 * 纯结构转换，无副作用、无外部依赖。
 */
@Component
class AiContextSerializer {

    FunctionUnitContextDTO buildContextDTO(FunctionUnit fu) {
        // Explicitly trigger lazy loading (ensure all associations are loaded within @Transactional)
        List<TableDefinition> tables = fu.getTableDefinitions();
        if (tables != null) tables.size();
        List<FormDefinition> forms = fu.getFormDefinitions();
        if (forms != null) forms.size();
        List<ActionDefinition> actions = fu.getActionDefinitions();
        if (actions != null) actions.size();
        List<DecisionDefinition> decisions = fu.getDecisionDefinitions();
        if (decisions != null) decisions.size();
        List<TableRelation> relations = fu.getTableRelations();
        if (relations != null) relations.size();
        ProcessDefinition pd = fu.getProcessDefinition();
        Icon icon = fu.getIcon();

        return FunctionUnitContextDTO.builder()
                .functionUnitId(fu.getId())
                .name(fu.getName())
                .description(fu.getDisplayName())
                .tableDefinitions(serializeTableDefinitions(tables))
                .formDefinitions(serializeFormDefinitions(forms))
                .actionDefinitions(serializeActionDefinitions(actions))
                .decisionDefinitions(serializeDecisionDefinitions(decisions))
                .tableRelations(serializeTableRelations(relations, tables))
                .processDefinition(serializeProcessDefinition(pd))
                .icon(serializeIcon(icon))
                .build();
    }

    private List<Map<String, Object>> serializeTableDefinitions(List<TableDefinition> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        return tables.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tableName", t.getTableName());
            map.put("tableType", t.getTableType() != null ? t.getTableType().name() : null);
            map.put("tableDisplayName", t.getTableDisplayName());
            map.put("description", t.getDisplayName());
            map.put("fieldDefinitions", serializeFieldDefinitions(t.getFieldDefinitions()));
            map.put("foreignKeys", serializeForeignKeys(t.getForeignKeys()));
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeFieldDefinitions(List<FieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldName", f.getFieldName());
            map.put("dataType", f.getDataType() != null ? f.getDataType().name() : null);
            map.put("length", f.getLength());
            map.put("precision", f.getPrecision());
            map.put("scale", f.getScale());
            map.put("nullable", f.getNullable());
            map.put("defaultValue", f.getDefaultValue());
            map.put("isPrimaryKey", f.getIsPrimaryKey());
            map.put("isUnique", f.getIsUnique());
            map.put("description", f.getDisplayName());
            map.put("sortOrder", f.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeForeignKeys(List<ForeignKey> foreignKeys) {
        if (foreignKeys == null || foreignKeys.isEmpty()) {
            return List.of();
        }
        return foreignKeys.stream().map(fk -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldName", fk.getFieldDefinition() != null ? fk.getFieldDefinition().getFieldName() : null);
            map.put("refTableName", fk.getRefTableDefinition() != null ? fk.getRefTableDefinition().getTableName() : null);
            map.put("refFieldName", fk.getRefFieldDefinition() != null ? fk.getRefFieldDefinition().getFieldName() : null);
            map.put("onDelete", fk.getOnDelete());
            map.put("onUpdate", fk.getOnUpdate());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeFormDefinitions(List<FormDefinition> forms) {
        if (forms == null || forms.isEmpty()) {
            return List.of();
        }
        return forms.stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("formName", f.getFormName());
            map.put("formType", f.getFormType() != null ? f.getFormType().name() : null);
            map.put("configJson", f.getConfigJson());
            map.put("description", f.getDisplayName());
            map.put("tableBindings", serializeTableBindings(f.getTableBindings()));
            map.put("fieldPermissions", f.getFieldPermissions() != null ? f.getFieldPermissions() : Map.of());
            map.put("showLiveValues", f.getShowLiveValues());
            map.put("stageBindings", serializeStageBindings(f.getStageBindings()));
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeStageBindings(List<FormStageBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("stageId", b.getStageId());
            map.put("stageName", b.getStageName());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeTableBindings(List<FormTableBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tableName", b.getTableName());
            map.put("bindingType", b.getBindingType() != null ? b.getBindingType().name() : null);
            map.put("bindingMode", b.getBindingMode() != null ? b.getBindingMode().name() : null);
            map.put("foreignKeyField", b.getForeignKeyField());
            map.put("sortOrder", b.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeActionDefinitions(List<ActionDefinition> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("actionName", a.getActionName());
            map.put("actionType", a.getActionType() != null ? a.getActionType().name() : null);
            map.put("configJson", a.getConfigJson());
            map.put("icon", a.getIcon());
            map.put("buttonColor", a.getButtonColor());
            map.put("description", a.getDisplayName());
            map.put("isDefault", a.getIsDefault());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> serializeDecisionDefinitions(List<DecisionDefinition> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }
        return decisions.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("decisionKey", d.getDecisionKey());
            map.put("decisionName", d.getDecisionName());
            map.put("dmnXml", d.getDmnXml());
            map.put("hitPolicy", d.getHitPolicy());
            map.put("description", d.getDescription());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Serialize table relations, resolving sourceTableId/targetTableId to corresponding tableName
     * (AI does not know internal IDs).
     */
    private List<Map<String, Object>> serializeTableRelations(
            List<TableRelation> relations, List<TableDefinition> tables) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }

        // Build ID → tableName lookup table
        Map<Long, String> idToName = new HashMap<>();
        if (tables != null) {
            for (TableDefinition t : tables) {
                if (t.getId() != null) {
                    idToName.put(t.getId(), t.getTableName());
                }
            }
        }

        return relations.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceTableName", idToName.getOrDefault(r.getSourceTableId(), "unknown_" + r.getSourceTableId()));
            map.put("sourceFieldName", r.getSourceFieldName());
            map.put("relationType", r.getRelationType());
            map.put("targetTableName", idToName.getOrDefault(r.getTargetTableId(), "unknown_" + r.getTargetTableId()));
            map.put("targetFieldName", r.getTargetFieldName());
            return map;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> serializeProcessDefinition(ProcessDefinition pd) {
        if (pd == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bpmnXml", pd.getBpmnXml());
        return map;
    }

    private Map<String, Object> serializeIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", icon.getName());
        map.put("category", icon.getCategory() != null ? icon.getCategory().name() : null);
        map.put("svgContent", icon.getSvgContent());
        map.put("description", icon.getDescription());
        return map;
    }
}

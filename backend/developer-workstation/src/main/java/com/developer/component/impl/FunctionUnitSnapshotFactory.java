package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能单元版本快照协作类。
 * 负责计算下一个版本号，以及把功能单元结构序列化为发布版本的 JSON 快照。
 */
@Component
@RequiredArgsConstructor
@Slf4j
class FunctionUnitSnapshotFactory {

    private final ObjectMapper objectMapper;

    String calculateNextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return "1.0.0";
        }
        try {
            String[] parts = currentVersion.split("\\.");
            if (parts.length != 3) {
                log.warn("Malformed version string '{}', falling back to 1.0.0", currentVersion);
                return "1.0.0";
            }
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]) + 1;
            return major + "." + minor + "." + patch;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse version '{}': {}, falling back to 1.0.0", currentVersion, e.getMessage());
            return "1.0.0";
        }
    }

    byte[] createSnapshot(FunctionUnit functionUnit) throws Exception {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", functionUnit.getName());
        snapshot.put("code", functionUnit.getCode());
        snapshot.put("description", functionUnit.getDisplayName());
        snapshot.put("status", functionUnit.getStatus() != null ? functionUnit.getStatus().name() : null);
        snapshot.put("processXml", functionUnit.getProcessDefinition() != null ?
                functionUnit.getProcessDefinition().getBpmnXml() : null);

        // Snapshot table definitions with fields
        List<Map<String, Object>> tableSnapshots = new ArrayList<>();
        for (TableDefinition table : functionUnit.getTableDefinitions()) {
            Map<String, Object> tableSnap = new HashMap<>();
            tableSnap.put("tableName", table.getTableName());
            tableSnap.put("tableType", table.getTableType() != null ? table.getTableType().name() : null);
            tableSnap.put("tableDisplayName", table.getTableDisplayName());
            tableSnap.put("description", table.getDisplayName());

            List<Map<String, Object>> fieldSnapshots = new ArrayList<>();
            for (FieldDefinition field : table.getFieldDefinitions()) {
                Map<String, Object> fieldSnap = new HashMap<>();
                fieldSnap.put("fieldName", field.getFieldName());
                fieldSnap.put("dataType", field.getDataType() != null ? field.getDataType().name() : null);
                fieldSnap.put("length", field.getLength());
                fieldSnap.put("precision", field.getPrecision());
                fieldSnap.put("scale", field.getScale());
                fieldSnap.put("nullable", field.getNullable());
                fieldSnap.put("defaultValue", field.getDefaultValue());
                fieldSnap.put("isPrimaryKey", field.getIsPrimaryKey());
                fieldSnap.put("isUnique", field.getIsUnique());
                fieldSnap.put("displayName", field.getDisplayName());
                fieldSnap.put("sortOrder", field.getSortOrder());
                fieldSnapshots.add(fieldSnap);
            }
            tableSnap.put("fieldDefinitions", fieldSnapshots);
            tableSnapshots.add(tableSnap);
        }
        snapshot.put("tableDefinitions", tableSnapshots);

        // Snapshot form definitions
        List<Map<String, Object>> formSnapshots = new ArrayList<>();
        for (FormDefinition form : functionUnit.getFormDefinitions()) {
            Map<String, Object> formSnap = new HashMap<>();
            formSnap.put("formName", form.getFormName());
            formSnap.put("formType", form.getFormType() != null ? form.getFormType().name() : null);
            formSnap.put("configJson", form.getConfigJson());
            formSnap.put("description", form.getDisplayName());
            formSnap.put("boundTableName", form.getBoundTableName());
            formSnapshots.add(formSnap);
        }
        snapshot.put("formDefinitions", formSnapshots);

        // Snapshot action definitions
        List<Map<String, Object>> actionSnapshots = new ArrayList<>();
        for (ActionDefinition action : functionUnit.getActionDefinitions()) {
            Map<String, Object> actionSnap = new HashMap<>();
            actionSnap.put("actionName", action.getActionName());
            actionSnap.put("actionType", action.getActionType() != null ? action.getActionType().name() : null);
            actionSnap.put("configJson", action.getConfigJson());
            actionSnap.put("icon", action.getIcon());
            actionSnap.put("buttonColor", action.getButtonColor());
            actionSnap.put("description", action.getDisplayName());
            actionSnap.put("isDefault", action.getIsDefault());
            actionSnapshots.add(actionSnap);
        }
        snapshot.put("actionDefinitions", actionSnapshots);

        // Snapshot decision definitions
        List<Map<String, Object>> decisionSnapshots = new ArrayList<>();
        for (DecisionDefinition decision : functionUnit.getDecisionDefinitions()) {
            Map<String, Object> decisionSnap = new HashMap<>();
            decisionSnap.put("decisionKey", decision.getDecisionKey());
            decisionSnap.put("decisionName", decision.getDecisionName());
            decisionSnap.put("dmnXml", decision.getDmnXml());
            decisionSnap.put("hitPolicy", decision.getHitPolicy());
            decisionSnap.put("description", decision.getDescription());
            decisionSnapshots.add(decisionSnap);
        }
        snapshot.put("decisionDefinitions", decisionSnapshots);

        return objectMapper.writeValueAsBytes(snapshot);
    }
}
